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

        // Spin angle (deg) while powered — advances with game time. The mode picks
        // the axis + sign (left/right = primary axis ±, up/down = pitch ±).
        final int mode = be.spinMode();
        float ang = 0f;
        if (be.isSpinning() && be.getLevel() != null) {
            ang = ((be.getLevel().getGameTime() + partialTick) * 4.0f) % 360.0f;
        }

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);

        if (facing == Direction.UP || facing == Direction.DOWN) {
            // Floor/ceiling: the plate lies flush on the surface (thin, ~1px), so
            // the item hovers in the open middle of the block — clear of the plate
            // and of any neighbouring block. Left/right = turntable about Y.
            pose.translate(0.0, facing == Direction.UP ? 0.1 : -0.1, 0.0);
            applySpin(pose, mode, Axis.YP, ang);
            pose.scale(0.6f, 0.6f, 0.6f);
        } else {
            // Wall: the item sits flat on the outward face. Left/right = pinwheel
            // about the outward Z axis.
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
            applySpin(pose, mode, Axis.ZP, ang);
            pose.scale(0.55f, 0.55f, 0.55f);
        }

        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, be.getLevel(), (int) be.getBlockPos().asLong());
        pose.popPose();
    }

    /** Apply the spin for the BE's mode: left/right turn about the placement's
     *  primary axis (±), up/down tumble about the item's horizontal (X) axis (±). */
    private static void applySpin(final PoseStack pose, final int mode, final Axis primary, final float ang) {
        switch (mode) {
            case MagneticItemFrameBlockEntity.SPIN_RIGHT -> pose.mulPose(primary.rotationDegrees(-ang));
            case MagneticItemFrameBlockEntity.SPIN_UP    -> pose.mulPose(Axis.XP.rotationDegrees(ang));
            case MagneticItemFrameBlockEntity.SPIN_DOWN  -> pose.mulPose(Axis.XP.rotationDegrees(-ang));
            default                                      -> pose.mulPose(primary.rotationDegrees(ang)); // SPIN_LEFT
        }
    }
}
