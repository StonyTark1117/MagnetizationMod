package com.stonytark.magnetization.compat.jade;

import com.stonytark.magnetization.content.golem.GalliumGolem;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Gallium thermal and repair state for Jade. */
public enum GalliumGolemProvider implements IEntityComponentProvider {
    INSTANCE;

    @Override
    public ResourceLocation getUid() {
        return MagJadePlugin.GALLIUM_GOLEM_INFO;
    }

    @Override
    public void appendTooltip(final ITooltip tooltip, final EntityAccessor accessor,
                              final IPluginConfig config) {
        if (!(accessor.getEntity() instanceof GalliumGolem golem)) return;
        com.stonytark.magnetization.content.golem.GalliumGolemHud.lines(golem)
                .forEach(tooltip::add);
    }
}
