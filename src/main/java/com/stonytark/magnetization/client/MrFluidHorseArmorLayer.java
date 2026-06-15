package com.stonytark.magnetization.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.mrarmor.MrFluidHorseArmorItem;
import com.stonytark.magnetization.registry.MagDataComponents;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.ItemStack;

/**
 * Renders MR Fluid Horse Armor as the animated MR fluid — the equine mirror of
 * {@link MrLiquidArmorLayer}. The item's own equipment texture
 * ({@code horse_armor_mr_liquid.png}) is fully transparent so vanilla's
 * {@code HorseArmorLayer} draws nothing; this layer is the sole renderer, binding
 * a per-frame fluid texture for the current time and swapping to the rigid
 * "hardened" texture while {@link MagDataComponents#HARDENED_UNTIL} is active.
 */
public final class MrFluidHorseArmorLayer extends RenderLayer<Horse, HorseModel<Horse>> {

    private static final int FRAMES = 16;
    private static final int FRAME_TIME = 3;

    private final HorseModel<Horse> model;

    public MrFluidHorseArmorLayer(final RenderLayerParent<Horse, HorseModel<Horse>> parent,
                                  final EntityModelSet models) {
        super(parent);
        this.model = new HorseModel<>(models.bakeLayer(ModelLayers.HORSE_ARMOR));
    }

    @Override
    public void render(final PoseStack pose, final MultiBufferSource buffers, final int packedLight,
                       final Horse horse, final float limbSwing, final float limbSwingAmount,
                       final float partialTicks, final float ageInTicks,
                       final float netHeadYaw, final float headPitch) {
        final ItemStack stack = horse.getItemBySlot(EquipmentSlot.CHEST);
        if (!(stack.getItem() instanceof MrFluidHorseArmorItem)) return;

        getParentModel().copyPropertiesTo(model);
        model.prepareMobModel(horse, limbSwing, limbSwingAmount, partialTicks);
        model.setupAnim(horse, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        final ResourceLocation tex = textureFor(stack, horse, ageInTicks);
        final VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
        model.renderToBuffer(pose, vc, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
    }

    private ResourceLocation textureFor(final ItemStack stack, final Horse horse, final float ageInTicks) {
        final Long until = stack.get(MagDataComponents.HARDENED_UNTIL.get());
        final boolean hardened = until != null && horse.level().getGameTime() < until;
        final String path = hardened
                ? "textures/entity/horse/armor/horse_armor_mr_liquid_hardened.png"
                : "textures/entity/horse/armor/horse_armor_mr_liquid_"
                        + (((int) (ageInTicks / FRAME_TIME)) % FRAMES) + ".png";
        return ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, path);
    }
}
