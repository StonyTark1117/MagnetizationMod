package com.stonytark.magnetization.compat;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Absent-mod-safe adapter for foreign blocks that can provide a live magnetic
 * field. Registry IDs form the stable boundary; optional implementation details
 * are read reflectively only after the matching mod and block are present.
 */
public final class ExternalFieldCompat {
    private static final Map<String, Float> CNA_STRENGTH_FALLBACKS = Map.of(
            "magnetite_block", 1.0f,
            "redstone_magnet", 2.0f,
            "layered_magnet", 4.0f,
            "fluxuated_magnetite", 8.0f,
            "netherite_magnet", 24.0f);

    private ExternalFieldCompat() {}

    public static boolean isKnownEmitter(final BlockState state) {
        final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) return false;
        return switch (id.getNamespace()) {
            case "create_new_age" -> CNA_STRENGTH_FALLBACKS.containsKey(id.getPath());
            case "immersiveengineering" -> id.getPath().equals("electromagnet")
                    || id.getPath().equals("tesla_coil");
            case "alexscaves" -> id.getPath().equals("azure_magnet")
                    || id.getPath().equals("scarlet_magnet");
            case "createaddition" -> id.getPath().equals("tesla_coil");
            case "createendertransmission" -> id.getPath().equals("energy_transmitter");
            case "tfmg" -> id.getPath().equals("polarizer");
            default -> false;
        };
    }

    public static boolean isSupportedEmitter(final BlockState state) {
        final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null || !isKnownEmitter(state)) return false;
        return switch (id.getNamespace()) {
            case "create_new_age" -> MagConfig.createNewAgeFieldsEnabled();
            case "immersiveengineering" -> MagConfig.immersiveEngineeringFieldsEnabled();
            case "alexscaves" -> MagConfig.alexsCavesFieldsEnabled();
            case "createaddition" -> MagConfig.createAdditionFieldsEnabled();
            case "createendertransmission" -> false;
            case "tfmg" -> MagConfig.tfmgPolarizerFieldEnabled();
            default -> false;
        };
    }

    public static @Nullable MagneticField currentField(final Level level, final BlockPos pos) {
        if (!level.hasChunkAt(pos)) return null;
        final BlockState state = level.getBlockState(pos);
        final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null || !isSupportedEmitter(state)) return null;
        return switch (id.getNamespace()) {
            case "create_new_age" -> createNewAgeField(level, pos, state, id.getPath());
            case "immersiveengineering" -> immersiveEngineeringField(level, pos, state, id.getPath());
            case "alexscaves" -> alexsCavesField(level, pos, state, id.getPath());
            case "createaddition" -> createAdditionField(level, pos, state);
            case "tfmg" -> tfmgField(level.getBlockEntity(pos));
            default -> null;
        };
    }

    /** Alex's Caves already moves ordinary entities itself. Its projected field is
     * applied only to physics ships to avoid doubling that native entity force. */
    public static boolean shipsOnly(final BlockState state) {
        final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && "alexscaves".equals(id.getNamespace());
    }

    public static boolean isImmersiveEngineeringRailgunShot(final net.minecraft.world.entity.Entity entity) {
        final ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id != null && id.getNamespace().equals("immersiveengineering")
                && id.getPath().equals("railgun_shot");
    }

    /** Native-strength-aware machine potency. A return value of zero delegates to
     * the generic external-magnet potency configured for the public tag. */
    public static int machineMagnetPotency(final ResourceLocation id, final int configuredBaseline) {
        if (id.getNamespace().equals("create_new_age")) {
            final Float nativeStrength = CNA_STRENGTH_FALLBACKS.get(id.getPath());
            if (nativeStrength != null && MagConfig.createNewAgeCompatEnabled()) {
                return Math.max(1, Math.round(nativeStrength * Math.max(1, configuredBaseline / 2.0f)));
            }
        }
        if (id.getNamespace().equals("alexscaves") && MagConfig.alexsCavesCompatEnabled()) {
            if (id.getPath().equals("azure_magnet") || id.getPath().equals("scarlet_magnet")) {
                return Math.max(configuredBaseline, configuredBaseline * 3);
            }
            if (id.getPath().contains("neodymium")) return Math.max(configuredBaseline, configuredBaseline * 2);
        }
        return 0;
    }

    private static @Nullable MagneticField createNewAgeField(final Level level, final BlockPos pos,
                                                              final BlockState state, final String path) {
        final double multiplier = MagConfig.createNewAgeFieldForceMultiplier();
        if (multiplier <= 0.0d) return null;
        float nativeStrength = CNA_STRENGTH_FALLBACKS.getOrDefault(path, 0.0f);
        try {
            final Object value = state.getBlock().getClass().getMethod("getStrength").invoke(state.getBlock());
            if (value instanceof Number number) nativeStrength = Math.max(0.0f, number.floatValue());
        } catch (final ReflectiveOperationException | RuntimeException ignored) { }
        if (nativeStrength <= 0.0f) return null;
        final double force = MagneticStrength.WEAK.force() * nativeStrength * multiplier;
        final MagneticPolarity polarity = level.hasNeighborSignal(pos)
                ? MagneticPolarity.SOUTH : MagneticPolarity.NORTH;
        return field(pos, Direction.UP, polarity, force, 0.0d);
    }

    private static @Nullable MagneticField alexsCavesField(final Level level, final BlockPos pos,
                                                           final BlockState state, final String path) {
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        // Alex's Caves only maintains isLocallyActive on the client for its
        // looping sound.  The server-side source of truth used by its own
        // magnet tick is the block's POWERED property.
        if (blockEntity == null || !booleanProperty(state, "powered", false)) return null;
        final Direction direction = invokeDirection(blockEntity, "getDirection", facing(state));
        final boolean azure = invokeBoolean(blockEntity, "isAzure", path.equals("azure_magnet"));
        final int nativeRange = Math.max(1, invokeInt(blockEntity, "getEffectiveRange", 5));
        final double force = MagneticStrength.STRONG.force()
                * MagConfig.alexsCavesFieldForceMultiplier();
        if (force <= 0.0d) return null;
        return field(pos, direction, azure ? MagneticPolarity.NORTH : MagneticPolarity.SOUTH,
                force, nativeRange);
    }

    private static @Nullable MagneticField immersiveEngineeringField(final Level level, final BlockPos pos,
                                                                     final BlockState state, final String path) {
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return null;
        final double ratio = energyRatio(blockEntity);
        if (ratio <= 0.0d) return null;
        if (path.equals("tesla_coil")) {
            if (!invokeBooleanWithInt(blockEntity, "canRun", 1, redstoneEnabled(level, pos, blockEntity))) return null;
            // IE's coil is a discharge machine, so project a brief field pulse rather
            // than turning stored FE into an uninterrupted permanent field.
            if (level.getGameTime() % 10L >= 2L) return null;
        } else if (!redstoneEnabled(level, pos, blockEntity)) {
            return null;
        }
        final double low = MagneticStrength.WEAK.force();
        final double high = path.equals("tesla_coil")
                ? MagneticStrength.EXTREME.force() : MagneticStrength.STRONG.force();
        final double force = (low + (high - low) * ratio)
                * MagConfig.immersiveEngineeringFieldForceMultiplier();
        return force <= 0.0d ? null : field(pos, facing(blockEntity, state), MagneticPolarity.NORTH, force, 0.0d);
    }

    private static @Nullable MagneticField createAdditionField(final Level level, final BlockPos pos,
                                                               final BlockState state) {
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || !invokeBoolean(blockEntity, "isPoweredState", false)) return null;
        final double ratio = Math.max(0.05d, energyRatio(blockEntity));
        final double force = (MagneticStrength.WEAK.force()
                + (MagneticStrength.EXTREME.force() - MagneticStrength.WEAK.force()) * ratio)
                * MagConfig.createAdditionFieldForceMultiplier();
        return force <= 0.0d ? null : field(pos, facing(state), MagneticPolarity.NORTH, force, 0.0d);
    }

    private static @Nullable MagneticField tfmgField(final @Nullable BlockEntity blockEntity) {
        if (blockEntity == null) return null;
        try {
            return com.stonytark.magnetization.compat.tfmg.TfmgPolarizerCompat.currentField(blockEntity);
        } catch (final LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static MagneticField field(final BlockPos pos, final Direction direction,
                                       final MagneticPolarity polarity, final double force,
                                       final double customRange) {
        final MagneticStrength tier = MagneticStrength.nearestForForce(force);
        return new MagneticField(Vec3.atCenterOf(pos), Vec3.atLowerCornerOf(direction.getNormal()),
                polarity, tier, MagneticField.Shape.OMNIDIRECTIONAL, customRange, force);
    }

    private static Direction facing(final BlockEntity blockEntity, final BlockState state) {
        return invokeDirection(blockEntity, "getFacing", facing(state));
    }

    private static Direction facing(final BlockState state) {
        if (state.hasProperty(BlockStateProperties.FACING)) return state.getValue(BlockStateProperties.FACING);
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) return state.getValue(HorizontalDirectionalBlock.FACING);
        return Direction.UP;
    }

    private static boolean redstoneEnabled(final Level level, final BlockPos pos, final BlockEntity blockEntity) {
        final boolean powered = invokeBoolean(blockEntity, "isRSPowered", level.hasNeighborSignal(pos));
        final boolean inverted = readBooleanField(blockEntity, "redstoneControlInverted", false);
        return powered != inverted;
    }

    private static double energyRatio(final BlockEntity blockEntity) {
        for (Class<?> type = blockEntity.getClass(); type != null; type = type.getSuperclass()) {
            for (final Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    final Object storage = field.get(blockEntity);
                    if (storage == null) continue;
                    final Method stored = storage.getClass().getMethod("getEnergyStored");
                    final Method capacity = storage.getClass().getMethod("getMaxEnergyStored");
                    final Object storedValue = stored.invoke(storage);
                    final Object capacityValue = capacity.invoke(storage);
                    if (storedValue instanceof Number have && capacityValue instanceof Number max
                            && max.doubleValue() > 0.0d) {
                        return Math.max(0.0d, Math.min(1.0d, have.doubleValue() / max.doubleValue()));
                    }
                } catch (final ReflectiveOperationException | RuntimeException ignored) { }
            }
        }
        return 0.0d;
    }

    private static boolean invokeBoolean(final Object target, final String name, final boolean fallback) {
        final Object value = invokeNoArgs(target, name);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static boolean invokeBooleanWithInt(final Object target, final String name, final int arg,
                                                final boolean fallback) {
        try {
            final Object value = target.getClass().getMethod(name, int.class).invoke(target, arg);
            return value instanceof Boolean bool ? bool : fallback;
        } catch (final ReflectiveOperationException | RuntimeException ignored) {
            return fallback;
        }
    }

    private static int invokeInt(final Object target, final String name, final int fallback) {
        final Object value = invokeNoArgs(target, name);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static Direction invokeDirection(final Object target, final String name, final Direction fallback) {
        final Object value = invokeNoArgs(target, name);
        return value instanceof Direction direction ? direction : fallback;
    }

    private static @Nullable Object invokeNoArgs(final Object target, final String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (final ReflectiveOperationException | RuntimeException ignored) { }
        }
        return null;
    }

    private static boolean readBooleanField(final Object target, final String name, final boolean fallback) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.getBoolean(target);
            } catch (final ReflectiveOperationException | RuntimeException ignored) { }
        }
        return fallback;
    }

    private static boolean booleanProperty(final BlockState state, final String name, final boolean fallback) {
        for (final var property : state.getProperties()) {
            if (property instanceof BooleanProperty booleanProperty && property.getName().equals(name)) {
                return state.getValue(booleanProperty);
            }
        }
        return fallback;
    }
}
