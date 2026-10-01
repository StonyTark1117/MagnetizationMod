package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Opt-in native lifecycle fixture. Control files coordinate isolated JVMs, never gameplay packets. */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class LifecyclePresentationAudit {
    public static final String[] STYLES = {"arrow_tubular", "bm_steel", "intamin_bb", "intamin_bb_double_spine",
            "intamin_box", "intamin_heavy", "intamin_heavy_double_spine", "intamin_no_spine", "intamin_triangular",
            "rmc_ibox", "rmc_ibox_alt", "steel_no_spine", "vekoma_nextgen", "wooden",
            "arrow_tubular_taller", "premier_tri", "premier_tall_spine", "premier_lsm", "premier_steel",
            "wooden_oak", "wooden_birch", "wooden_jungle", "wooden_dark_oak", "wooden_acacia",
            "wooden_mangrove", "wooden_cherry", "wooden_bamboo", "wooden_crimson", "wooden_warped", "wooden_white",
            "invisible", "support_small", "support_medium", "support_large", "support_square_small",
            "support_square_medium", "support_square_large"};
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("magnetization/validation-audit");
    private static int age, step = -1, wait;
    private static String lastView = "";
    private static boolean done;
    private static CompletableFuture<Void> reload;
    public static Path control() { return Path.of(System.getProperty("magnetization.audit.validationDir")); }
    public static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    public static boolean bookPresent() throws Exception {
        Object registry = Class.forName("vazkii.patchouli.common.book.BookRegistry").getField("INSTANCE").get(null);
        return ((java.util.Map<?, ?>) registry.getClass().getField("books").get(registry))
                .containsKey(ResourceLocation.parse("magnetization:field_manual"));
    }
    public static boolean isManual(ItemStack stack) {
        var component = net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE
                .get(ResourceLocation.parse("patchouli:book"));
        return ResourceLocation.parse("magnetization:field_manual").equals(stack.get(component));
    }
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        String phase = System.getProperty("magnetization.audit.validation", "");
        if (phase.isEmpty() || done) return;
        try {
            var server = event.getServer();
            var players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) return;
            var player = players.getFirst();
            if (++age > 24000) throw new IllegalStateException("Audit timed out");
            if (step == -1) {
                player.setGameMode(GameType.CREATIVE);
                boolean gifted = !phase.equals("disabled") && !phase.equals("gift-off");
                int count = player.getInventory().items.stream().filter(LifecyclePresentationAudit::isManual)
                        .mapToInt(ItemStack::getCount).sum();
                boolean flag = player.getPersistentData().getCompound(ServerPlayer.PERSISTED_NBT_TAG)
                        .getBoolean("magnetization:field_manual_given");
                check(count == (gifted ? 1 : 0) && flag == gifted, "Unexpected login gift/flag: " + count + "/" + flag);
                check(bookPresent() == !phase.equals("disabled"), "Unexpected startup book registration");
                recipes(player, !phase.equals("disabled"), false, !phase.equals("disabled"));
                LOG.info("VALIDATION_LOGIN_PASS phase={} uuid={} manuals={} persistedFlag={} pid={}",
                        phase, player.getUUID(), count, flag, ProcessHandle.current().pid());
                if (phase.equals("initial")) Presentation.setup(player);
                Files.writeString(control().resolve("server-ready"), phase);
                step = 0;
            }
            if (phase.equals("initial")) Presentation.view(player);
            if (!Files.exists(control().resolve("client-done"))) return;
            if (!phase.equals("initial")) { finish(server, phase); return; }
            if (step == 0) {
                edit("fieldManualIronRecipeEnabled", true); step = 1; wait = 0;
            }
            if (step == 1 && MagConfig.fieldManualIronRecipeEnabled() && ++wait > 20) startReload(server, 2);
            else if (step == 2 && reload.isDone()) {
                reload.join(); recipes(player, true, true, true);
                edit("fieldManualMagnetiteRecipeEnabled", false); step = 3; wait = 0;
            } else if (step == 3 && !MagConfig.fieldManualMagnetiteRecipeEnabled() && ++wait > 20) startReload(server, 4);
            else if (step == 4 && reload.isDone()) {
                reload.join(); recipes(player, false, true, true);
                edit("fieldManualLodestoneRecipeEnabled", false); step = 5; wait = 0;
            } else if (step == 5 && !MagConfig.fieldManualLodestoneRecipeEnabled() && ++wait > 20) startReload(server, 6);
            else if (step == 6 && reload.isDone()) {
                reload.join(); recipes(player, false, true, false);
                edit("patchouliCompatEnabled", false); step = 7; wait = 0;
            } else if (step == 7 && !MagConfig.patchouliCompatEnabled() && ++wait > 20) startReload(server, 8);
            else if (step == 8 && reload.isDone()) {
                reload.join(); recipes(player, false, false, false);
                check(!bookPresent(), "Config reload did not unregister manual");
                Files.writeString(control().resolve("package-off"), "ready"); step = 9;
            } else if (step == 9 && Files.exists(control().resolve("client-package-off"))) {
                edit("patchouliCompatEnabled", true);
                edit("fieldManualMagnetiteRecipeEnabled", true); edit("fieldManualLodestoneRecipeEnabled", true);
                edit("fieldManualIronRecipeEnabled", false); step = 10; wait = 0;
            } else if (step == 10 && MagConfig.patchouliCompatEnabled() && ++wait > 20) startReload(server, 11);
            else if (step == 11 && reload.isDone()) {
                reload.join(); recipes(player, true, false, true);
                check(!bookPresent(), "Re-enable unexpectedly rediscovered book before restart");
                Files.writeString(control().resolve("package-on"), "restart-required"); step = 12;
            } else if (step == 12 && Files.exists(control().resolve("client-package-on"))) finish(server, phase);
        } catch (Throwable error) {
            done = true; LOG.error("VALIDATION_SERVER_FAILED", error); event.getServer().halt(false);
        }
    }
    private static void finish(net.minecraft.server.MinecraftServer server, String phase) {
        done = true; LOG.info("VALIDATION_SERVER_PASS phase={}", phase); server.halt(false);
    }
    private static void startReload(net.minecraft.server.MinecraftServer server, int next) {
        reload = server.reloadResources(server.getPackRepository().getSelectedIds()); step = next;
    }
    private static void edit(String key, boolean value) throws Exception {
        Path repo = control().getParent().getParent().getParent();
        for (String role : new String[]{"server", "client"}) {
            Path config = repo.resolve("run-validation-audit-" + role + "/config/magnetization-common.toml");
            String text = Files.readString(config);
            check(text.contains(key + " = "), "Missing config key " + key);
            Files.writeString(config, text.replaceAll("(?m)^(\\s*" + key + " = )(true|false)", "$1" + value));
        }
        LOG.info("VALIDATION_CONFIG_EDIT {}={}", key, value);
    }
    private static void recipes(ServerPlayer player, boolean magnetite, boolean iron, boolean lodestone) {
        craft(player, new ItemStack(MagItems.RAW_MAGNETITE.get()), magnetite);
        craft(player, new ItemStack(Items.IRON_INGOT), iron);
        craft(player, new ItemStack(Items.LODESTONE), lodestone);
        LOG.info("VALIDATION_RECIPES_PASS magnetite={} iron={} lodestone={}", magnetite, iron, lodestone);
    }
    private static void craft(ServerPlayer player, ItemStack material, boolean expected) {
        String materialId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(material.getItem()).toString();
        var menu = new net.minecraft.world.inventory.CraftingMenu(0, player.getInventory(),
                net.minecraft.world.inventory.ContainerLevelAccess.create(player.level(), player.blockPosition()));
        menu.getSlot(1).set(new ItemStack(Items.BOOK)); menu.getSlot(2).set(material);
        ItemStack output = menu.getSlot(0).getItem();
        check(isManual(output) == expected, "Native crafting result mismatch for " + material + ": " + output);
        if (expected) {
            ItemStack taken = menu.getSlot(0).remove(1); menu.getSlot(0).onTake(player, taken);
            check(isManual(taken) && taken.getCount() == 1, "Wrong crafted book component/count");
            check(menu.getSlot(1).getItem().isEmpty() && menu.getSlot(2).getItem().isEmpty(), "Inputs not consumed");
            LOG.info("VALIDATION_NATIVE_CRAFT_PASS material={} consumed=book+material output={}", materialId, taken);
        } else check(menu.getSlot(1).getItem().getCount() == 1 && menu.getSlot(2).getItem().getCount() == 1,
                "Disabled recipe consumed inputs");
    }
    // Avoid resolving optional Coasters classes when the fixture is disabled.
    private static final class Presentation {
        private static BlockPos magnet = new BlockPos(6, 100, 64);
        static void setup(ServerPlayer player) {
            var level = player.serverLevel(); level.setDayTime(6000); level.getServer().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
            player.setGameMode(GameType.SURVIVAL);
            for (int i = 0; i < STYLES.length + 2; i++) {
                BlockPos a = new BlockPos(0, 100, i * 4), b = a.east(12);
                level.setBlockAndUpdate(a, dev.silvergold.simulatedcoasters.SimulatedCoastersBlocks.COASTER_ANCHORPOINT.get().defaultBlockState());
                level.setBlockAndUpdate(b, dev.silvergold.simulatedcoasters.SimulatedCoastersBlocks.COASTER_ANCHORPOINT.get().defaultBlockState());
                var anchor = (dev.silvergold.simulatedcoasters.track.anchor.CoasterAnchorpointBlockEntity) level.getBlockEntity(a);
                var peer = (dev.silvergold.simulatedcoasters.track.anchor.CoasterAnchorpointBlockEntity) level.getBlockEntity(b);
                var curve = new com.simibubi.create.content.trains.track.BezierConnection(
                        net.createmod.catnip.data.Couple.create(a, b),
                        net.createmod.catnip.data.Couple.create(net.minecraft.world.phys.Vec3.atCenterOf(a), net.minecraft.world.phys.Vec3.atCenterOf(b)),
                        net.createmod.catnip.data.Couple.create(new net.minecraft.world.phys.Vec3(1, 0, 0), new net.minecraft.world.phys.Vec3(-1, 0, 0)),
                        net.createmod.catnip.data.Couple.create(new net.minecraft.world.phys.Vec3(0, 1, 0), new net.minecraft.world.phys.Vec3(0, 1, 0)),
                        true, false, dev.silvergold.simulatedcoasters.CoasterTrackMaterials.COASTER);
                anchor.putAnchorPeerCurve(level, b, curve);
                if (i < STYLES.length) {
                    for (var be : new net.minecraft.world.level.block.entity.BlockEntity[]{anchor, peer}) {
                        be.getPersistentData().putString("CoasterStyle", STYLES[i]);
                        be.getPersistentData().putInt("CoasterBeamColor", 0x4422AA);
                        be.getPersistentData().putInt("CoasterRailColor", 0xDDDDAA);
                    }
                } else {
                    var mode = i == STYLES.length ? net.antopfr.coastersmagnetized.magnet.AnchorMode.MAGNET : net.antopfr.coastersmagnetized.magnet.AnchorMode.BRAKE;
                    net.antopfr.coastersmagnetized.magnet.MagnetizedAnchors.setMode(level, a, mode);
                    net.antopfr.coastersmagnetized.magnet.MagnetizedAnchors.setMode(level, b, mode);
                }
                anchor.notifyUpdate(); peer.notifyUpdate();
            }
            LOG.info("VALIDATION_PRESENTATION_READY styles={} modes=magnet,brake", STYLES.length);
        }
        static void view(ServerPlayer player) throws Exception {
            Path request = control().resolve("view-request"); if (!Files.exists(request)) return;
            String view = Files.readString(request); if (view.equals(lastView)) return; lastView = view;
            String[] parts = view.split(":"); int row = Integer.parseInt(parts[0]); boolean field = Boolean.parseBoolean(parts[1]);
            var level = player.serverLevel();
            level.removeBlock(magnet, false); com.stonytark.magnetization.physics.EmitterRegistry.unregister(level, magnet);
            level.removeBlock(magnet.east(10), false); com.stonytark.magnetization.physics.EmitterRegistry.unregister(level, magnet.east(10));
            magnet = new BlockPos(1, 100, row * 4 + 1);
            if (field) for (BlockPos pos : new BlockPos[]{magnet, magnet.east(10)}) {
                level.setBlockAndUpdate(pos, com.stonytark.magnetization.registry.MagBlocks.PERMANENT_MAGNET.get().defaultBlockState());
                com.stonytark.magnetization.physics.EmitterRegistry.register(level, pos);
            }
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(level, 6, 106, row * 4 + 3, java.util.Set.of(), 180, 65);
            Files.writeString(control().resolve("view-ready"), view);
            LOG.info("VALIDATION_VIEW row={} field={}", row, field);
        }
    }
}
