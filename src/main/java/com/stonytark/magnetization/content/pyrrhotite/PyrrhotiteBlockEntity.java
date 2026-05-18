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
    private static final int MAX_CATALYST_SCAN_RADIUS = 7;

    @Override
    protected @Nullable MagneticField computeField(final BlockState state) {
        final Level level = getLevel();
        if (level == null) return null;
        final BlockPos pos = getBlockPos();

        // 1) Direct heat sources touching the pyrrhotite itself.
        BlazeBurnerBlock.HeatLevel max = scanDirectHeat(level, pos);

        // 2) Heat forwarded through any Catalyst whose own transmitRadius
        //    reaches this pyrrhotite. Each tier (basic 3 / enhanced 5 /
        //    cosmic 7) decides its own range, so mixed-tier networks stack.
        for (int dx = -MAX_CATALYST_SCAN_RADIUS; dx <= MAX_CATALYST_SCAN_RADIUS; dx++) {
            for (int dy = -MAX_CATALYST_SCAN_RADIUS; dy <= MAX_CATALYST_SCAN_RADIUS; dy++) {
                for (int dz = -MAX_CATALYST_SCAN_RADIUS; dz <= MAX_CATALYST_SCAN_RADIUS; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    final BlockPos at = pos.offset(dx, dy, dz);
                    if (!(level.getBlockState(at).getBlock() instanceof PyrrhotiteCatalystBlock cat)) continue;
                    final int chebyshev = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                    if (chebyshev > cat.transmitRadius()) continue;
                    final BlazeBurnerBlock.HeatLevel relayed = scanDirectHeat(level, at);
                    if (relayed.ordinal() > max.ordinal()) max = relayed;
                }
            }
        }

        // Persist + sync only when the observed heat actually changes —
        // tooltip line shows the cached value, and without an explicit sync
        // the client copy stays at the boot-time NONE.
        if (lastObservedHeat != max) {
            lastObservedHeat = max;
            setChanged();
            if (level instanceof net.minecraft.server.level.ServerLevel server) {
                markForClientSync(server);
            }
        }
        final MagneticStrength strength = strengthForHeat(max);
        if (strength == null) return null;

        return new MagneticField(
                Vec3.atCenterOf(pos),
                new Vec3(0, 1, 0),
                MagneticPolarity.NORTH,
                strength,
                MagneticField.Shape.OMNIDIRECTIONAL
        );
    }

    /** Pure heat→strength mapping. Extracted so the boundary cases and the
     *  intentional {@code NONE → null} (no field) can be regression-tested
     *  without a live Create blaze burner. */
    public static @Nullable MagneticStrength strengthForHeat(final BlazeBurnerBlock.HeatLevel heat) {
        return switch (heat) {
            case NONE -> null;
            case SMOULDERING, FADING -> MagneticStrength.WEAK;
            case KINDLED -> MagneticStrength.STRONG;
            case SEETHING -> MagneticStrength.EXTREME;
        };
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
        BlazeBurnerBlock.HeatLevel max = BlazeBurnerBlock.HeatLevel.NONE;
        for (final Direction dir : Direction.values()) {
            final BlockState neighbour = level.getBlockState(pos.relative(dir));
            final BlazeBurnerBlock.HeatLevel observed = heatOf(neighbour);
            if (observed.ordinal() > max.ordinal()) max = observed;
        }
        return max;
    }

    /** Map a single block state to a Create-equivalent heat tier. */
    private static BlazeBurnerBlock.HeatLevel heatOf(final BlockState state) {
        if (state.hasProperty(BlazeBurnerBlock.HEAT_LEVEL)) {
            return state.getValue(BlazeBurnerBlock.HEAT_LEVEL);
        }
        final var block = state.getBlock();
        if (block == net.minecraft.world.level.block.Blocks.LAVA) {
            return BlazeBurnerBlock.HeatLevel.SEETHING;
        }
        if (block == net.minecraft.world.level.block.Blocks.FIRE
                || block == net.minecraft.world.level.block.Blocks.SOUL_FIRE
                || block == net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) {
            return BlazeBurnerBlock.HeatLevel.KINDLED;
        }
        if ((block == net.minecraft.world.level.block.Blocks.CAMPFIRE
                || block == net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                && state.hasProperty(net.minecraft.world.level.block.CampfireBlock.LIT)
                && state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) {
            return BlazeBurnerBlock.HeatLevel.SMOULDERING;
        }
        return BlazeBurnerBlock.HeatLevel.NONE;
    }

    /** Cached last-tick heat reading for tooltip surfacing. Updated each
     *  {@link #computeField} pass; cleared to NONE when no heat source touches.
     *  Exposed via {@link #extraTooltipLines} so the player can see *why* the
     *  block is or isn't emitting without consulting external tooling.
     *  Serialised to NBT (see {@link #saveAdditional}) so the client copy
     *  surfaces the correct value — without that round-trip the WTHIT hover
     *  always reads NONE because the field is transient on the server BE. */
    private BlazeBurnerBlock.HeatLevel lastObservedHeat = BlazeBurnerBlock.HeatLevel.NONE;

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
                        "tooltip.magnetization.pyrrhotite.heat", lastObservedHeat.name())
                .withStyle(colour));
        return lines;
    }
}
