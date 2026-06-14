package com.stonytark.magnetization.compat.wthit;

import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IWailaClientPlugin;
import net.minecraft.world.level.block.Block;

/**
 * WTHIT client plugin entry — registers our body provider against
 * {@link Block} so every block flows through; the provider self-filters on
 * {@link com.stonytark.magnetization.api.MagneticFieldSource}. We only render
 * the body tooltip, so no {@code head} / {@code icon} / {@code dataContext}
 * registration is needed.
 *
 * <p>Registered via {@code resources/waila_plugins.json} so the class is only
 * loaded when WTHIT is installed; if it isn't, neither this class nor
 * {@link EmitterBodyProvider} are referenced and their imports never resolve.
 *
 * <p>This replaces the previously-used {@code MagWthitPlugin} (which
 * implemented the deprecated {@code IWailaPlugin}). The modern API splits
 * common + client entrypoints; tooltip rendering is client-side only, so
 * only the client entrypoint is registered.
 */
public class MagWthitClientPlugin implements IWailaClientPlugin {

    @Override
    public void register(final IClientRegistrar registrar) {
        registrar.body(EmitterBodyProvider.INSTANCE, Block.class);
        registrar.body(MachineBodyProvider.INSTANCE, Block.class);
        registrar.body(SaplingBodyProvider.INSTANCE, Block.class);
        registrar.body(CatalystBodyProvider.INSTANCE, Block.class);
    }
}
