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

        // Spin angle (deg) while powered — advances with game time + BE direction.
        float spin = 0f;
        if (be.isSpinning() && be.getLevel() != null) {
            spin = ((be.getLevel().getGameTime() + partialTick) * 4.0f * be.spinDir()) % 360.0f;
        }

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);

        if (facing == Direction.UP || facing == Direction.DOWN) {
            // Floor/ceiling: the item stands upright and hovers off the plate,
            // spinning about the vertical axis.
            pose.translate(0.0, facing == Direction.UP ? 0.45 : -0.45, 0.0);
            pose.mulPose(Axis.YP.rotationDegrees(spin));
            pose.scale(0.7f, 0.7f, 0.7f);
        } else {
            // Wall: the item sits flat on the outward face, spinning in-plane.
            switch (facing) {
                case NORTH -> pose.mulPose(Axis.YP.rotationDegrees(180));
                case WEST  -> pose.mulPose(Axis.YP.rotationDegrees(90));
                case EAST  -> pose.mulPose(Axis.YP.rotationDegrees(270));
                default    -> { } // SOUTH
            }
            pose.translate(0.0, 0.0, 0.5 - 0.02); // out to just under the face
            pose.mulPose(Axis.ZP.rotationDegrees(spin));
            pose.scale(0.55f, 0.55f, 0.55f);
        }

        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, be.getLevel(), (int) be.getBlockPos().asLong());
        pose.popPose();
    }
}
