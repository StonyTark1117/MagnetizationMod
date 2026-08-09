package com.stonytark.magnetization.compat;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.EmitterRegistry;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.physics.MagneticFields;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.List;

/** Experimental, config-off-by-default field projection through linked Create:
 * Ender Transmission kinetic channels. Relayed fields are one hop only. */
public final class EnderFieldRelayCompat {
    private EnderFieldRelayCompat() {}

    public static void apply(final ServerLevel destination) {
        if (!MagConfig.enderTransmissionFieldRelayEnabled()) return;
        for (final BlockPos destinationPos : EmitterRegistry.snapshot(destination)) {
            if (!isEnergyTransmitter(destination, destinationPos)) continue;
            final BlockEntity local = destination.getBlockEntity(destinationPos);
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

    private static boolean isEnergyTransmitter(final ServerLevel level, final BlockPos pos) {
        final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
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
