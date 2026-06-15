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
            // Floor/ceiling: the plate lies flush on the surface (thin, ~1px), so
            // the item hovers in the open middle of the block — clear of the plate
            // and of any neighbouring block — spinning about the vertical axis.
            pose.translate(0.0, facing == Direction.UP ? 0.1 : -0.1, 0.0);
            pose.mulPose(Axis.YP.rotationDegrees(spin));
            pose.scale(0.6f, 0.6f, 0.6f);
        } else {
            // Wall: the item sits flat on the outward face, spinning in-plane.
            switch (facing) {
                case NORTH -> pose.mulPose(Axis.YP.rotationDegrees(180));
                case WEST  -> pose.mulPose(Axis.YP.rotationDegrees(90));
                case EAST  -> pose.mulPose(Axis.YP.rotationDegrees(270));
                default    -> { } // SOUTH
            }
            // The plate sits flush on the mounting wall (the FACING.opposite face).
            // Local +z points outward (toward FACING). Sit the item just in front
            // of the plate: -0.32 clipped into it, the far face (+0.48) floated a
            // whole block off, so -0.20 keeps it perched on the frame.
            pose.translate(0.0, 0.0, -0.20);
            pose.mulPose(Axis.ZP.rotationDegrees(spin));
            pose.scale(0.55f, 0.55f, 0.55f);
        }

        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, be.getLevel(), (int) be.getBlockPos().asLong());
        pose.popPose();
    }
}
