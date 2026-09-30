package com.stonytark.magnetization.compat.wthit;

import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IWailaCommonPlugin;
import net.minecraft.world.entity.LivingEntity;

/** Loaded only by WTHIT, on either side; supplies authoritative remote-entity effect data. */
public final class MagWthitCommonPlugin implements IWailaCommonPlugin {
    @Override public void register(ICommonRegistrar registrar) {
        registrar.entityData(SlugterraStatusDataProvider.INSTANCE, LivingEntity.class);
    }
}
