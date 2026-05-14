package com.stonytark.magnetization.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only hook that opts our mod into NeoForge's built-in config-screen
 * factory. Without this registration the "Config" button on the Mods list is
 * greyed out — NeoForge ships {@link ConfigurationScreen}, an auto-generated
 * UI for any {@code ModConfigSpec}, but it only kicks in when the mod
 * registers it as an extension point.
 *
 * <p>Called from {@code Magnetization} on the client side only — the
 * {@code Dist.CLIENT} guard there ensures this class never loads on a
 * dedicated server, which is important because both
 * {@link ConfigurationScreen} and {@link IConfigScreenFactory} live in
 * {@code net.neoforged.neoforge.client.*} and don't exist server-side.
 */
public final class MagClientConfig {

    private MagClientConfig() {}

    public static void registerConfigScreen(final ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new ConfigurationScreen(mod, parent));
    }
}
