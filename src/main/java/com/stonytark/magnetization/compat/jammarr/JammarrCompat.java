package com.stonytark.magnetization.compat.jammarr;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.stonytark.magnetization.Magnetization;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Optional easter egg. Jammarr is never resolved until its exact supported version is loaded. */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class JammarrCompat {
    private static Binding binding;
    private JammarrCompat() {}

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        String version = ModList.get().getModContainerById("jammarr")
                .map(mod -> mod.getModInfo().getVersion().toString()).orElse(null);
        register(root, version);
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root, String version) {
        if ("1.1.0".equals(version)) root.then(Commands.literal("how").executes(ctx -> execute(ctx.getSource())));
    }

    private static int execute(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Run this as a player with Jammarr installed.")); return 0;
        }
        try {
            if (binding == null) binding = new Binding(source.getServer());
            Binding active = binding;
            if (!active.authorized(player)) {
                source.sendFailure(Component.literal("This requires a compatible Jammarr client and Jammarr's operator permission.")); return 0;
            }
            var result = active.playback.request(() -> binding == active && active.authorized(player));
            result.thenAcceptAsync(outcome -> {
                if (binding != active || source.getServer().isStopped() || player.hasDisconnected()) return;
                String message = switch (outcome) {
                    case STARTED -> "Miracles — Insane Clown Posse. Starting through Jammarr…";
                    case BUSY -> "Already looking for a miracle. Please wait.";
                    case COOLDOWN -> "Give the magnets a moment. Try again shortly.";
                    case NOT_FOUND -> "Miracles by Insane Clown Posse isn't in the available music library.";
                    case UNAVAILABLE -> "Jammarr's music library is currently unavailable.";
                    case CANCELLED -> "Playback changed while the song was loading; request cancelled.";
                    case FAILED -> "Couldn't start Miracles through Jammarr.";
                };
                if (outcome == JammarrPlayback.Result.STARTED) source.sendSuccess(() -> Component.literal(message), false);
                else source.sendFailure(Component.literal(message));
            }, source.getServer());
            if (!result.isDone()) source.sendSuccess(() -> Component.literal("Looking for a miracle…"), false);
            return result.isDone() && result.getNow(JammarrPlayback.Result.FAILED) != JammarrPlayback.Result.STARTED ? 0 : 1;
        } catch (ReflectiveOperationException | RuntimeException error) {
            // Do not expose Plex credentials or HTTP response details from reflective causes.
            source.sendFailure(Component.literal("The installed Jammarr playback API is unavailable."));
            return 0;
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        Binding previous = binding; binding = null;
        if (previous != null) previous.playback.close();
    }

    private static final class Binding {
        private final MinecraftServer server;
        private final JammarrAccess api, settings;
        private final Object instance;
        private final JammarrPlayback playback;

        private Binding(MinecraftServer server) throws ReflectiveOperationException {
            this.server = server;
            ClassLoader loader = JammarrCompat.class.getClassLoader();
            api = new JammarrAccess(loader, "server.JammarrServer")
                    .method("instance").method("player").method("accepted", ServerPlayer.class);
            settings = new JammarrAccess(loader, "core.platform.JammarrSettings").method("operatorPermissionLevel");
            JammarrAccess global = new JammarrAccess(loader, "server.GlobalPlayer").field("delegate");
            instance = api.call(null, "instance");
            Object player = api.call(instance, "player");
            playback = new JammarrPlayback(global.get(player, "delegate"));
        }

        private boolean authorized(ServerPlayer player) {
            return !server.isStopped() && server.getPlayerList().getPlayer(player.getUUID()) == player
                    && !player.hasDisconnected() && (boolean) api.call(instance, "accepted", player)
                    && player.hasPermissions((int) settings.call(null, "operatorPermissionLevel"));
        }
    }
}
