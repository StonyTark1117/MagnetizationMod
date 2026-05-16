package com.stonytark.magnetization.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.item.MagneticGrappleItem;
import com.stonytark.magnetization.content.item.RepulsorGunItem;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.lwjgl.glfw.GLFW;

/**
 * Per-item keybinds for active tools that live in Curios slots — Magnetic
 * Grapple and Repulsor Gun. Both appear in the standard
 * <em>Options → Controls → Key Binds</em> menu under a "Magnetization"
 * category, default unbound so they don't collide with vanilla on first
 * install. Players assign their preferred key and Minecraft handles conflict
 * detection / rebinding automatically.
 *
 * <p>On key press: scan the player's curios slots for the matching item; if
 * found, send a small server-side payload that invokes the item's standard
 * {@code use()} handler. The actual game effect runs server-authoritative,
 * same code path as right-clicking from hand.
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

    // ----------------- network payload + server handler -----------------

    public record UseCurioPayload(Kind kind) implements CustomPacketPayload {
        public enum Kind { GRAPPLE, REPULSOR_GUN }
        public static final Type<UseCurioPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "use_curio"));
        public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, UseCurioPayload>
                CODEC = net.minecraft.network.codec.StreamCodec.of(
                        (buf, p) -> buf.writeByte(p.kind.ordinal()),
                        buf -> new UseCurioPayload(Kind.values()[buf.readByte()]));

        @Override public Type<UseCurioPayload> type() { return TYPE; }
    }

    /** Server-side handler — invoked when the client sends a key-press packet.
     *  Looks up the requested item in the player's curios slots and runs its
     *  use() handler. */
    public static void handleUseCurio(final UseCurioPayload payload, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp)) return;
            final ItemStack stack = findInCurios(sp, payload.kind());
            if (stack == null || stack.isEmpty()) return;
            switch (payload.kind()) {
                case GRAPPLE -> {
                    if (stack.getItem() instanceof MagneticGrappleItem grapple) {
                        grapple.use(sp.level(), sp, InteractionHand.MAIN_HAND);
                    }
                }
                case REPULSOR_GUN -> {
                    if (stack.getItem() instanceof RepulsorGunItem gun) {
                        gun.fire(sp.level(), sp);
                        // Same cooldown the in-hand path applies, so curio fires
                        // don't bypass the rate limit.
                        sp.getCooldowns().addCooldown(gun, cooldownTicksFromConfig());
                    }
                }
            }
        });
    }

    /** Hook into NeoForge's payload registration. Called from {@code Magnetization}
     *  main class on RegisterPayloadHandlersEvent. */
    public static void registerPayloads(final PayloadRegistrar registrar) {
        registrar.playToServer(
                UseCurioPayload.TYPE,
                UseCurioPayload.CODEC,
                MagKeyBindings::handleUseCurio);
    }

    private static ItemStack findInCurios(final LivingEntity entity, final UseCurioPayload.Kind kind) {
        if (!ModList.get().isLoaded("curios")) return null;
        try {
            final var handler = entity.getCapability(top.theillusivec4.curios.api.CuriosCapability.INVENTORY);
            if (handler == null) return null;
            for (final var entry : handler.getCurios().entrySet()) {
                final var stacks = entry.getValue().getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    final ItemStack s = stacks.getStackInSlot(i);
                    final boolean match = switch (kind) {
                        case GRAPPLE -> s.is(MagItems.MAGNETIC_GRAPPLE.get());
                        case REPULSOR_GUN -> s.is(MagItems.REPULSOR_GUN.get());
                    };
                    if (match) return s;
                }
            }
        } catch (final Throwable t) {
            // Curios API drift — fail soft.
        }
        return null;
    }

    private static int cooldownTicksFromConfig() {
        try { return com.stonytark.magnetization.config.MagConfig.REPULSOR_GUN_COOLDOWN_TICKS.get(); }
        catch (final Throwable t) { return 20; }
    }

    /** Server-side: unused parameter, suppression keeps SuppressWarnings clean. */
    @SuppressWarnings("unused")
    private static void noop(final GLFW unused) {}
}
