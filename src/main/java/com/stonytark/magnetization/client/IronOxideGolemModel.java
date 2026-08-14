package com.stonytark.magnetization.client;

import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.animal.IronGolem;

/**
 * Four deliberately different mineral bodies which retain Iron Golem animation
 * semantics without retaining its silhouette. Each profile uses the five part
 * names consumed by {@link IronGolemModel}; mineral plates and crystals are
 * baked into those animated parts.
 */
public final class IronOxideGolemModel extends IronGolemModel<IronGolem> {
    public enum Profile {
        MAGNETITE,
        PYRRHOTITE,
        HEMATITE,
        TITANOMAGNETITE
    }

    public IronOxideGolemModel(final Profile profile) {
        super(createBodyLayer(profile).bakeRoot());
    }

    public static LayerDefinition createBodyLayer(final Profile profile) {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        switch (profile) {
            case MAGNETITE -> magnetite(root);
            case PYRRHOTITE -> pyrrhotite(root);
            case HEMATITE -> hematite(root);
            case TITANOMAGNETITE -> titanomagnetite(root);
        }
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static void magnetite(final PartDefinition root) {
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.5f, -12.0f, -5.0f, 9.0f, 9.0f, 9.0f)
                        .texOffs(24, 0).addBox(-1.0f, -6.0f, -7.0f, 2.0f, 4.0f, 2.0f)
                        .texOffs(37, 0).addBox(-6.0f, -10.0f, -2.0f, 3.0f, 3.0f, 3.0f)
                        .texOffs(60, 0).addBox(3.0f, -8.0f, -1.0f, 3.0f, 4.0f, 3.0f),
                PartPose.offset(0.0f, -7.0f, -2.0f));
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 40).addBox(-10.0f, -3.0f, -6.5f, 20.0f, 13.0f, 12.0f)
                        .texOffs(0, 70).addBox(-5.0f, 10.0f, -3.5f, 10.0f, 5.0f, 7.0f,
                                new CubeDeformation(0.35f))
                        .texOffs(37, 0).addBox(-12.0f, -5.0f, -4.0f, 5.0f, 6.0f, 6.0f)
                        .texOffs(60, 0).addBox(7.0f, -4.0f, -3.0f, 5.0f, 5.0f, 5.0f)
                        .texOffs(24, 0).addBox(-3.0f, 0.0f, -8.0f, 6.0f, 6.0f, 2.0f),
                PartPose.offset(0.0f, -7.0f, 0.0f));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                        .texOffs(60, 21).addBox(-14.0f, -2.5f, -3.5f, 6.0f, 29.0f, 7.0f)
                        .texOffs(37, 0).addBox(-15.5f, 3.0f, -4.5f, 4.0f, 5.0f, 4.0f)
                        .texOffs(60, 0).addBox(-15.0f, 18.0f, 0.5f, 4.0f, 4.0f, 4.0f),
                PartPose.offset(0.0f, -7.0f, 0.0f));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                        .texOffs(60, 58).addBox(8.0f, -2.5f, -3.5f, 6.0f, 29.0f, 7.0f)
                        .texOffs(60, 0).addBox(11.5f, 2.0f, -3.5f, 4.0f, 5.0f, 4.0f)
                        .texOffs(37, 0).addBox(11.0f, 17.0f, 0.0f, 4.0f, 4.0f, 4.0f),
                PartPose.offset(0.0f, -7.0f, 0.0f));
        legs(root, 7.0f, 16.0f, 6.0f, 4.5f);
    }

    private static void pyrrhotite(final PartDefinition root) {
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.5f, -13.0f, -5.0f, 7.0f, 10.0f, 8.0f)
                        .texOffs(24, 0).addBox(-1.0f, -7.0f, -7.0f, 2.0f, 4.0f, 2.0f)
                        .texOffs(60, 0).addBox(-1.0f, -17.0f, -1.0f, 2.0f, 5.0f, 3.0f),
                PartPose.offset(0.0f, -7.0f, -2.0f));
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 40).addBox(-8.0f, -3.0f, -5.0f, 16.0f, 14.0f, 9.0f)
                        .texOffs(0, 70).addBox(-4.0f, 11.0f, -3.0f, 8.0f, 5.0f, 6.0f)
                        .texOffs(24, 0).addBox(-1.0f, -1.0f, -7.0f, 2.0f, 13.0f, 2.0f)
                        .texOffs(37, 0).addBox(-7.0f, -8.0f, 1.0f, 3.0f, 7.0f, 3.0f)
                        .texOffs(60, 0).addBox(4.0f, -6.0f, 1.0f, 3.0f, 6.0f, 3.0f),
                PartPose.offset(0.0f, -7.0f, 0.0f));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                        .texOffs(60, 21).addBox(-12.0f, -2.0f, -2.5f, 4.0f, 30.0f, 5.0f)
                        .texOffs(37, 0).addBox(-15.0f, 2.0f, -1.5f, 3.0f, 8.0f, 3.0f)
                        .texOffs(60, 0).addBox(-14.0f, 15.0f, -1.5f, 2.0f, 7.0f, 3.0f),
                PartPose.offset(0.0f, -7.0f, 0.0f));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                        .texOffs(60, 58).addBox(8.0f, -2.0f, -2.5f, 4.0f, 30.0f, 5.0f)
                        .texOffs(60, 0).addBox(12.0f, 1.0f, -1.5f, 3.0f, 8.0f, 3.0f)
                        .texOffs(37, 0).addBox(12.0f, 15.0f, -1.5f, 2.0f, 7.0f, 3.0f),
                PartPose.offset(0.0f, -7.0f, 0.0f));
        legs(root, 5.0f, 18.0f, 5.0f, 3.5f);
    }

    private static void hematite(final PartDefinition root) {
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-5.0f, -11.0f, -5.5f, 10.0f, 8.0f, 9.0f)
                        .texOffs(24, 0).addBox(-1.5f, -6.0f, -7.5f, 3.0f, 3.0f, 2.0f)
                        .texOffs(0, 70).addBox(-6.0f, -12.5f, -6.0f, 12.0f, 2.0f, 10.0f),
                PartPose.offset(0.0f, -7.0f, -2.0f));
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 40).addBox(-10.0f, -3.0f, -6.0f, 20.0f, 13.0f, 11.0f)
                        .texOffs(0, 70).addBox(-5.0f, 10.0f, -3.5f, 10.0f, 5.0f, 7.0f,
                                new CubeDeformation(0.5f))
                        .texOffs(60, 21).addBox(-11.5f, -1.5f, -7.0f, 23.0f, 3.0f, 2.0f)
                        .texOffs(60, 58).addBox(-9.0f, 2.0f, -7.5f, 18.0f, 3.0f, 2.0f)
                        .texOffs(37, 0).addBox(-6.0f, 6.0f, -8.0f, 12.0f, 3.0f, 2.0f),
                PartPose.offset(0.0f, -7.0f, 0.0f));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                        .texOffs(60, 21).addBox(-15.0f, -3.0f, -3.5f, 7.0f, 28.0f, 7.0f)
                        .texOffs(0, 70).addBox(-16.0f, -1.5f, -4.5f, 9.0f, 5.0f, 9.0f),
                PartPose.offset(0.0f, -7.0f, 0.0f));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                        .texOffs(60, 58).addBox(8.0f, -3.0f, -3.5f, 7.0f, 28.0f, 7.0f)
                        .texOffs(0, 70).addBox(7.0f, -1.5f, -4.5f, 9.0f, 5.0f, 9.0f),
                PartPose.offset(0.0f, -7.0f, 0.0f));
        legs(root, 7.0f, 16.0f, 6.0f, 4.5f);
    }

    private static void titanomagnetite(final PartDefinition root) {
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.5f, -13.0f, -5.5f, 9.0f, 11.0f, 9.0f)
                        .texOffs(24, 0).addBox(-1.0f, -7.0f, -7.5f, 2.0f, 4.0f, 2.0f)
                        .texOffs(37, 0).addBox(-5.5f, -15.0f, -4.0f, 3.0f, 4.0f, 4.0f)
                        .texOffs(60, 0).addBox(-1.5f, -17.0f, -3.0f, 3.0f, 5.0f, 4.0f)
                        .texOffs(37, 0).addBox(2.5f, -15.0f, -4.0f, 3.0f, 4.0f, 4.0f),
                PartPose.offset(0.0f, -7.0f, -2.0f));
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 40).addBox(-11.0f, -4.0f, -7.0f, 22.0f, 15.0f, 13.0f)
                        .texOffs(0, 70).addBox(-5.5f, 11.0f, -4.0f, 11.0f, 5.0f, 8.0f,
                                new CubeDeformation(0.6f))
                        .texOffs(60, 21).addBox(-9.0f, -2.0f, -8.5f, 18.0f, 2.0f, 2.0f)
                        .texOffs(60, 58).addBox(-9.0f, 8.0f, -8.5f, 18.0f, 2.0f, 2.0f)
                        .texOffs(37, 0).addBox(-9.0f, 0.0f, -8.5f, 2.0f, 8.0f, 2.0f)
                        .texOffs(37, 0).addBox(7.0f, 0.0f, -8.5f, 2.0f, 8.0f, 2.0f)
                        .texOffs(24, 0).addBox(-3.5f, 1.0f, -8.0f, 7.0f, 6.0f, 2.0f),
                PartPose.offset(0.0f, -7.0f, 0.0f));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                        .texOffs(60, 21).addBox(-16.0f, -3.5f, -4.0f, 8.0f, 31.0f, 8.0f)
                        .texOffs(0, 70).addBox(-17.0f, 1.0f, -5.0f, 10.0f, 5.0f, 10.0f)
                        .texOffs(37, 0).addBox(-17.0f, 19.0f, -5.0f, 10.0f, 4.0f, 10.0f),
                PartPose.offset(0.0f, -7.0f, 0.0f));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                        .texOffs(60, 58).addBox(8.0f, -3.5f, -4.0f, 8.0f, 31.0f, 8.0f)
                        .texOffs(0, 70).addBox(7.0f, 1.0f, -5.0f, 10.0f, 5.0f, 10.0f)
                        .texOffs(37, 0).addBox(7.0f, 19.0f, -5.0f, 10.0f, 4.0f, 10.0f),
                PartPose.offset(0.0f, -7.0f, 0.0f));
        legs(root, 8.0f, 18.0f, 7.0f, 5.0f);
    }

    private static void legs(final PartDefinition root, final float width, final float height,
                             final float depth, final float offset) {
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                        .texOffs(37, 0).addBox(-width / 2.0f, -3.0f, -depth / 2.0f,
                                width, height, depth),
                PartPose.offset(-offset, 11.0f, 0.0f));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(60, 0).mirror()
                        .addBox(-width / 2.0f, -3.0f, -depth / 2.0f, width, height, depth),
                PartPose.offset(offset, 11.0f, 0.0f));
    }
}
