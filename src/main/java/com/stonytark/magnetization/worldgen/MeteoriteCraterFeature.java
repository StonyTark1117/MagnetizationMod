package com.stonytark.magnetization.worldgen;

import com.mojang.serialization.Codec;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Procedural crater + meteorite_core placement. Replaces the bare single-block
 * meteorite spawn that worldgen used to drop. Each fired feature picks a radius
 * 3..5, carves a hemispherical bowl into the surface, paints the bowl interior
 * with magnetite + raw_magnetite + petrified_wood splashes, scatters impact
 * debris on the rim, and drops a {@link MagBlocks#METEORITE_CORE} at the
 * floor's centre.
 *
 * <p>Implementation choice: pure procedural Java rather than NBT templates
 * (the path Phase B's plan reserved for later). Templates give artist control
 * but force per-shape JSON; procedural keeps every meteorite shaped uniquely
 * from a single deterministic source. When NBT templates land we can switch
 * configured_feature/meteorite_core.json to a jigsaw + structure_set without
 * touching this class.
 */
public final class MeteoriteCraterFeature extends Feature<NoneFeatureConfiguration> {

    public MeteoriteCraterFeature(final Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(final FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        final WorldGenLevel level = ctx.level();
        final BlockPos origin = ctx.origin();
        final RandomSource rand = ctx.random();

        // Re-anchor to the surface for our centre column — the placed_feature
        // already pinned WORLD_SURFACE_WG, but the origin's Y may sit on the
        // wrong column if the heightmap shifted between scan and place.
        final int centreX = origin.getX();
        final int centreZ = origin.getZ();
        final int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, centreX, centreZ);
        final BlockPos centre = new BlockPos(centreX, surfaceY - 1, centreZ);

        // Skip ocean / underwater spawns. WORLD_SURFACE_WG counts the topmost
        // non-air block, but the column may still be water all the way down.
        // If the surface block we'd carve into is water, abort cleanly — better
        // to have one fewer meteorite this chunk than a half-submerged crater
        // that looks like terrain corruption.
        if (level.getBlockState(centre).getFluidState().getType() != net.minecraft.world.level.material.Fluids.EMPTY) {
            return false;
        }

        // Radius 3..5 (inclusive). Larger craters are rarer because the cubic
        // term in the bowl-volume formula otherwise dominates the workload.
        final int radius = 3 + rand.nextInt(3);
        final int radiusSq = radius * radius;

        // Pass 1 — carve the bowl + paint the interior. Walk a square cross-section
        // around the centre; for each column compute its bowl depth from the
        // distance to centre and write that column.
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                final int distSq = dx * dx + dz * dz;
                if (distSq > radiusSq) continue;

                final float distFrac = (float) Math.sqrt(distSq) / radius;
                final int bowlDepth = Math.max(1, Math.round((1f - distFrac) * radius));

                final int colX = centreX + dx;
                final int colZ = centreZ + dz;
                final int colSurfY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, colX, colZ);

                // Air-out the column above the bowl rim — anything that
                // protrudes above where the impact "scorched" the terrain.
                for (int y = colSurfY - 1; y >= colSurfY - bowlDepth; y--) {
                    if (y < level.getMinBuildHeight()) break;
                    final BlockState fill = pickCraterFill(rand, distFrac);
                    level.setBlock(new BlockPos(colX, y, colZ), fill, 2);
                }
                // Carve out anything still standing above this column up to the
                // original surface (vegetation, snow, etc.) so the bowl reads cleanly.
                for (int y = colSurfY; y < colSurfY + 2; y++) {
                    final BlockPos above = new BlockPos(colX, y, colZ);
                    if (level.getBlockState(above).isAir()) continue;
                    if (Mth.abs(dx) + Mth.abs(dz) <= radius - 1) {
                        level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }

        // Pass 2 — impact debris on the rim. A handful of magnetite_ore /
        // petrified_wood / cobblestone surface scatters just beyond the bowl edge.
        final int debrisCount = 4 + rand.nextInt(4);
        for (int i = 0; i < debrisCount; i++) {
            final double theta = rand.nextDouble() * Math.PI * 2.0;
            final double r = radius + 1 + rand.nextDouble() * 2.0;
            final int rx = centreX + (int) Math.round(Math.cos(theta) * r);
            final int rz = centreZ + (int) Math.round(Math.sin(theta) * r);
            final int ry = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, rx, rz);
            final BlockState debris = pickDebris(rand);
            level.setBlock(new BlockPos(rx, ry, rz), debris, 2);
        }

        // Pass 3 — the core itself, planted at the bowl floor's centre.
        // Slightly recessed so the player has to dig one block to dislodge it.
        final BlockPos corePos = new BlockPos(centreX, centre.getY() - (radius - 1), centreZ);
        level.setBlock(corePos, MagBlocks.METEORITE_CORE.get().defaultBlockState(), 2);
        return true;
    }

    /** Block to put inside the carved bowl. Deeper / closer-to-centre columns
     *  favour magnetite/raw_magnetite (denser ore signature); outer columns
     *  fade to petrified_wood / cobblestone as the impact shock dissipates. */
    private static BlockState pickCraterFill(final RandomSource rand, final float distFrac) {
        final float roll = rand.nextFloat();
        if (distFrac < 0.5f) {
            // Core of the impact zone — mostly magnetic mass.
            if (roll < 0.55f) return MagBlocks.MAGNETITE_BLOCK.get().defaultBlockState();
            if (roll < 0.85f) return MagBlocks.RAW_MAGNETITE_BLOCK.get().defaultBlockState();
            return MagBlocks.PETRIFIED_WOOD.get().defaultBlockState();
        }
        // Outer rim — fade to mundane.
        if (roll < 0.35f) return MagBlocks.RAW_MAGNETITE_BLOCK.get().defaultBlockState();
        if (roll < 0.65f) return MagBlocks.PETRIFIED_WOOD.get().defaultBlockState();
        return Blocks.COBBLESTONE.defaultBlockState();
    }

    /** Surface debris flung outside the rim. Mostly magnetite ores, a sprinkle
     *  of petrified_wood as if vegetation got fossilised by the impact field. */
    private static BlockState pickDebris(final RandomSource rand) {
        final float roll = rand.nextFloat();
        if (roll < 0.5f) return MagBlocks.MAGNETITE_ORE.get().defaultBlockState();
        if (roll < 0.85f) return MagBlocks.PETRIFIED_WOOD.get().defaultBlockState();
        return Blocks.COBBLESTONE.defaultBlockState();
    }
}
