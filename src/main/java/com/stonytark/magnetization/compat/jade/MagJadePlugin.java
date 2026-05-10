package com.stonytark.magnetization.compat.jade;

import com.stonytark.magnetization.Magnetization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade integration entry point. Discovered via {@link WailaPlugin} annotation
 * scanning when Jade is installed; if it isn't, this class is never loaded so
 * the missing Jade types don't surface at runtime.
 */
@WailaPlugin
public class MagJadePlugin implements IWailaPlugin {

    public static final ResourceLocation FIELD_INFO = Magnetization.id("field_info");

    @Override
    public void register(final IWailaCommonRegistration registration) {
        // No server-side data needed: cachedField updates are already mirrored to
        // clients via setBlock UPDATE_CLIENTS when the BE recomputes its field.
    }

    @Override
    public void registerClient(final IWailaClientRegistration registration) {
        // Register against Block.class so every block runs through our provider;
        // EmitterFieldProvider self-filters by checking for MagneticFieldSource on
        // the BE, so non-emitter blocks see a no-op.
        registration.registerBlockComponent(EmitterFieldProvider.INSTANCE, Block.class);
    }
}
