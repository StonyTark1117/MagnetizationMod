package com.stonytark.magnetization.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.stonytark.magnetization.content.shaft.MagneticShaftBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Render the complete original rotor even when Flywheel is enabled for other Create blocks. */
public final class MagneticShaftRenderer extends KineticBlockEntityRenderer<MagneticShaftBlockEntity> {
    public MagneticShaftRenderer(BlockEntityRendererProvider.Context context) { super(context); }
    @Override protected void renderSafe(MagneticShaftBlockEntity shaft, float partialTicks, PoseStack pose,
                                        MultiBufferSource buffers, int light, int overlay) {
        var state = shaft.getBlockState();
        renderRotatingBuffer(shaft, getRotatedModel(shaft, state), pose, buffers.getBuffer(getRenderType(shaft, state)), light);
    }
}
