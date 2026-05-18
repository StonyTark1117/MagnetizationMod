package com.stonytark.magnetization.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.item.MagneticElytraItem;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * Custom elytra layer for {@link MagneticElytraItem}. Renders the metal-base
 * cape texture and applies a dye tint when the stack carries a
 * {@link DataComponents#DYED_COLOR} component — same crafting flow as a
 * leather chestplate (drop the elytra + dyes in a 2×2 grid).
 *
 * <p>Vanilla's {@link ElytraLayer#shouldRender} hard-codes the check to
 * {@code Items.ELYTRA}, so subclassed elytra items render nothing unless we
 * install our own layer instance. We add this layer to every humanoid renderer
 * (player + armor stand + mob fallbacks) via {@code EntityRenderersEvent.AddLayers}.
 *
 * <p>The render override is a copy of vanilla's ElytraLayer.render with one
 * change — the per-vertex colour passed into {@link ElytraModel#renderToBuffer}
 * carries the resolved dye colour instead of the default white. We hold our
 * own private ElytraModel instance because the parent's private model field
 * isn't visible to subclasses.
 */
public final class MagneticElytraLayer<T extends LivingEntity, M extends EntityModel<T>>
        extends ElytraLayer<T, M> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "textures/entity/magnetic_elytra.png");

    /** Default tint when the stack has no DYED_COLOR component — full white,
     *  i.e. show the base texture untouched. Matches vanilla elytra behaviour. */
    private static final int DEFAULT_TINT = 0xFFFFFFFF;

    private final ElytraModel<T> elytraModel;

    public MagneticElytraLayer(final RenderLayerParent<T, M> parent, final EntityModelSet models) {
        super(parent, models);
        this.elytraModel = new ElytraModel<>(models.bakeLayer(ModelLayers.ELYTRA));
    }

    @Override
    public boolean shouldRender(final ItemStack stack, final T entity) {
        return stack.getItem() instanceof MagneticElytraItem;
    }

    @Override
    public ResourceLocation getElytraTexture(final ItemStack stack, final T entity) {
        return TEXTURE;
    }

    @Override
    public void render(final PoseStack pose, final MultiBufferSource buffers,
                       final int packedLight, final T entity,
                       final float limbSwing, final float limbSwingAmount,
                       final float partialTicks, final float ageInTicks,
                       final float netHeadYaw, final float headPitch) {
        final ItemStack stack = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (!shouldRender(stack, entity)) return;

        final ResourceLocation tex = getElytraTexture(stack, entity);
        final int tint = resolveDyeTint(stack);

        pose.pushPose();
        pose.translate(0.0F, 0.0F, 0.125F);
        this.getParentModel().copyPropertiesTo(this.elytraModel);
        this.elytraModel.setupAnim(entity, limbSwing, limbSwingAmount,
                ageInTicks, netHeadYaw, headPitch);
        final VertexConsumer vc = ItemRenderer.getArmorFoilBuffer(
                buffers, RenderType.armorCutoutNoCull(tex), stack.hasFoil());
        this.elytraModel.renderToBuffer(pose, vc, packedLight, OverlayTexture.NO_OVERLAY, tint);
        pose.popPose();
    }

    /** Decode the stack's {@link DyedItemColor} into the packed ARGB int the
     *  renderer expects. Stack with no DYED_COLOR (i.e. undyed elytra) gets
     *  {@link #DEFAULT_TINT} so the base texture renders as-is. */
    private static int resolveDyeTint(final ItemStack stack) {
        final DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);
        if (dyed == null) return DEFAULT_TINT;
        // DyedItemColor.rgb() returns 0xRRGGBB. Pack to 0xFFRRGGBB.
        return 0xFF000000 | (dyed.rgb() & 0x00FFFFFF);
    }
}
