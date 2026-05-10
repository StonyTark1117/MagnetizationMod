package com.stonytark.magnetization.compat.ponder;

import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Scene authoring stub. Real Ponder scenes are scripted demos with placed
 * blocks, camera moves, and tooltip overlays — they live in this class as
 * methods that take a {@code SceneBuilder} and step through the demonstration.
 *
 * <p>Currently empty: scripting a meaningful scene requires a {@code .nbt}
 * structure file (the starting block layout) which I can't author without an
 * in-game schematic export. Future contributors can add scenes here following
 * the pattern in Create's {@code AllPonderScenes} and the reference
 * {@code create_propulsion_simulated/CPSPonderScenes}.
 */
public final class MagPonderScenes {

    private MagPonderScenes() {}

    public static void register(final PonderSceneRegistrationHelper<ResourceLocation> helper) {
        // Stub — see class javadoc.
    }
}
