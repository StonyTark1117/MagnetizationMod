package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.meteorite.MeteoriteCoreBlockEntity;
import com.stonytark.magnetization.content.meteorite.MeteoriteFieldRegistry;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/** Opt-in real-network audit. Two fresh JVMs share the ordinary saved world. */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class AeMeteoriteRuntimeAudit {
    public static final BlockPos AE = new BlockPos(-40, 100, 0);
    public static final BlockPos DEAD = new BlockPos(5, 100, 0);
    public static final BlockPos NATIVE_FAR = new BlockPos(0, 100, 80);
    public static final BlockPos NATIVE_NEAR = new BlockPos(0, 100, 12);
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("magnetization/ae-meteorite-audit");
    private static ItemEntity aeProbe, nativeProbe;
    private static int stage = -1, stageTicks, totalTicks, forceSamples;
    private static double aeMax, nativeMax;
    private static boolean done;
    private static Properties manifest;

    private AeMeteoriteRuntimeAudit() {}
    public static Path control() { return Path.of(System.getProperty("magnetization.audit.aeMeteoriteDir")); }
    public static void check(final boolean condition, final String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private static boolean enabled() { return !System.getProperty("magnetization.audit.aeMeteorite", "").isEmpty(); }

    @SubscribeEvent
    public static void beforeTick(final ServerTickEvent.Pre event) {
        if (!enabled() || done || aeProbe == null) return;
        reset(aeProbe, AE); reset(nativeProbe, NATIVE_FAR);
    }

    private static void reset(final ItemEntity item, final BlockPos source) {
        item.setPos(source.getX() + 3.5, source.getY() + 0.5, source.getZ() + 0.5);
        item.setDeltaMovement(Vec3.ZERO);
    }

    @SubscribeEvent
    public static void afterTick(final ServerTickEvent.Post event) {
        if (!enabled() || done) return;
        final String phase = System.getProperty("magnetization.audit.aeMeteorite");
        final var server = event.getServer(); final var level = server.overworld();
        try {
            if (++totalTicks > 6000) throw new IllegalStateException("Runtime audit timed out");
            if (server.getPlayerList().getPlayers().isEmpty()) return;
            final ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            if (stage == -1) {
                check(ModList.get().isLoaded("ae2"), "AE2 is missing from audit runtime");
                setup(level, player, phase);
                stage = 0; beginStage(level, phase); return;
            }
            stageTicks++;
            // Sample actual velocities after normal level/entity/field ticks.
            // Probes are reset in ServerTick.Pre: prior inertia and gravity cannot
            // imitate a magnetic impulse or hide the disabled baseline.
            if (stageTicks > 5) {
                aeMax = Math.max(aeMax, aeProbe.getDeltaMovement().length());
                nativeMax = Math.max(nativeMax, nativeProbe.getDeltaMovement().length());
                if (level.getGameTime() % MagConfig.meteoriteFieldTicks() == 0L) forceSamples++;
            }
            if (stageTicks < 100 || !Files.exists(control().resolve("client-" + phase + "-" + stage))) return;
            final boolean hook = MagConfig.AE2_METEORITE_HOOK_ENABLED.get();
            check(forceSamples >= 10, "Not enough normal field tick samples");
            check(hook ? aeMax > 1e-6 : aeMax < 1e-12,
                    "AE2 field force mismatch: enabled=" + hook + " max=" + aeMax);
            final boolean nativeActive = level.getBlockState(NATIVE_FAR).is(MagBlocks.METEORITE_CORE.get());
            check(!nativeActive || nativeMax > 1e-6, "Native field control stopped emitting");
            LOG.info("AE_AUDIT_FORCE_PASS phase={} stage={} enabled={} samples={} aeDelta={} nativeDelta={}",
                    phase, stage, hook, forceSamples, aeMax, nativeMax);
            final int lastStage = phase.equals("create") ? 4 : 3;
            if (stage < lastStage) { stage++; beginStage(level, phase); return; }
            check(MeteoriteFieldRegistry.snapshot(level).size() == 2, "Saved virtual source count changed");
            manifest.setProperty("savedGameTime", Long.toString(level.getGameTime()));
            if (phase.equals("create")) {
                manifest.setProperty("nativeChargedAt", Long.toString(nativeCharge(level)));
                try (var out = Files.newOutputStream(control().resolve("manifest.properties"))) {
                    manifest.store(out, "AE2 compass runtime audit persistence expectations");
                }
            }
            LOG.info("AE_AUDIT_SERVER_PASS phase={} pid={} time={} activeChargedAt={}",
                    phase, ProcessHandle.current().pid(), level.getGameTime(), entry(level, AE).chargedAtTick());
            aeProbe.discard(); nativeProbe.discard();
            Files.writeString(control().resolve("server-done-" + phase), "pass");
            done = true; server.halt(false);
        } catch (Throwable failure) {
            done = true; LOG.error("AE_AUDIT_SERVER_FAILED phase=" + phase, failure);
            server.halt(false);
        }
    }

    private static void setup(final ServerLevel level, final ServerPlayer player, final String phase) throws Exception {
        MagConfig.AE2_METEORITE_HOOK_ENABLED.set(true);
        level.getChunkAt(AE); level.getChunkAt(NATIVE_FAR); level.getChunkAt(DEAD);
        manifest = new Properties();
        if (phase.equals("create")) {
            check(MeteoriteFieldRegistry.snapshot(level).isEmpty(), "Creation requires a fresh audit world");
            MeteoriteFieldRegistry.register(level, AE, level.getGameTime() - 1200);
            MeteoriteFieldRegistry.register(level, DEAD, level.getGameTime() - MeteoriteCoreBlockEntity.decayTicks());
            placeNative(level, NATIVE_FAR);
            manifest.setProperty("creatorPid", Long.toString(ProcessHandle.current().pid()));
            manifest.setProperty("activeChargedAt", Long.toString(entry(level, AE).chargedAtTick()));
            manifest.setProperty("deadChargedAt", Long.toString(entry(level, DEAD).chargedAtTick()));
        } else {
            check(phase.equals("verify"), "Unknown audit phase");
            try (var in = Files.newInputStream(control().resolve("manifest.properties"))) { manifest.load(in); }
            check(!manifest.getProperty("creatorPid").equals(Long.toString(ProcessHandle.current().pid())),
                    "Persistence requires a new server process");
            check(MeteoriteFieldRegistry.snapshot(level).size() == 2, "Virtual sources did not survive restart");
            check(entry(level, AE).chargedAtTick() == Long.parseLong(manifest.getProperty("activeChargedAt")),
                    "Active source was recharged during save/restart");
            check(entry(level, DEAD).chargedAtTick() == Long.parseLong(manifest.getProperty("deadChargedAt")),
                    "Dead source was recharged during save/restart");
            check(nativeCharge(level) == Long.parseLong(manifest.getProperty("nativeChargedAt")),
                    "Native charge time did not survive save/restart");
            check(level.getGameTime() >= Long.parseLong(manifest.getProperty("savedGameTime")), "Saved world clock rolled back");
            MeteoriteFieldRegistry.register(level, AE, level.getGameTime());
            check(entry(level, AE).chargedAtTick() == Long.parseLong(manifest.getProperty("activeChargedAt")),
                    "Rediscovery recharged a restored virtual source");
            LOG.info("AE_AUDIT_RESTART_PASS creatorPid={} verifierPid={} sources=2 activeChargedAt={} deadChargedAt={} nativeChargedAt={} now={}",
                    manifest.getProperty("creatorPid"), ProcessHandle.current().pid(), manifest.getProperty("activeChargedAt"),
                    manifest.getProperty("deadChargedAt"), nativeCharge(level), level.getGameTime());
        }
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true; player.onUpdateAbilities();
        player.teleportTo(level, 0.5, 100, 0.5, 0, 15);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(MagItems.COSMIC_COMPASS.get()));
        player.getInventory().selected = 0; player.inventoryMenu.broadcastChanges();
        aeProbe = probe(level, AE); nativeProbe = probe(level, NATIVE_FAR);
    }

    private static void beginStage(final ServerLevel level, final String phase) throws Exception {
        final boolean creating = phase.equals("create");
        final boolean hook = creating ? stage != 2 && stage != 3 : stage != 2;
        MagConfig.AE2_METEORITE_HOOK_ENABLED.set(hook);
        boolean near = stage == 1 || stage == 2;
        if (near) placeNative(level, NATIVE_NEAR); else level.setBlockAndUpdate(NATIVE_NEAR, Blocks.AIR.defaultBlockState());
        if (creating && stage == 3) level.setBlockAndUpdate(NATIVE_FAR, Blocks.AIR.defaultBlockState());
        if (creating && stage == 4) placeNative(level, NATIVE_FAR);
        final String selection = near ? "native" : hook ? "ae2" : "none";
        final float expectedAngle = selection.equals("ae2") ? 0.75f : selection.equals("native") ? 0.5f : 0f;
        stageTicks = 0; forceSamples = 0; aeMax = 0; nativeMax = 0;
        Files.writeString(control().resolve("stage"), phase + "|" + stage + "|" + selection + "|" + hook + "|" + expectedAngle);
        LOG.info("AE_AUDIT_STAGE phase={} stage={} expectedSelection={} hook={}", phase, stage, selection, hook);
    }

    private static void placeNative(final ServerLevel level, final BlockPos pos) {
        if (level.getBlockState(pos).is(MagBlocks.METEORITE_CORE.get())) return;
        level.setBlockAndUpdate(pos, MagBlocks.METEORITE_CORE.get().defaultBlockState());
        ((MeteoriteCoreBlockEntity) level.getBlockEntity(pos)).refill(level.getGameTime());
    }
    private static ItemEntity probe(final ServerLevel level, final BlockPos source) {
        final var item = new ItemEntity(level, source.getX() + 3.5, source.getY() + 0.5, source.getZ() + 0.5,
                new ItemStack(Items.IRON_INGOT));
        item.setNoGravity(true); item.setInvulnerable(true); item.setPickUpDelay(32767);
        item.setDeltaMovement(Vec3.ZERO); level.addFreshEntity(item); return item;
    }
    private static MeteoriteFieldRegistry.Entry entry(final ServerLevel level, final BlockPos pos) {
        return MeteoriteFieldRegistry.snapshot(level).stream().filter(e -> e.pos().equals(pos)).findFirst().orElseThrow();
    }
    private static long nativeCharge(final ServerLevel level) {
        final var be = level.getBlockEntity(NATIVE_FAR);
        check(be instanceof MeteoriteCoreBlockEntity, "Native core missing after restart");
        return be.saveWithoutMetadata(level.registryAccess()).getLong("ChargedAt");
    }
}
