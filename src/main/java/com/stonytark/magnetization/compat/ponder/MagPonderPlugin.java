package com.stonytark.magnetization.compat.ponder;

import com.stonytark.magnetization.Magnetization;
import com.simibubi.create.foundation.ponder.CreatePonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Ponder integration entry point. Extends Create's plugin base so we get the
 * default style configuration. Scene authoring lives in {@link MagPonderScenes}.
 *
 * <p>Discovery: registered as a {@code java.util.ServiceLoader} service via
 * {@code META-INF/services/net.createmod.ponder.api.registration.PonderPlugin}.
 * If Ponder isn't installed, this class is never loaded so the missing
 * imports don't surface.
 */
public class MagPonderPlugin extends CreatePonderPlugin {

    @Override
    public String getModId() {
        return Magnetization.MOD_ID;
    }

    @Override
    public void registerScenes(final PonderSceneRegistrationHelper<ResourceLocation> helper) {
        MagPonderScenes.register(helper);
    }

    @Override
    public void registerTags(final PonderTagRegistrationHelper<ResourceLocation> helper) {}

    @Override
    public void registerSharedText(final SharedTextRegistrationHelper helper) {}
}
