package com.stonytark.magnetization.client;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.golem.MagnetiteGolem;
import com.stonytark.magnetization.content.golem.PyrrhotiteGolem;
import com.stonytark.magnetization.content.golem.TitanomagnetiteGolem;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;

/** Distinct mineral silhouettes with material/state-specific textures. */
public final class IronOxideGolemRenderer extends IronGolemRenderer {
    public IronOxideGolemRenderer(final EntityRendererProvider.Context context,
                                  final IronOxideGolemModel.Profile profile) {
        super(context);
        this.model = new IronOxideGolemModel(profile);
    }

    private static ResourceLocation texture(final String name) {
        return ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "textures/entity/" + name + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(final IronGolem entity) {
        if (entity instanceof MagnetiteGolem golem) return texture(golem.isOxidized() ? "maghemite_golem" : "magnetite_golem");
        if (entity instanceof PyrrhotiteGolem golem) return texture(golem.mobileField() == null ? "pyrrhotite_golem" : "pyrrhotite_golem_active");
        if (entity instanceof TitanomagnetiteGolem golem) return texture(golem.isCharged() ? "titanomagnetite_golem_charged" : "titanomagnetite_golem");
        return texture("hematite_golem");
    }
}
