package com.stonytark.magnetization.content.effect;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.worldgen.PetrifiedForestBiome;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-level registry of transient magnetic fields seeded by lightning. Two
 * sources right now:
 *
 * <ul>
 *   <li><b>Freshly petrified wood</b> — every log a lightning bolt converts to
 *       {@code petrified_wood} (see {@link LightningRemnantMagnetism#petrifyLogsAround})
 *       leaves a weak omnidirectional field at its position, decaying to zero
 *       over {@link #DURATION_TICKS}. The block itself stays petrified after the
 *       field fades; that's the new "inert" state.</li>
 *   <li><b>Ground strikes</b> — a lightning bolt landing on a solid cell has a
 *       biome-biased chance of leaving a stronger temporary field at the strike
 *       point. Higher chance in the {@link PetrifiedForestBiome} so the biome's
 *       characteristic frequent storms accumulate a noticeable magnetic field
 *       as a player walks through.</li>
 * </ul>
 *
 * <p>Each registered field is run through {@link FieldApplicator#apply} every
 * {@link #APPLY_INTERVAL_TICKS} so existing emitter logic (entity pass,
 * sub-level pass) handles the actual force application — no parallel
 * implementation needed. Linear strength decay over the duration mirrors how
 * {@link com.stonytark.magnetization.api.Lirm} treats item stamps.
 *
 * <p>Storage is in-memory only — transient by design. A server restart
 * forgets all active fields, which is fine: anything that mattered already
 * happened (gear got nudged, items got tossed) before the restart.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class TemporaryLirmFields {

    /** How long each registered field stays active. 30 seconds is long
     *  enough to be visible while keeping the in-memory list bounded. */
    private static final int DURATION_TICKS = 600;


    /** Base chance a ground strike leaves a temporary field, outside the
     *  petrified forest. Already-rare vanilla lightning means the global
     *  rate stays low. */
    private static final double GROUND_FIELD_CHANCE = 0.30d;

    /** Chance inside the petrified forest. Combined with the biome's
     *  custom-storm rate, the player sees several active fields at any time
     *  while inside the biome — its defining environmental quirk. */
    private static final double PETRIFIED_GROUND_FIELD_CHANCE = 0.75d;

    /** Field properties for petrified-wood emitters. Weak + short range
     *  so a tree-load of stamps doesn't dominate the world. */
    private static final MagneticStrength PETRIFIED_LOG_TIER = MagneticStrength.WEAK;
    private static final double PETRIFIED_LOG_RANGE_BASE = 4.0d;

    /** Ground-strike field is stronger and reaches further — the bolt's
     *  energy was deposited directly into the ground, no log filter. */
    private static final MagneticStrength GROUND_FIELD_TIER = MagneticStrength.MEDIUM;
    private static final double GROUND_FIELD_RANGE_BASE = 6.0d;
    private static final double PETRIFIED_GROUND_FIELD_RANGE_BASE = 9.0d;

    /** Live entries by level. Key by dimension resource key so this stays
     *  correct across multi-world servers. Outer map is concurrent; inner
     *  lists are synchronized so the tick-handler's iterate-with-remove is
     *  safe even if a register call lands from off-main during lightning
     *  event dispatch. */
    private static final Map<ResourceKey<Level>, List<Entry>> ENTRIES_BY_LEVEL = new ConcurrentHashMap<>();

    /** One transient remnant field. {@code baseRange} captures the initial
     *  range; we shrink it linearly with age to sell the "fading" feel. */
    private record Entry(
            Vec3 origin,
            MagneticPolarity polarity,
            MagneticStrength tier,
            double baseRange,
            long bornTick
    ) {}

    private TemporaryLirmFields() {}

    /** Generic registration — every other caller funnels through here. Use this
     *  from new event handlers (explosions, mob deaths, etc.) so the registry
     *  stays the single source of transient-field truth. */
    public static void register(final ServerLevel level, final Vec3 origin,
                                final MagneticPolarity polarity,
                                final MagneticStrength tier,
                                final double range, final long now) {
        addEntry(level, new Entry(origin, polarity, tier, range, now));
    }

    /** Register a temporary field at a just-petrified log. Called from
     *  {@link LightningRemnantMagnetism} for every cell converted in a strike. */
    public static void registerPetrifiedLog(final ServerLevel level, final BlockPos pos, final long now) {
        final MagneticPolarity pol = randomPolarity(level);
        addEntry(level, new Entry(Vec3.atCenterOf(pos), pol,
                PETRIFIED_LOG_TIER, PETRIFIED_LOG_RANGE_BASE, now));
    }

    /** Convenience for callers that want a random polarity but custom range/tier. */
    public static void registerRandomPolarity(final ServerLevel level, final Vec3 origin,
                                              final MagneticStrength tier,
                                              final double range, final long now) {
        register(level, origin, randomPolarity(level), tier, range, now);
    }

    /** Roll for a temporary ground field at a strike position. Higher chance
     *  inside the Petrified Forest. Caller pre-filters out strikes that
     *  landed in air / on entities. */
    public static void maybeRegisterGroundStrike(final ServerLevel level, final BlockPos pos, final long now) {
        final boolean inForest = PetrifiedForestBiome.isAt(level, pos);
        final double chance = inForest ? PETRIFIED_GROUND_FIELD_CHANCE : GROUND_FIELD_CHANCE;
        if (level.random.nextDouble() >= chance) return;
        final double range = inForest ? PETRIFIED_GROUND_FIELD_RANGE_BASE : GROUND_FIELD_RANGE_BASE;
        final MagneticPolarity pol = randomPolarity(level);
        addEntry(level, new Entry(Vec3.atCenterOf(pos), pol, GROUND_FIELD_TIER, range, now));
    }

    private static void addEntry(final ServerLevel level, final Entry entry) {
        ENTRIES_BY_LEVEL
                .computeIfAbsent(level.dimension(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(entry);
    }

    private static MagneticPolarity randomPolarity(final ServerLevel level) {
        return level.random.nextBoolean() ? MagneticPolarity.NORTH : MagneticPolarity.SOUTH;
    }

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        if ((server.getGameTime() % com.stonytark.magnetization.config.MagConfig.temporaryLirmApplyTicks()) != 0L) return;
        final List<Entry> entries = ENTRIES_BY_LEVEL.get(server.dimension());
        if (entries == null || entries.isEmpty()) return;

        final long now = server.getGameTime();
        // Hold the list lock for the whole iterate-with-remove pass — entries
        // is a synchronizedList, so its iterator() requires external sync per
        // the Collections.synchronizedList contract.
        synchronized (entries) {
            final Iterator<Entry> it = entries.iterator();
            while (it.hasNext()) {
                final Entry e = it.next();
                final long age = now - e.bornTick;
                if (age >= DURATION_TICKS) {
                    it.remove();
                    continue;
                }
                // Linear decay: full range at birth, zero at expiration. Skip
                // pump-throughs where the effective range collapsed below 1.
                final double remaining = 1.0d - (age / (double) DURATION_TICKS);
                final double range = e.baseRange * remaining;
                if (range < 1.0d) continue;
                final MagneticField field = new MagneticField(
                        e.origin,
                        new Vec3(0, 1, 0),
                        e.polarity,
                        e.tier,
                        MagneticField.Shape.OMNIDIRECTIONAL,
                        range);
                FieldApplicator.apply(server, field);
            }
        }
    }

    /** Test/debug helper — returns the count of active entries in the level,
     *  without exposing the internal list. */
    public static int activeCount(final ServerLevel level) {
        final List<Entry> entries = ENTRIES_BY_LEVEL.get(level.dimension());
        return entries == null ? 0 : entries.size();
    }
}
