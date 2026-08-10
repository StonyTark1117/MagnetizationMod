package com.stonytark.magnetization.compat;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.EmitterRegistry;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.physics.LoadedChunkAccess;
import com.stonytark.magnetization.physics.MagneticFields;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

/** Experimental, config-off-by-default field projection through linked Create:
 * Ender Transmission kinetic channels. Relayed fields are one hop only. */
public final class EnderFieldRelayCompat {
    private EnderFieldRelayCompat() {}

    public static void apply(final ServerLevel destination) {
        applyPositions(destination, EmitterRegistry.snapshot(destination));
    }

    /** Hot tick path: relay only transmitters close enough to an active target to matter. */
    public static void apply(final ServerLevel destination, final Collection<Long> targetChunks) {
        if (targetChunks.isEmpty()) return;
        applyPositions(destination, EmitterRegistry.snapshotExternalInChunks(
                destination, targetChunks, 4096));
    }

    private static void applyPositions(final ServerLevel destination,
                                       final Iterable<BlockPos> destinations) {
        if (!MagConfig.enderTransmissionFieldRelayEnabled()) return;
        for (final BlockPos destinationPos : destinations) {
            final BlockState state = LoadedChunkAccess.blockState(destination, destinationPos);
            if (state == null || !isEnergyTransmitter(state)) continue;
            final BlockEntity local = LoadedChunkAccess.blockEntity(destination, destinationPos);
            if (local == null) continue;
            for (final Object connected : connectedTransmitters(local)) {
                if (!(connected instanceof BlockEntity remote)) continue;
                if (!(remote.getLevel() instanceof ServerLevel source)) continue;
                final MagneticField sourceField = MagneticFields.nearestField(
                        source, Vec3.atCenterOf(remote.getBlockPos()));
                if (sourceField == null) continue;
                final MagneticField relayed = new MagneticField(
                        Vec3.atCenterOf(destinationPos), sourceField.axis(), sourceField.polarity(),
                        sourceField.strength(), sourceField.shape(), sourceField.range(), sourceField.force());
                FieldApplicator.apply(destination, relayed);
                break;
            }
        }
    }

    private static boolean isEnergyTransmitter(final BlockState state) {
        final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && id.getNamespace().equals("createendertransmission")
                && id.getPath().equals("energy_transmitter");
    }

    private static List<?> connectedTransmitters(final Object transmitter) {
        try {
            final Method method = transmitter.getClass().getMethod("getConnectedTransmitters");
            final Object result = method.invoke(transmitter);
            return result instanceof List<?> list ? list : List.of();
        } catch (final ReflectiveOperationException | RuntimeException ignored) {
            return List.of();
        }
    }
}
