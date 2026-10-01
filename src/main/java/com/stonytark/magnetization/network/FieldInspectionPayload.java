package com.stonytark.magnetization.network;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.physics.inspection.FieldInspectionTracker;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import java.util.ArrayList;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public record FieldInspectionPayload(ResourceLocation dimension, @Nullable UUID ship, Vec3 center,
                                     FieldInspectionTracker.Snapshot snapshot) implements CustomPacketPayload {
    public static final Type<FieldInspectionPayload> TYPE = new Type<>(Magnetization.id("field_inspection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FieldInspectionPayload> CODEC = StreamCodec.of(FieldInspectionPayload::encode, FieldInspectionPayload::decode);
    private static volatile FieldInspectionPayload latest;
    private static long receivedAt;
    public static @Nullable FieldInspectionPayload latest() { return latest; }
    public static boolean fresh() { return System.nanoTime() - receivedAt < 1_000_000_000L; }
    public static void clear() { latest = null; }
    @Override public Type<FieldInspectionPayload> type() { return TYPE; }
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(TYPE, CODEC, (data, ctx) -> ctx.enqueueWork(() -> { latest = data; receivedAt = System.nanoTime(); }));
    }
    private static void vec(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 vec(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    private static void encode(RegistryFriendlyByteBuf b, FieldInspectionPayload p) {
        b.writeResourceLocation(p.dimension); b.writeBoolean(p.ship != null);
        if (p.ship == null) return;
        b.writeUUID(p.ship); vec(b, p.center); b.writeLong(p.snapshot.tick());
        vec(b, p.snapshot.force()); vec(b, p.snapshot.torque()); b.writeVarInt(p.snapshot.omitted()); b.writeVarInt(p.snapshot.limited());
        b.writeVarInt(p.snapshot.sources().size());
        for (var source : p.snapshot.sources()) {
            vec(b, source.origin()); vec(b, source.requested()); vec(b, source.applied()); vec(b, source.torque()); b.writeBoolean(source.capped());
        }
    }
    private static FieldInspectionPayload decode(RegistryFriendlyByteBuf b) {
        var dimension = b.readResourceLocation();
        if (!b.readBoolean()) return new FieldInspectionPayload(dimension, null, Vec3.ZERO, new FieldInspectionTracker.Snapshot(-1, Vec3.ZERO, Vec3.ZERO, java.util.List.of(), 0, 0));
        var id = b.readUUID(); var center = vec(b); long tick = b.readLong();
        var force = vec(b); var torque = vec(b); int omitted = b.readVarInt(); int limited = b.readVarInt(); int count = b.readVarInt();
        if (count < 0 || count > 128 || omitted < 0 || limited < 0 || (long) limited > (long) count + omitted) throw new IllegalArgumentException("Invalid inspection source count");
        var sources = new ArrayList<FieldInspectionTracker.Contribution>(count);
        for (int i = 0; i < count; i++) sources.add(new FieldInspectionTracker.Contribution(vec(b), vec(b), vec(b), vec(b), b.readBoolean()));
        return new FieldInspectionPayload(dimension, id, center, new FieldInspectionTracker.Snapshot(tick, force, torque, java.util.List.copyOf(sources), omitted, limited));
    }
}
