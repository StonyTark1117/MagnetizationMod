package com.stonytark.magnetization.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/** Small cave-air pockets filled from a supported ceiling or floor. */
public final class GasPocketFeature extends Feature<NoneFeatureConfiguration> {
    private final Supplier<? extends Fluid> gas;
    private final boolean rises;

    public GasPocketFeature(final Codec<NoneFeatureConfiguration> codec,
                            final Supplier<? extends Fluid> gas, final boolean rises) {
        super(codec);
        this.gas = gas;
        this.rises = rises;
    }

    @Override public boolean place(final FeaturePlaceContext<NoneFeatureConfiguration> context) {
        final WorldGenLevel level = context.level();
        final RandomSource random = context.random();
        final int rarity = rises ? com.stonytark.magnetization.config.MagConfig.heliumPocketRarity()
                : com.stonytark.magnetization.config.MagConfig.radonPocketRarity();
        if (random.nextInt(Math.max(1, rarity)) != 0) return false;
        final int configuredMinY = rises ? com.stonytark.magnetization.config.MagConfig.heliumPocketMinY()
                : com.stonytark.magnetization.config.MagConfig.radonPocketMinY();
        final int configuredMaxY = rises ? com.stonytark.magnetization.config.MagConfig.heliumPocketMaxY()
                : com.stonytark.magnetization.config.MagConfig.radonPocketMaxY();
        final int minY = Math.max(level.getMinBuildHeight() + 8, configuredMinY);
        final int maxY = Math.min(level.getMaxBuildHeight() - 9, configuredMaxY);
        if (minY > maxY) return false;
        final BlockPos origin = context.origin().atY(minY + random.nextInt(Math.max(1, maxY - minY + 1)));
        BlockPos anchor = null;
        for (int attempt = 0; attempt < 18 && anchor == null; attempt++) {
            BlockPos cursor = origin.offset(random.nextInt(13) - 6, random.nextInt(9) - 4, random.nextInt(13) - 6);
            if (!level.getBlockState(cursor).isAir()) continue;
            for (int distance = 1; distance <= 8; distance++) {
                final BlockPos support = rises ? cursor.above(distance) : cursor.below(distance);
                if (!level.getBlockState(support).isAir()) {
                    final BlockPos candidate = rises ? support.below() : support.above();
                    if (level.getBlockState(candidate).isAir()) anchor = candidate;
                    break;
                }
            }
        }
        if (anchor == null) return false;

        final int minimum = com.stonytark.magnetization.config.MagConfig.gasPocketMinCells();
        final int maximum = com.stonytark.magnetization.config.MagConfig.gasPocketMaxCells();
        final int wanted = minimum + random.nextInt(Math.max(1, maximum - minimum + 1));
        final ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        final Set<BlockPos> seen = new HashSet<>();
        frontier.add(anchor);
        int placed = 0;
        while (!frontier.isEmpty() && placed < wanted) {
            final BlockPos pos = frontier.removeFirst();
            if (!seen.add(pos) || !level.getBlockState(pos).isAir()) continue;
            level.setBlock(pos, gas.get().defaultFluidState().createLegacyBlock(), 2);
            placed++;
            final net.minecraft.core.Direction[] directions = net.minecraft.core.Direction.values();
            for (int i = directions.length - 1; i > 0; i--) {
                final int j = random.nextInt(i + 1);
                final net.minecraft.core.Direction swap = directions[i];
                directions[i] = directions[j];
                directions[j] = swap;
            }
            for (final net.minecraft.core.Direction direction : directions) {
                if (direction == (rises ? net.minecraft.core.Direction.DOWN : net.minecraft.core.Direction.UP)) continue;
                frontier.addLast(pos.relative(direction));
            }
        }
        return placed >= minimum;
    }
}
