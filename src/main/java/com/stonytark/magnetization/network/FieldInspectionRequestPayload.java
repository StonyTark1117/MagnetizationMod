package com.stonytark.magnetization.network;

import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.physics.inspection.FieldInspectionTracker;
import com.stonytark.magnetization.physics.inspection.ShipInspectionTarget;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import java.util.Map;
import java.util.WeakHashMap;

/** The client requests inspection, never supplies a ship ID or claims to wear goggles. */
public record FieldInspectionRequestPayload() implements CustomPacketPayload {
    public static final Type<FieldInspectionRequestPayload> TYPE = new Type<>(Magnetization.id("field_inspection_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FieldInspectionRequestPayload> CODEC = StreamCodec.of((b,p) -> {}, b -> new FieldInspectionRequestPayload());
    private static final Map<ServerPlayer, Long> LAST_REQUEST = new WeakHashMap<>();
    @Override public Type<FieldInspectionRequestPayload> type() { return TYPE; }
    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(TYPE, CODEC, (data, ctx) -> ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || !player.isShiftKeyDown() || !GogglesItem.isWearingGoggles(player)) return;
            long now = player.serverLevel().getGameTime();
            Long previous = LAST_REQUEST.get(player);
            if (previous != null && now >= previous && now - previous < 4) return;
            LAST_REQUEST.put(player, now);
            var ship = ShipInspectionTarget.find(player);
            if (ship == null) {
                PacketDistributor.sendToPlayer(player, new FieldInspectionPayload(player.level().dimension().location(), null, Vec3.ZERO,
                        new FieldInspectionTracker.Snapshot(now - 1, Vec3.ZERO, Vec3.ZERO, java.util.List.of(), 0, 0)));
                return;
            }
            FieldInspectionTracker.watch(player.serverLevel(), ship.getUniqueId());
            var b = ship.boundingBox();
            Vec3 center = new Vec3((b.minX()+b.maxX())/2, b.maxY() + .75, (b.minZ()+b.maxZ())/2);
            PacketDistributor.sendToPlayer(player, new FieldInspectionPayload(player.level().dimension().location(), ship.getUniqueId(), center,
                    FieldInspectionTracker.latest(player.serverLevel(), ship.getUniqueId())));
        }));
    }
}
