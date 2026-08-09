package com.stonytark.magnetization.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/** Renders a gas with vanilla fluid geometry reflected vertically. */
public final class CeilingGasRenderer {
    private CeilingGasRenderer() {}

    /**
     * NeoForge fluid-rendering hook used by both vanilla and Sodium chunk
     * meshers. The mirrored view makes vanilla sample gas depth from the
     * ceiling, while the mirrored consumer reflects the generated mesh back
     * into the real section.
     */
    public static boolean render(final FluidState fluidState,
                                 final BlockAndTintGetter level,
                                 final BlockPos pos,
                                 final VertexConsumer vertices,
                                 final BlockState blockState) {
        Minecraft.getInstance().getBlockRenderer().getLiquidBlockRenderer().tesselate(
                new MirroredBlockAndTintGetter(level, pos.getY()),
                pos,
                new MirroredVertexConsumer(vertices, pos.getY() & 15),
                blockState,
                fluidState);
        return true;
    }

    /** Presents blocks above the gas as blocks below it, and vice versa. */
    private record MirroredBlockAndTintGetter(BlockAndTintGetter delegate, int originY)
            implements BlockAndTintGetter {

        private BlockPos mirror(final BlockPos pos) {
            return new BlockPos(pos.getX(), 2 * originY - pos.getY(), pos.getZ());
        }

        private Direction mirror(final Direction direction) {
            return switch (direction) {
                case UP -> Direction.DOWN;
                case DOWN -> Direction.UP;
                default -> direction;
            };
        }

        @Override
        public BlockEntity getBlockEntity(final BlockPos pos) {
            return delegate.getBlockEntity(mirror(pos));
        }

        @Override
        public BlockState getBlockState(final BlockPos pos) {
            return delegate.getBlockState(mirror(pos));
        }

        @Override
        public FluidState getFluidState(final BlockPos pos) {
            return delegate.getFluidState(mirror(pos));
        }

        @Override
        public float getShade(final Direction direction, final boolean shade) {
            return delegate.getShade(mirror(direction), shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return delegate.getLightEngine();
        }

        @Override
        public int getBlockTint(final BlockPos pos, final ColorResolver resolver) {
            return delegate.getBlockTint(mirror(pos), resolver);
        }

        @Override
        public int getBrightness(final LightLayer layer, final BlockPos pos) {
            return delegate.getBrightness(layer, mirror(pos));
        }

        @Override
        public int getRawBrightness(final BlockPos pos, final int skyDarken) {
            return delegate.getRawBrightness(mirror(pos), skyDarken);
        }

        @Override
        public boolean canSeeSky(final BlockPos pos) {
            return delegate.canSeeSky(mirror(pos));
        }

        @Override
        public int getHeight() {
            return delegate.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return delegate.getMinBuildHeight();
        }
    }

    /** Reflects section-local geometry around the center of the gas block. */
    private record MirroredVertexConsumer(VertexConsumer delegate, int sectionY)
            implements VertexConsumer {

        @Override
        public VertexConsumer addVertex(final float x, final float y, final float z) {
            delegate.addVertex(x, 2.0F * sectionY + 1.0F - y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(final int red, final int green, final int blue, final int alpha) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(final float u, final float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(final int u, final int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(final int u, final int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(final float x, final float y, final float z) {
            delegate.setNormal(x, -y, z);
            return this;
        }
    }
}
