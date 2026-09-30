package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.*;
import com.stonytark.magnetization.physics.FieldApplicator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/** Opt-in development audit; requires two real, connected Minecraft clients. */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class AircraftNetworkAudit {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("magnetization/aircraft-network-audit");
    private static List<ResourceLocation> types;
    private static int index, age;
    private static Entity craft;
    private static Vec3 start;
    private static double repelStartX;
    private static boolean finished;

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("magnetization.audit.aircraftNetwork") || finished) return;
        final var server = event.getServer();
        final var pilot = server.getPlayerList().getPlayerByName("AuditPilot");
        final var observer = server.getPlayerList().getPlayerByName("AuditObserver");
        if (pilot == null || observer == null) return;
        try {
            if (types == null) {
                types = BuiltInRegistries.ENTITY_TYPE.getTag(MagTags.MAGNETIZABLE_ENTITIES).orElseThrow().stream()
                        .map(h -> BuiltInRegistries.ENTITY_TYPE.getKey(h.value()))
                        .filter(id -> id.getNamespace().equals("immersive_aircraft") || id.getNamespace().equals("aviator_dream"))
                        .sorted().toList();
                if (types.size() != 15) throw new IllegalStateException("Expected 15 aircraft, got " + types);
                pilot.setGameMode(GameType.CREATIVE); observer.setGameMode(GameType.SPECTATOR);
            }
            if (craft == null) {
                pilot.stopRiding();
                pilot.teleportTo(server.overworld(), 0, 180, 0, 0, 0);
                observer.teleportTo(server.overworld(), 4, 185, 0, 0, 0);
                craft = BuiltInRegistries.ENTITY_TYPE.get(types.get(index)).create(server.overworld());
                craft.setPos(0, 180, 0);
                craft.setInvulnerable(true);
                craft.setCustomName(net.minecraft.network.chat.Component.literal("audit-" + index));
                server.overworld().addFreshEntity(craft);
                if (!pilot.startRiding(craft, true)) throw new IllegalStateException("Cannot pilot " + types.get(index));
                start = craft.position(); age = 0;
                LOG.info("AIRCRAFT_BEGIN type={} uuid={}", types.get(index), craft.getUUID());
            }
            age++;
            if (pilot.getVehicle() != craft || !craft.isAlive()) throw new IllegalStateException("Pilot/craft lost for " + types.get(index));
            if (age % 20 == 0) observer.teleportTo(server.overworld(), craft.getX()+4, craft.getY()+5, craft.getZ(), 0, 0);
            if (age == 110) repelStartX = craft.getX();
            if (age >= 60 && age < 160) {
                final var pole = age < 110 ? MagneticPolarity.SOUTH : MagneticPolarity.NORTH;
                FieldApplicator.applyEntitiesOnly(server.overworld(), new MagneticField(
                        craft.position().add(-3, craft.getBbHeight()*0.5, 0), new Vec3(1,0,0), pole,
                        MagneticStrength.WEAK, MagneticField.Shape.OMNIDIRECTIONAL, 4, 9.0));
            }
            if (age % 10 == 0) LOG.info("AIRCRAFT_SERVER type={} uuid={} age={} x={} y={} z={} vx={}",
                    types.get(index), craft.getUUID(), age, craft.getX(), craft.getY(), craft.getZ(), craft.getDeltaMovement().x);
            if (age == 180) {
                if (craft.getX() <= repelStartX) throw new IllegalStateException("Repulsion did not reverse piloted motion for " + types.get(index)
                        + "; before=" + repelStartX + " after=" + craft.getX());
                if (craft.position().distanceTo(start) < 2) throw new IllegalStateException("No sustained motion for " + types.get(index));
                LOG.info("AIRCRAFT_PASS type={} ticks={} distance={}", types.get(index), age, craft.position().distanceTo(start));
                pilot.stopRiding(); craft.discard(); craft = null; index++;
                if (index == types.size()) { finished = true; LOG.info("AIRCRAFT_NETWORK_SERVER_PASS types={}", types.size()); }
            }
        } catch (Exception e) { finished = true; LOG.error("AIRCRAFT_NETWORK_FAILED", e); }
    }
}
