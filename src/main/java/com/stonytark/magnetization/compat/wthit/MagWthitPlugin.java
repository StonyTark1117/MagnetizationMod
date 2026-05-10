package com.stonytark.magnetization.compat.wthit;

import mcp.mobius.waila.api.IRegistrar;
import mcp.mobius.waila.api.IWailaPlugin;
import net.minecraft.world.level.block.Block;

/**
 * WTHIT integration entry point. Registered via
 * {@code resources/waila_plugins.json} so the class is only loaded when WTHIT
 * is installed; if it isn't, neither this class nor {@link EmitterBodyProvider}
 * are referenced and their imports never resolve.
 */
public class MagWthitPlugin implements IWailaPlugin {

    @Override
    public void register(final IRegistrar registrar) {
        // Register the body provider against Block.class so every block flows
        // through; the provider self-filters on MagneticFieldSource.
        registrar.body(EmitterBodyProvider.INSTANCE, Block.class);
    }
}
