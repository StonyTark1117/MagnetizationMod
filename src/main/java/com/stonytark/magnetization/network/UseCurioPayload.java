package com.stonytark.magnetization.network;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.item.MagneticGrappleItem;
import com.stonytark.magnetization.content.item.RepulsorGunItem;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Server-safe payload + handler for the use-curio keybind. Sent from client
 * key-press logic in {@code MagKeyBindings} (Dist.CLIENT-only) and handled here.
 *
 * <p>This class lives outside the {@code .client} package on purpose: the common
 * mod-init path calls {@link #register} during
 * {@code RegisterPayloadHandlersEvent}, which fires on both client and dedicated
 * server. Routing the registration through a client-only class would trigger
 * class-loading of {@code KeyMapping} on the server and crash.
 */
public record UseCurioPayload(Kind kind) implements CustomPacketPayload {

    public enum Kind { GRAPPLE, REPULSOR_GUN }

    public static final Type<UseCurioPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "use_curio"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UseCurioPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeByte(p.kind.ordinal()),
                    buf -> new UseCurioPayload(Kind.values()[buf.readByte()]));

    @Override
    public Type<UseCurioPayload> type() { return TYPE; }

    /** Wire to NeoForge's payload registry. Called from {@code Magnetization}'s
     *  {@code onRegisterPayloads}. Common-only — runs on both physical sides. */
    public static void register(final PayloadRegistrar registrar) {
        registrar.playToServer(TYPE, CODEC, UseCurioPayload::handle);
    }

    /** Server handler: look up the requested item in the player's Curios slots
     *  and invoke its standard {@code use()} path. Same effect as right-clicking
     *  from hand, but routed through the configurable keybind. */
    private static void handle(final UseCurioPayload payload, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
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

    private static ItemStack findInCurios(final LivingEntity entity, final Kind kind) {
        if (!ModList.get().isLoaded("curios")) return null;
        try {
            final var handler = entity.getCapability(top.theillusivec4.curios.api.CuriosCapability.INVENTORY);
            if (handler == null) return null;
            final var curios = handler.getCurios();
            if (curios == null) return null;
            for (final var entry : curios.entrySet()) {
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
}
