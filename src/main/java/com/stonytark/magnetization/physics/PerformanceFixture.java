package com.stonytark.magnetization.physics;

import com.mojang.authlib.GameProfile;
import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.mrarmor.MrArmorHandler;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Opt-in local harness only. Synthetic players exercise the real MR handler,
 * without pretending to measure network or connected-player overhead. */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class PerformanceFixture {
    private static final boolean ENABLED = Boolean.getBoolean("magnetization.performanceFixture");
    private static final Map<ServerLevel, FakePlayer> PLAYERS = new WeakHashMap<>();
    private static final GameProfile PROFILE = new GameProfile(
            UUID.fromString("4cac6432-183b-4708-a5b7-9db09380e33c"), "MagPerfFixture");
    private PerformanceFixture() {}

    @SubscribeEvent
    public static void register(final RegisterCommandsEvent event) {
        if (!ENABLED) return;
        event.getDispatcher().register(Commands.literal("magperf").requires(src -> src.hasPermission(2))
                .then(Commands.literal("reset").executes(ctx -> {
                    PerformanceDiagnostics.resetWork(ctx.getSource().getLevel());
                    return 1;
                }))
                .then(Commands.literal("counts").executes(ctx -> {
                    final var counts = PerformanceDiagnostics.workSnapshot(ctx.getSource().getLevel());
                    ctx.getSource().sendSuccess(() -> Component.literal("MAG_PERF_COUNTS " +
                            new com.google.gson.Gson().toJson(counts)), false);
                    return 1;
                }))
                .then(Commands.literal("reindex").then(Commands.argument("radius",
                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 256)).executes(ctx -> {
                    final ServerLevel level = ctx.getSource().getLevel();
                    final int radius = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius");
                    for (int x = Math.floorDiv(-radius, 16); x <= Math.floorDiv(radius, 16); x++) {
                        for (int z = Math.floorDiv(-radius, 16); z <= Math.floorDiv(radius, 16); z++) {
                            final var chunk = level.getChunkSource().getChunkNow(x, z);
                            if (chunk != null) com.stonytark.magnetization.compat.ExternalEmitterTracker.rebuildChunkIndex(level, chunk);
                        }
                    }
                    return 1;
                })))
                .then(Commands.literal("verifyexternal").executes(ctx -> {
                    final int count = EmitterRegistry.externalSize(ctx.getSource().getLevel());
                    ctx.getSource().sendSuccess(() -> Component.literal(count > 0
                            ? "MAG_PERF_EXTERNAL_READY " + count : "MAG_PERF_SETUP_FAIL external index empty"), false);
                    return count;
                }))
                .then(Commands.literal("clear").executes(ctx -> {
                    PLAYERS.remove(ctx.getSource().getLevel());
                    return 1;
                }))
                .then(Commands.literal("player").then(Commands.argument("equipment",
                        com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
                    final ServerLevel level = ctx.getSource().getLevel();
                    final FakePlayer player = FakePlayerFactory.get(level, PROFILE);
                    for (final EquipmentSlot slot : EquipmentSlot.values()) player.setItemSlot(slot, ItemStack.EMPTY);
                    player.setPos(0.5d, 82.0d, 0.5d);
                    player.removeTag("mag_perf_cycle");
                    final String equipment = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "equipment");
                    switch (equipment) {
                        case "none" -> { }
                        case "cycle" -> player.addTag("mag_perf_cycle");
                        case "armor" -> equip(player, EquipmentSlot.CHEST, "mr_liquid_chestplate");
                        case "main" -> equip(player, EquipmentSlot.MAINHAND, "mr_fluid_pickaxe");
                        case "off" -> equip(player, EquipmentSlot.OFFHAND, "mr_fluid_pickaxe");
                        default -> throw new IllegalArgumentException("Unknown fixture equipment: " + equipment);
                    }
                    PLAYERS.put(level, player);
                    return 1;
                }))));
    }

    @SubscribeEvent
    public static void unload(final net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        PLAYERS.remove(event.getLevel());
    }

    private static void equip(final FakePlayer player, final EquipmentSlot slot, final String path) {
        final var id = ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, path);
        if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("Missing fixture item " + id);
        player.setItemSlot(slot, new ItemStack(BuiltInRegistries.ITEM.get(id)));
    }

    @SubscribeEvent
    public static void tick(final LevelTickEvent.Post event) {
        if (!ENABLED) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        final FakePlayer player = PLAYERS.get(level);
        if (player != null) {
            if (player.getTags().contains("mag_perf_cycle") && level.getGameTime() % 20 == 0) {
                for (final EquipmentSlot slot : EquipmentSlot.values()) player.setItemSlot(slot, ItemStack.EMPTY);
                switch ((int) ((level.getGameTime() / 20) % 4)) {
                    case 1 -> equip(player, EquipmentSlot.CHEST, "mr_liquid_chestplate");
                    case 2 -> equip(player, EquipmentSlot.MAINHAND, "mr_fluid_pickaxe");
                    case 3 -> equip(player, EquipmentSlot.OFFHAND, "mr_fluid_pickaxe");
                    default -> { }
                }
            }
            MrArmorHandler.onPlayerTick(new PlayerTickEvent.Post(player));
        }
    }
}
