package com.stonytark.magnetization.content.item;

import com.stonytark.magnetization.content.fluid.ExcitableGasBlock;
import com.stonytark.magnetization.content.gas.ProxyGasCloudBlockEntity;
import com.stonytark.magnetization.registry.MagFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FlowingFluid;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Finds and classifies the nearest gaseous fluid around a handheld detector. */
public final class GasDetectorScanner {
    public static final int RANGE = 24;
    private static final int CACHE_TICKS = 5;
    private static final TagKey<Fluid> GASEOUS = TagKey.create(Registries.FLUID,
            ResourceLocation.fromNamespaceAndPath("c", "gaseous"));
    private static final Map<CacheKey, CachedReading> CACHE = new HashMap<>();

    private GasDetectorScanner() {}

    public static @Nullable Reading nearest(final Level level, final BlockPos origin) {
        final CacheKey key = new CacheKey(level, origin.immutable(), level.getGameTime() / CACHE_TICKS);
        final CachedReading cached = CACHE.get(key);
        if (cached != null) return cached.reading();

        final Reading reading = scan(level, origin);
        CACHE.put(key, new CachedReading(reading));
        if (CACHE.size() > 32) CACHE.keySet().removeIf(existing -> existing.level() != level);
        return reading;
    }

    private static Reading scan(final Level level, final BlockPos origin) {
        final int rangeSqr = RANGE * RANGE;
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos bestPos = null;
        Fluid bestFluid = null;
        boolean bestExcited = false;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -RANGE; x <= RANGE; x++) {
            for (int y = -RANGE; y <= RANGE; y++) {
                for (int z = -RANGE; z <= RANGE; z++) {
                    if (x * x + y * y + z * z > rangeSqr) continue;
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (!level.hasChunkAt(cursor)) continue;
                    final Fluid source;
                    final boolean excited;
                    if (level.getBlockEntity(cursor) instanceof ProxyGasCloudBlockEntity cloud) {
                        source = cloud.fluid();
                        excited = cloud.isExcited();
                    } else {
                        final FluidState fluidState = level.getFluidState(cursor);
                        if (!fluidState.is(GASEOUS)) continue;
                        source = sourceOf(fluidState.getType());
                        excited = isExcited(level.getBlockState(cursor));
                    }
                    final double distance = cursor.distSqr(origin);
                    if (distance >= bestDistance) continue;
                    bestPos = cursor.immutable();
                    bestFluid = source;
                    bestExcited = excited;
                    bestDistance = distance;
                }
            }
        }

        return bestPos == null ? Reading.none() : new Reading(bestPos, bestFluid, bestExcited,
                bestFluid == MagFluids.RADON.get(), Math.sqrt(bestDistance));
    }

    private static Fluid sourceOf(final Fluid fluid) {
        return fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
    }

    private static boolean isExcited(final BlockState state) {
        return state.hasProperty(ExcitableGasBlock.EXCITED)
                && state.getValue(ExcitableGasBlock.EXCITED);
    }

    public record Reading(@Nullable BlockPos position, @Nullable Fluid fluid, boolean excited,
                          boolean dangerous, double distance) {
        public static Reading none() {
            return new Reading(null, null, false, false, Double.MAX_VALUE);
        }

        public boolean found() {
            return position != null && fluid != null;
        }

        public String statusKey() {
            if (!found()) return "none";
            return fluid == MagFluids.TRITIUM.get() ? "active"
                    : (excited ? "excited" : "dormant");
        }
    }

    private record CacheKey(Level level, BlockPos origin, long tickBucket) {}
    private record CachedReading(Reading reading) {}
}
