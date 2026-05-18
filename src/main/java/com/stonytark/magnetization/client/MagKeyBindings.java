package com.stonytark.magnetization.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.network.UseCurioPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Per-item keybinds for active tools that live in Curios slots — Magnetic
 * Grapple and Repulsor Gun. Both appear in the standard
 * <em>Options → Controls → Key Binds</em> menu under a "Magnetization"
 * category, default unbound so they don't collide with vanilla on first
 * install. Players assign their preferred key and Minecraft handles conflict
 * detection / rebinding automatically.
 *
 * <p>On key press: send {@link UseCurioPayload} to the server, which scans the
 * player's curios slots for the matching item and runs its {@code use()}
 * handler. The server-side logic lives in {@link UseCurioPayload} so the
 * common payload-registration path doesn't class-load this client-only class
 * (which would crash a dedicated server on {@code KeyMapping}).
 *
 * <p>If Curios isn't installed, the keybinds still register so the menu shows
 * them — but pressing them is a no-op (since no curios slot exists to scan).
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID, value = Dist.CLIENT)
public final class MagKeyBindings {

    public static final String CATEGORY = "key.categories.magnetization";

    /** Default keys deliberately UNBOUND so we never collide with vanilla on
     *  first launch. Players bind via Options → Controls; remembered across
     *  sessions by the vanilla controls config. */
    public static final KeyMapping USE_GRAPPLE = new KeyMapping(
            "key.magnetization.use_grapple",
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(),
            CATEGORY);
    public static final KeyMapping USE_REPULSOR_GUN = new KeyMapping(
            "key.magnetization.use_repulsor_gun",
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(),
            CATEGORY);

    private MagKeyBindings() {}

    @SubscribeEvent
    public static void onRegisterKeyMappings(final RegisterKeyMappingsEvent event) {
        event.register(USE_GRAPPLE);
        event.register(USE_REPULSOR_GUN);
    }

    /** Drain key presses each client tick; consumeClick returns true once per
     *  press so we don't double-fire while the key is held. */
    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event) {
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        if (player == null) return;
        while (USE_GRAPPLE.consumeClick()) {
            PacketDistributor.sendToServer(new UseCurioPayload(UseCurioPayload.Kind.GRAPPLE));
        }
        while (USE_REPULSOR_GUN.consumeClick()) {
            PacketDistributor.sendToServer(new UseCurioPayload(UseCurioPayload.Kind.REPULSOR_GUN));
        }
    }
}
