package com.stonytark.magnetization.client;

import com.stonytark.magnetization.Magnetization;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Real client physics/render world observations, enabled only by the isolated audit run profile. */
@EventBusSubscriber(modid = Magnetization.MOD_ID, value = Dist.CLIENT)
public final class AircraftNetworkAuditClient {
    private static int ticks;
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("magnetization.audit.aircraftNetwork")) return;
        final var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (mc.player.getVehicle() != null) {
            final var craft = mc.player.getVehicle();
            try { craft.getClass().getMethod("setEngineTarget", float.class).invoke(craft, 0.8f); }
            catch (NoSuchMethodException ignored) { /* Vehicles without an engine still have native controls. */ }
            final var keys = Class.forName("immersive_aircraft.client.KeyBindings");
            ((net.minecraft.client.KeyMapping) keys.getField("up").get(null)).setDown(true);
            ((net.minecraft.client.KeyMapping) keys.getField("forward").get(null)).setDown(true);
        }
        if (++ticks % 10 != 0) return;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity.getCustomName() == null || !entity.getCustomName().getString().startsWith("audit-")) continue;
            org.slf4j.LoggerFactory.getLogger("magnetization/aircraft-network-audit").info(
                    "AIRCRAFT_CLIENT player={} type={} uuid={} tick={} x={} y={} z={} vx={} pilot={} entityTicks={} chunkLoaded={} playerPos={}",
                    mc.player.getGameProfile().getName(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                    entity.getUUID(), ticks, entity.getX(), entity.getY(), entity.getZ(), entity.getDeltaMovement().x,
                    entity.getControllingPassenger() == mc.player, entity.tickCount, mc.level.hasChunkAt(entity.blockPosition()), mc.player.position());
        }
    }
}
