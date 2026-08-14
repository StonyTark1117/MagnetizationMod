package com.stonytark.magnetization.content.pyrrhotite;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Heat-activated emitter BE. Scans the 6 axis-aligned neighbours each
 * computeField pass for any block carrying {@link BlazeBurnerBlock#HEAT_LEVEL}
 * (the Create heat-source property); maps the maximum observed level to a
 * field-strength tier.
 *
 * <p>Heat → strength mapping:
 * <ul>
 *   <li>{@code NONE} / no adjacent heat → null (no field emitted)</li>
 *   <li>{@code SMOULDERING} or {@code FADING} → {@code WEAK}</li>
 *   <li>{@code KINDLED} → {@code STRONG}</li>
 *   <li>{@code SEETHING} → {@code EXTREME}</li>
 * </ul>
 *
 * Polarity is always {@code NORTH} (the iron-sulfide lattice naturally
 * aligns its dipoles when heated; there's no user control). Players who
 * want a specific polarity stack a Polarity Inverter next to the block.
 */
public final class PyrrhotiteBlockEntity extends AbstractEmitterBlockEntity {

    public PyrrhotiteBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.PYRRHOTITE.get(), pos, state);
    }

    /** Pyrrhotite is heat-driven; redstone/FE don't activate it. Suppress
     *  the inherited power-source + energy-buffer tooltip lines. */
    @Override
    protected boolean acceptsPower() { return false; }

    /** Max Catalyst scan radius — sized to the cosmic catalyst tier. We always
     *  scan this cube; cheaper-tier catalysts found within the cube self-gate
     *  by checking the pyrrhotite's distance against their own transmitRadius.
     *  7³ = 729 block-state reads at worst per pyrrhotite tick — still cheap. */
    private static final int MAX_CATALYST_SCAN_RADIUS = PyrrhotiteHeatResolver.MAX_CATALYST_SCAN_RADIUS;

    /** Tick of the last full heat/catalyst scan; gates the expensive cube scan. */
    private long lastScanTick = Long.MIN_VALUE;

    @Override
    protected @Nullable MagneticField computeField(final BlockState state) {
        final Level level = getLevel();
        if (level == null) return null;

        // Throttle the 15x15x15 catalyst scan. A cold pyrrhotite polls on the short
        // interval to notice newly-added heat; an already-hot one re-checks on the
        // longer "residual" interval, so it coasts on residual heat and stays warm a
        // beat after its source is removed (and costs less while running). The field
        // is rebuilt from the cached heat every tick (cheap), so it never flickers
        // between scans.
        final int interval = (lastObservedHeat == BlazeBurnerBlock.HeatLevel.NONE)
                ? com.stonytark.magnetization.config.MagConfig.pyrrhotiteScanTicks()
                : com.stonytark.magnetization.config.MagConfig.pyrrhotiteResidualScanTicks();
        final long gameTime = level.getGameTime();
        // `lastScanTick == MIN_VALUE` forces the first scan: subtracting MIN_VALUE
        // would overflow to a negative delta, so the throttle would never fire and
        // the ore would never observe heat (never become magnetic).
        if (lastScanTick == Long.MIN_VALUE || gameTime - lastScanTick >= interval) {
            lastScanTick = gameTime;
            recomputeHeat(level);
        }

        final MagneticStrength strength = strengthForHeat(lastObservedHeat);
        if (strength == null) return null;
        return new MagneticField(
                Vec3.atCenterOf(getBlockPos()),
                new Vec3(0, 1, 0),
                MagneticPolarity.NORTH,
                strength,
                MagneticField.Shape.OMNIDIRECTIONAL
        );
    }

    /** Full heat re-evaluation: 6-neighbour direct heat + the catalyst cube scan.
     *  Updates {@link #lastObservedHeat} (+ syncs on change). Gated by computeField. */
    private void recomputeHeat(final Level level) {
        final BlockPos pos = getBlockPos();

        // 1) Direct heat sources touching the pyrrhotite itself.
        final BlazeBurnerBlock.HeatLevel max = PyrrhotiteHeatResolver.resolve(level, pos);

        // Persist + sync only when the observed heat actually changes.
        if (lastObservedHeat != max) {
            lastObservedHeat = max;
            setChanged();
            if (level instanceof net.minecraft.server.level.ServerLevel server) {
                markForClientSync(server);
            }
        }
    }

    /** Pure heat→strength mapping. Extracted so the boundary cases and the
     *  intentional {@code NONE → null} (no field) can be regression-tested
     *  without a live Create blaze burner. */
    public static @Nullable MagneticStrength strengthForHeat(final BlazeBurnerBlock.HeatLevel heat) {
        return PyrrhotiteHeatResolver.strengthForHeat(heat);
    }

    /** Max heat level across the 6 axis-aligned neighbours of {@code pos}.
     *  Recognised sources (in priority order):
     *  <ul>
     *    <li>Create's {@code BlazeBurnerBlock.HEAT_LEVEL} — direct mapping.</li>
     *    <li>{@code minecraft:lava} (any level) → SEETHING.</li>
     *    <li>{@code minecraft:fire} / {@code soul_fire} / {@code magma_block} → KINDLED.</li>
     *    <li>Lit {@code campfire} / {@code soul_campfire} → SMOULDERING.</li>
     *  </ul>
     *  Falling back to vanilla heat sources fixes the common "I put a
     *  campfire next to pyrrhotite, why doesn't it work" complaint — only
     *  Create's blaze burner has the HEAT_LEVEL property, so the original
     *  scan silently missed everything else. */
    private static BlazeBurnerBlock.HeatLevel scanDirectHeat(final Level level, final BlockPos pos) {
        return PyrrhotiteHeatResolver.scanDirectHeat(level, pos);
    }

    /** Map a single block state to a Create-equivalent heat tier. */
    private static BlazeBurnerBlock.HeatLevel heatOf(final BlockState state) {
        return PyrrhotiteHeatResolver.heatOf(state);
    }

    /** Cached last-tick heat reading for tooltip surfacing. Updated each
     *  {@link #computeField} pass; cleared to NONE when no heat source touches.
     *  Exposed via {@link #extraTooltipLines} so the player can see *why* the
     *  block is or isn't emitting without consulting external tooling.
     *  Serialised to NBT (see {@link #saveAdditional}) so the client copy
     *  surfaces the correct value — without that round-trip the WTHIT hover
     *  always reads NONE because the field is transient on the server BE. */
    private BlazeBurnerBlock.HeatLevel lastObservedHeat = BlazeBurnerBlock.HeatLevel.NONE;

    /** The heat level observed at the last scan (NONE = no adjacent source). Exposed
     *  for gametests + HUD; reflects whether the ore is heat-activated right now. */
    public BlazeBurnerBlock.HeatLevel observedHeat() { return lastObservedHeat; }

    @Override
    protected void saveAdditional(final net.minecraft.nbt.CompoundTag tag,
                                   final net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("ObservedHeat", lastObservedHeat.name());
    }

    @Override
    protected void loadAdditional(final net.minecraft.nbt.CompoundTag tag,
                                   final net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("ObservedHeat")) {
            try { lastObservedHeat = BlazeBurnerBlock.HeatLevel.valueOf(tag.getString("ObservedHeat")); }
            catch (final IllegalArgumentException ignored) { lastObservedHeat = BlazeBurnerBlock.HeatLevel.NONE; }
        }
    }

    @Override
    public java.util.List<net.minecraft.network.chat.Component> extraTooltipLines(final boolean verbose) {
        final java.util.List<net.minecraft.network.chat.Component> lines = super.extraTooltipLines(verbose);
        final net.minecraft.ChatFormatting colour = switch (lastObservedHeat) {
            case NONE -> net.minecraft.ChatFormatting.DARK_GRAY;
            case SMOULDERING, FADING -> net.minecraft.ChatFormatting.GRAY;
            case KINDLED -> net.minecraft.ChatFormatting.GOLD;
            case SEETHING -> net.minecraft.ChatFormatting.RED;
        };
        lines.add(net.minecraft.network.chat.Component.translatable(
                        "tooltip.magnetization.pyrrhotite.heat",
                        net.minecraft.network.chat.Component.translatable(
                                "tooltip.magnetization.heat." + lastObservedHeat.name().toLowerCase(java.util.Locale.ROOT)))
                .withStyle(colour));
        return lines;
    }
}
