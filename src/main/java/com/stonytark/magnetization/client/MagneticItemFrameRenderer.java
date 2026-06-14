package com.stonytark.magnetization.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stonytark.magnetization.content.itemframe.MagneticItemFrameBlock;
import com.stonytark.magnetization.content.itemframe.MagneticItemFrameBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DirectionalBlock;

/**
 * Draws the item a {@link MagneticItemFrameBlockEntity} holds, flat against the
 * plate's outward face, using the item's {@code FIXED} (item-frame) display
 * transform. Orientation is a best-estimate per facing — verify in-world.
 */
public class MagneticItemFrameRenderer implements BlockEntityRenderer<MagneticItemFrameBlockEntity> {

    public MagneticItemFrameRenderer(final BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(final MagneticItemFrameBlockEntity be, final float partialTick, final PoseStack pose,
                       final MultiBufferSource buffers, final int light, final int overlay) {
        final ItemStack stack = be.getDisplayedItem();
        if (stack.isEmpty()) return;
        final Direction facing = be.getBlockState().getValue(DirectionalBlock.FACING);

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        // Map the FIXED item (front = +Z / south) to face along `facing`.
        switch (facing) {
            case NORTH -> pose.mulPose(Axis.YP.rotationDegrees(180));
            case SOUTH -> { }
            case WEST  -> pose.mulPose(Axis.YP.rotationDegrees(90));
            case EAST  -> pose.mulPose(Axis.YP.rotationDegrees(270));
            case UP    -> pose.mulPose(Axis.XP.rotationDegrees(-90));
            case DOWN  -> pose.mulPose(Axis.XP.rotationDegrees(90));
        }
        pose.translate(0.0, 0.0, 0.5 - 0.02); // out to just under the face
        pose.scale(0.55f, 0.55f, 0.55f);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, be.getLevel(), (int) be.getBlockPos().asLong());
        pose.popPose();
    }
}
