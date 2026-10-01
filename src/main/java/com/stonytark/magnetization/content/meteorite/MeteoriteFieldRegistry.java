package com.stonytark.magnetization.content.meteorite;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.network.CosmicCompassTargetPayload;
import com.stonytark.magnetization.physics.FieldApplicator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Per-level registry of meteorite field sources that don't have a backing
 * {@link MeteoriteCoreBlockEntity}. Current feeder: the AE2 meteor scanner
 * ({@link AeMeteoriteScanner}) — when it finds an AE2 meteor structure on a
 * freshly-loaded chunk, it adds an entry here so the AE2 meteorite emits the
 * same decaying magnetic field a native meteorite_core would, without
 * dropping an extra block on top of AE2's structure.
 *
 * <p>Each entry persists in a per-dimension {@link State} SavedData so a
 * reloaded meteorite resumes its decay from the persisted charged-at tick
 * (rather than reset to full charge on every world load).
 *
 * <p>Why a registry instead of placing a meteorite_core block at the AE2
 * meteor's centre: AE2's structure already occupies its centre cell with
 * sky_stone; overwriting that would either eat AE2 loot or sit weirdly.
 * The registry lets us emit a field at the same coordinates without
 * disturbing any blocks.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MeteoriteFieldRegistry {

    private MeteoriteFieldRegistry() {}

    /** One AE2-sourced meteorite. NORTH polarity, decaying strength tier. */
    public record Entry(BlockPos pos, long chargedAtTick) {
        CompoundTag save() {
            final CompoundTag t = new CompoundTag();
            t.putLong("Pos", pos.asLong());
            t.putLong("ChargedAt", chargedAtTick);
            return t;
        }
        static Entry load(final CompoundTag t) {
            return new Entry(BlockPos.of(t.getLong("Pos")), t.getLong("ChargedAt"));
        }
    }

    /** Register an AE2 meteorite at {@code pos} with {@code now} as the
     *  full-charge moment. Idempotent — repeated calls at the same pos are
     *  ignored so chunk re-loads don't reset the decay timer. */
    public static void register(final ServerLevel level, final BlockPos pos, final long now) {
        final State state = State.get(level);
        synchronized (state.entries) {
            for (final Entry e : state.entries) if (e.pos.equals(pos)) return;
            state.entries.add(new Entry(pos, now));
        }
        state.setDirty();
    }

    public static int activeCount(final ServerLevel level) {
        return State.get(level).entries.size();
    }

    public static Collection<Entry> snapshot(final ServerLevel level) {
        final State state = State.get(level);
        synchronized (state.entries) {
            return new ArrayList<>(state.entries);
        }
    }

    /** Select virtual sources without requiring their chunks on the client. */
    static @Nullable Entry nearestActive(final Iterable<Entry> entries, final Vec3 from,
                                         final double range, final long now, final long decay) {
        Entry best = null;
        double bestDistance = range * range;
        for (final Entry entry : entries) {
            if (MeteoriteCoreBlockEntity.tierForElapsed(now - entry.chargedAtTick(), decay) == null) continue;
            final double distance = entry.pos().getCenter().distanceToSqr(from);
            if (distance >= bestDistance) continue;
            best = entry;
            bestDistance = distance;
        }
        return best;
    }

    /** Authoritative reading for this dimension, including the integration switch. */
    public static CosmicCompassTargetPayload compassTarget(final ServerLevel level, final Vec3 from) {
        return compassTarget(level, from, AeMeteoriteScanner.isEnabled() ? snapshot(level) : List.of());
    }

    private static CosmicCompassTargetPayload compassTarget(final ServerLevel level, final Vec3 from,
                                                             final Collection<Entry> entries) {
        final long now = level.getGameTime();
        final long decay = MeteoriteCoreBlockEntity.decayTicks();
        final Entry target = nearestActive(entries, from, cosmicCompassRange(), now, decay);
        final long expiresAt = target == null ? now : Math.min(now + 60L, target.chargedAtTick() + decay);
        return new CosmicCompassTargetPayload(level.dimension().location(),
                java.util.Optional.ofNullable(target == null ? null : target.pos()), expiresAt);
    }

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        if (!net.neoforged.fml.ModList.get().isLoaded("ae2")) return;
        final boolean enabled = AeMeteoriteScanner.isEnabled();
        final long now = server.getGameTime();
        // Refresh one small target per player once a second. Empty updates also
        // clear a previous target after the hook is disabled or the source decays.
        if (now % 20L == 0L && !server.players().isEmpty()) {
            final Collection<Entry> entries = enabled ? snapshot(server) : List.of();
            for (final var player : server.players()) {
                PacketDistributor.sendToPlayer(player, compassTarget(server, player.position(), entries));
            }
        }
        if (!enabled) return;
        if ((server.getGameTime() % com.stonytark.magnetization.config.MagConfig.meteoriteFieldTicks()) != 0L) return;
        final State state = State.get(server);
        if (state.entries.isEmpty()) return;

        final long decay = MeteoriteCoreBlockEntity.decayTicks();
        synchronized (state.entries) {
            for (final Entry e : state.entries) {
                final long elapsed = now - e.chargedAtTick;
                final MagneticStrength tier = MeteoriteCoreBlockEntity.tierForElapsed(elapsed, decay);
                if (tier == null) continue;
                final MagneticField field = new MagneticField(
                        Vec3.atCenterOf(e.pos),
                        new Vec3(0, 1, 0),
                        MagneticPolarity.NORTH,
                        tier,
                        MagneticField.Shape.OMNIDIRECTIONAL);
                FieldApplicator.apply(server, field);
            }
        }
    }

    private static double cosmicCompassRange() {
        try { return MagConfig.COSMIC_COMPASS_RANGE.get(); }
        catch (final Throwable t) { return 512.0; }
    }

    /** Per-level SavedData. Lives in {@code <level>/data/magnetization_meteorite_fields.dat}. */
    public static final class State extends SavedData {
        private static final String KEY = "magnetization_meteorite_fields";
        // Synchronized so the registry's iterate-with-add patterns are safe
        // against multi-source register calls (ChunkEvent.Load fires on the
        // server's main thread but other event handlers may not).
        final List<Entry> entries = Collections.synchronizedList(new ArrayList<>());

        public static State get(final ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(
                    new Factory<>(State::new, State::load),
                    KEY);
        }

        private State() {}

        private static State load(final CompoundTag tag, final HolderLookup.Provider lookup) {
            final State s = new State();
            final ListTag list = tag.getList("Entries", 10);
            for (int i = 0; i < list.size(); i++) {
                s.entries.add(Entry.load(list.getCompound(i)));
            }
            return s;
        }

        @Override
        public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider lookup) {
            final ListTag list = new ListTag();
            synchronized (entries) { for (final Entry e : entries) list.add(e.save()); }
            tag.put("Entries", list);
            return tag;
        }
    }

    /** Force the per-level SavedData to bind for {@code level}. Optional
     *  warmup — typically the first tick or register() call does this on
     *  its own. */
    public static void ensureLoaded(final ServerLevel level) {
        State.get(level);
    }
}
