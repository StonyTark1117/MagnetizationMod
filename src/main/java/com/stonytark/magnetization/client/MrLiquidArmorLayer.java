package com.stonytark.magnetization.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.mrarmor.MrLiquidArmorItem;
import com.stonytark.magnetization.registry.MagDataComponents;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Renders worn Magnetorheological armor as the animated MR fluid — the same
 * rippling water-derived surface as the item icon — and swaps it to a brighter,
 * rigid "hardened" texture for the post-hit window driven by
 * {@link MagDataComponents#HARDENED_UNTIL} (set in {@code MrArmorHandler}).
 *
 * <p>The material's own layer texture ({@code mr_liquid_layer_1/2.png}) is fully
 * transparent, so vanilla's {@code HumanoidArmorLayer} draws nothing for these
 * pieces and this layer is the sole renderer — avoiding a double draw. Animation
 * is per-frame textures {@code mr_liquid_layer_<n>_<f>.png}; rather than a ticking
 * atlas sprite (armor textures aren't atlas sprites, so {@code .mcmeta} animation
 * is ignored), we just bind the frame for the current time each render.
 */
public final class MrLiquidArmorLayer<T extends LivingEntity, M extends HumanoidModel<T>>
        extends RenderLayer<T, M> {

    private static final int FRAMES = 16;
    private static final int FRAME_TIME = 3; // ticks per frame (~2.4s loop)

    private final HumanoidModel<T> innerModel; // leggings (layer 2)
    private final HumanoidModel<T> outerModel; // helmet/chest/boots (layer 1)

    public MrLiquidArmorLayer(final RenderLayerParent<T, M> parent, final EntityModelSet models) {
        super(parent);
        this.innerModel = new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        this.outerModel = new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
    }

    @Override
    public void render(final PoseStack pose, final MultiBufferSource buffers, final int packedLight,
                       final T entity, final float limbSwing, final float limbSwingAmount,
                       final float partialTicks, final float ageInTicks,
                       final float netHeadYaw, final float headPitch) {
        renderPiece(pose, buffers, packedLight, entity, EquipmentSlot.HEAD, ageInTicks);
        renderPiece(pose, buffers, packedLight, entity, EquipmentSlot.CHEST, ageInTicks);
        renderPiece(pose, buffers, packedLight, entity, EquipmentSlot.LEGS, ageInTicks);
        renderPiece(pose, buffers, packedLight, entity, EquipmentSlot.FEET, ageInTicks);
    }

    private void renderPiece(final PoseStack pose, final MultiBufferSource buffers, final int packedLight,
                             final T entity, final EquipmentSlot slot, final float ageInTicks) {
        final ItemStack stack = entity.getItemBySlot(slot);
        if (!(stack.getItem() instanceof MrLiquidArmorItem)) return;

        final boolean inner = slot == EquipmentSlot.LEGS; // leggings use the inner model + layer 2
        final HumanoidModel<T> model = inner ? innerModel : outerModel;
        getParentModel().copyPropertiesTo(model);
        setPartVisibility(model, slot);

        final ResourceLocation tex = textureFor(stack, entity, inner ? 2 : 1, ageInTicks);
        final VertexConsumer vc = ItemRenderer.getArmorFoilBuffer(
                buffers, RenderType.armorCutoutNoCull(tex), stack.hasFoil());
        model.renderToBuffer(pose, vc, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
    }

    private ResourceLocation textureFor(final ItemStack stack, final T entity, final int layer, final float ageInTicks) {
        final Long until = stack.get(MagDataComponents.HARDENED_UNTIL.get());
        final boolean hardened = until != null && entity.level().getGameTime() < until;
        final String path = hardened
                ? "textures/models/armor/mr_liquid_layer_" + layer + "_hardened.png"
                : "textures/models/armor/mr_liquid_layer_" + layer + "_"
                        + (((int) (ageInTicks / FRAME_TIME)) % FRAMES) + ".png";
        return ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, path);
    }

    /** Mirror of vanilla {@code HumanoidArmorLayer.setPartVisibility}. */
    private static void setPartVisibility(final HumanoidModel<?> model, final EquipmentSlot slot) {
        model.setAllVisible(false);
        switch (slot) {
            case HEAD -> { model.head.visible = true; model.hat.visible = true; }
            case CHEST -> { model.body.visible = true; model.rightArm.visible = true; model.leftArm.visible = true; }
            case LEGS -> { model.body.visible = true; model.rightLeg.visible = true; model.leftLeg.visible = true; }
            case FEET -> { model.rightLeg.visible = true; model.leftLeg.visible = true; }
            default -> { }
        }
    }
}
