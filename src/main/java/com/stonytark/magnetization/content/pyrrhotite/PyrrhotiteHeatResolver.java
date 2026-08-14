package com.stonytark.magnetization.content.pyrrhotite;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.stonytark.magnetization.api.MagneticStrength;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Shared direct-and-catalyst heat resolution for Pyrrhotite blocks and golems. */
public final class PyrrhotiteHeatResolver {
    public static final int MAX_CATALYST_SCAN_RADIUS = 7;

    private PyrrhotiteHeatResolver() {}

    public static BlazeBurnerBlock.HeatLevel resolve(final Level level, final BlockPos pos) {
        BlazeBurnerBlock.HeatLevel max = scanDirectHeat(level, pos);
        for (int dx = -MAX_CATALYST_SCAN_RADIUS; dx <= MAX_CATALYST_SCAN_RADIUS; dx++) {
            for (int dy = -MAX_CATALYST_SCAN_RADIUS; dy <= MAX_CATALYST_SCAN_RADIUS; dy++) {
                for (int dz = -MAX_CATALYST_SCAN_RADIUS; dz <= MAX_CATALYST_SCAN_RADIUS; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    final BlockPos at = pos.offset(dx, dy, dz);
                    if (!(level.getBlockState(at).getBlock() instanceof PyrrhotiteCatalystBlock catalyst)) continue;
                    final int distance = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                    if (distance > catalyst.transmitRadius()) continue;
                    final BlazeBurnerBlock.HeatLevel relayed = scanDirectHeat(level, at);
                    if (relayed.ordinal() > max.ordinal()) max = relayed;
                }
            }
        }
        return max;
    }

    public static @Nullable MagneticStrength strengthForHeat(final BlazeBurnerBlock.HeatLevel heat) {
        return switch (heat) {
            case NONE -> null;
            case SMOULDERING, FADING -> MagneticStrength.WEAK;
            case KINDLED -> MagneticStrength.STRONG;
            case SEETHING -> MagneticStrength.EXTREME;
        };
    }

    public static BlazeBurnerBlock.HeatLevel scanDirectHeat(final Level level, final BlockPos pos) {
        BlazeBurnerBlock.HeatLevel max = BlazeBurnerBlock.HeatLevel.NONE;
        for (final Direction direction : Direction.values()) {
            final BlazeBurnerBlock.HeatLevel observed = heatOf(level.getBlockState(pos.relative(direction)));
            if (observed.ordinal() > max.ordinal()) max = observed;
        }
        return max;
    }

    public static BlazeBurnerBlock.HeatLevel heatOf(final BlockState state) {
        if (state.hasProperty(BlazeBurnerBlock.HEAT_LEVEL)) return state.getValue(BlazeBurnerBlock.HEAT_LEVEL);
        final var block = state.getBlock();
        if (block == Blocks.LAVA) return BlazeBurnerBlock.HeatLevel.SEETHING;
        if (block == Blocks.FIRE || block == Blocks.SOUL_FIRE || block == Blocks.MAGMA_BLOCK) {
            return BlazeBurnerBlock.HeatLevel.KINDLED;
        }
        if ((block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE)
                && state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT)) {
            return BlazeBurnerBlock.HeatLevel.SMOULDERING;
        }
        return BlazeBurnerBlock.HeatLevel.NONE;
    }
}
