package com.stonytark.magnetization.compat.tfmg;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Reflection boundary for TFMG's optional Polarizer block entity. Keeping all
 * TFMG types behind public method discovery lets the base mod compile and load
 * without TFMG while still deriving a field from its live network voltage. */
public final class TfmgPolarizerCompat {
    private record VoltageAccess(Method getData, Method getVoltage) {}

    private static final ConcurrentHashMap<Class<?>, Optional<VoltageAccess>> ACCESS =
            new ConcurrentHashMap<>();

    private TfmgPolarizerCompat() {}

    /** Current field for a live TFMG Polarizer, or null when disabled/unpowered. */
    public static @Nullable MagneticField currentField(final BlockEntity blockEntity) {
        if (!MagConfig.tfmgPolarizerFieldEnabled()) return null;
        return fieldForVoltage(blockEntity.getBlockPos(), blockEntity.getBlockState(), voltage(blockEntity));
    }

    /** Deterministic voltage-to-field mapping shared by runtime and GameTests. */
    public static @Nullable MagneticField fieldForVoltage(final BlockPos pos, final BlockState state,
                                                           final int voltage) {
        final double multiplier = MagConfig.tfmgPolarizerForceMultiplier();
        if (!MagConfig.tfmgPolarizerFieldEnabled() || voltage <= 0 || multiplier <= 0.0d) return null;

        final double normalized = Math.min(1.0d,
                voltage / (double) Math.max(1, MagConfig.tfmgPolarizerVoltageForExtreme()));
        final double weak = MagneticStrength.WEAK.force();
        final double extreme = MagneticStrength.EXTREME.force();
        final double force = weak * Math.pow(extreme / weak, normalized) * multiplier;
        final MagneticStrength tier = MagneticStrength.nearestForForce(force);
        final Direction facing = state.hasProperty(HorizontalDirectionalBlock.FACING)
                ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        final Vec3 axis = Vec3.atLowerCornerOf(facing.getNormal());
        return new MagneticField(Vec3.atCenterOf(pos), axis, MagneticPolarity.NORTH,
                tier, MagneticField.Shape.OMNIDIRECTIONAL, 0.0d, force);
    }

    /** Read ElectricBlockEntity#getData().getVoltage() without linking TFMG. */
    public static int voltage(final BlockEntity blockEntity) {
        final Optional<VoltageAccess> access = ACCESS.computeIfAbsent(
                blockEntity.getClass(), TfmgPolarizerCompat::findVoltageAccess);
        if (access.isEmpty()) return 0;
        try {
            final Object data = access.get().getData().invoke(blockEntity);
            if (data == null) return 0;
            final Object value = access.get().getVoltage().invoke(data);
            return value instanceof Number number ? Math.max(0, number.intValue()) : 0;
        } catch (final ReflectiveOperationException | RuntimeException ignored) {
            return 0;
        }
    }

    private static Optional<VoltageAccess> findVoltageAccess(final Class<?> type) {
        try {
            final Method getData = type.getMethod("getData");
            final Method getVoltage = getData.getReturnType().getMethod("getVoltage");
            return Optional.of(new VoltageAccess(getData, getVoltage));
        } catch (final ReflectiveOperationException | RuntimeException ignored) {
            return Optional.empty();
        }
    }
}
