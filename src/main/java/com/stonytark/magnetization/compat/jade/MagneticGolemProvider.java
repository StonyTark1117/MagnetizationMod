package com.stonytark.magnetization.compat.jade;

import com.stonytark.magnetization.content.golem.MagneticGolem;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Jade mirror of the shared live iron-oxide golem HUD. */
public enum MagneticGolemProvider implements IEntityComponentProvider {
    INSTANCE;

    @Override
    public ResourceLocation getUid() {
        return MagJadePlugin.GOLEM_INFO;
    }

    @Override
    public void appendTooltip(final ITooltip tooltip, final EntityAccessor accessor,
                              final IPluginConfig config) {
        if (!(accessor.getEntity() instanceof MagneticGolem golem)) return;
        for (final var line : com.stonytark.magnetization.content.golem.MagneticGolemHud.lines(golem)) {
            tooltip.add(line);
        }
    }
}
