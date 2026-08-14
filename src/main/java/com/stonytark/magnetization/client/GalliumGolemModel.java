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
 * A cast gallium body which has sagged around its frame. It preserves the five
 * animated Iron Golem part names but deliberately avoids the rigid vanilla
 * silhouette: the shoulders slump, the limbs bulge unevenly, and cooled drips
 * hang from the torso and hands.
 */
public final class GalliumGolemModel extends IronGolemModel<IronGolem> {
    public GalliumGolemModel() {
        super(createBodyLayer().bakeRoot());
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-5.0f, -10.0f, -5.5f, 10.0f, 8.0f, 9.0f,
                                new CubeDeformation(0.25f))
                        .texOffs(24, 0).addBox(-1.5f, -5.5f, -7.5f, 3.0f, 3.0f, 2.0f)
                        .texOffs(37, 0).addBox(-5.5f, -3.0f, -3.5f, 3.0f, 4.0f, 3.0f)
                        .texOffs(60, 0).addBox(3.0f, -3.0f, -2.5f, 2.0f, 5.0f, 3.0f),
                PartPose.offset(0.0f, -7.0f, -2.0f));
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 40).addBox(-10.5f, -2.0f, -6.0f, 21.0f, 12.0f, 11.0f,
                                new CubeDeformation(0.35f))
                        .texOffs(0, 70).addBox(-6.0f, 9.0f, -4.0f, 12.0f, 7.0f, 8.0f,
                                new CubeDeformation(0.65f))
                        .texOffs(37, 0).addBox(-8.0f, 15.0f, -3.5f, 4.0f, 5.0f, 4.0f)
                        .texOffs(60, 0).addBox(3.0f, 15.0f, -3.0f, 3.0f, 7.0f, 4.0f)
                        .texOffs(24, 0).addBox(-2.0f, 14.0f, -5.0f, 3.0f, 4.0f, 3.0f),
                PartPose.offset(0.0f, -7.0f, 0.0f));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                        .texOffs(60, 21).addBox(-15.5f, -1.0f, -4.0f, 7.5f, 27.0f, 8.0f,
                                new CubeDeformation(0.3f))
                        .texOffs(37, 0).addBox(-16.0f, 20.0f, -4.5f, 9.0f, 7.0f, 9.0f)
                        .texOffs(24, 0).addBox(-13.5f, 26.0f, -2.0f, 3.0f, 5.0f, 3.0f),
                PartPose.offset(0.0f, -7.0f, 0.0f));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                        .texOffs(60, 58).addBox(8.0f, -2.0f, -3.5f, 7.0f, 29.0f, 7.0f,
                                new CubeDeformation(0.45f))
                        .texOffs(0, 70).addBox(7.0f, 18.0f, -4.0f, 9.0f, 8.0f, 8.0f)
                        .texOffs(60, 0).addBox(11.0f, 25.0f, -1.5f, 3.0f, 6.0f, 3.0f),
                PartPose.offset(0.0f, -7.0f, 0.0f));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                        .texOffs(37, 0).addBox(-4.0f, -2.0f, -3.5f, 8.0f, 16.0f, 7.0f,
                                new CubeDeformation(0.2f)),
                PartPose.offset(-4.5f, 11.0f, 0.0f));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(60, 0).mirror()
                        .addBox(-3.5f, -2.0f, -3.5f, 7.0f, 15.0f, 7.0f,
                                new CubeDeformation(0.4f)),
                PartPose.offset(4.5f, 11.0f, 0.0f));
        return LayerDefinition.create(mesh, 128, 128);
    }
}
