package com.stonytark.magnetization.compat.jade;

import com.stonytark.magnetization.content.golem.MrFluidGolem;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Jade mirror of the live MR Fluid Golem state line. */
public enum MrFluidGolemProvider implements IEntityComponentProvider {
    INSTANCE;

    @Override
    public ResourceLocation getUid() {
        return MagJadePlugin.MR_FLUID_GOLEM_INFO;
    }

    @Override
    public void appendTooltip(final ITooltip tooltip, final EntityAccessor accessor,
                              final IPluginConfig config) {
        if (!(accessor.getEntity() instanceof MrFluidGolem golem)) return;
        for (final var line : com.stonytark.magnetization.content.golem.MrFluidGolemHud.lines(golem)) {
            tooltip.add(line);
        }
    }
}
