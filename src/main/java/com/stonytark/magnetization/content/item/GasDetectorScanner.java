package com.stonytark.magnetization.content.item;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.fluid.ExcitableGasBlock;
import com.stonytark.magnetization.content.gas.ProxyGasCloudBlockEntity;
import com.stonytark.magnetization.registry.MagBlocks;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/** Finds and classifies the nearest gaseous fluid around a handheld detector. */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class GasDetectorScanner {
    public static final int RANGE = 24;
    private static final int CACHE_TICKS = 5;
    private static final int MAX_CACHE_ENTRIES = 64;
    private static final TagKey<Fluid> GASEOUS = TagKey.create(Registries.FLUID,
            ResourceLocation.fromNamespaceAndPath("c", "gaseous"));
    private static final Map<Level, BoundedTickCache<BlockPos, Reading>> LEVEL_CACHES = new WeakHashMap<>();

    private GasDetectorScanner() {}

    @SubscribeEvent
    public static void onLevelUnload(final LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            synchronized (LEVEL_CACHES) {
                LEVEL_CACHES.remove(level);
            }
        }
    }

    public static @Nullable Reading nearest(final Level level, final BlockPos origin) {
        final long tickBucket = level.getGameTime() / CACHE_TICKS;
        final BoundedTickCache<BlockPos, Reading> cache = cacheFor(level);
        final BlockPos key = origin.immutable();
        final Reading cached = cache.get(key, tickBucket);
        if (cached != null) return cached;

        final Reading reading = scan(level, origin);
        return cache.putIfNewer(key, tickBucket, reading);
    }

    private static BoundedTickCache<BlockPos, Reading> cacheFor(final Level level) {
        synchronized (LEVEL_CACHES) {
            return LEVEL_CACHES.computeIfAbsent(level, ignored -> new BoundedTickCache<>(MAX_CACHE_ENTRIES));
        }
    }

    private static Reading scan(final Level level, final BlockPos origin) {
        final int rangeSqr = RANGE * RANGE;
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos bestPos = null;
        Fluid bestFluid = null;
        boolean bestExcited = false;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -RANGE; x <= RANGE; x++) {
            for (int z = -RANGE; z <= RANGE; z++) {
                final int horizontalDistance = x * x + z * z;
                if (horizontalDistance > rangeSqr) continue;
                cursor.set(origin.getX() + x, origin.getY(), origin.getZ() + z);
                if (!level.hasChunkAt(cursor)) continue;
                final int verticalRange = (int) Math.sqrt(rangeSqr - horizontalDistance);
                for (int y = -verticalRange; y <= verticalRange; y++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    final Fluid source;
                    final boolean excited;
                    final BlockState state = level.getBlockState(cursor);
                    if (state.is(MagBlocks.PROXY_GAS_CLOUD.get())
                            && level.getBlockEntity(cursor) instanceof ProxyGasCloudBlockEntity cloud) {
                        source = cloud.fluid();
                        excited = cloud.isExcited();
                    } else {
                        final FluidState fluidState = state.getFluidState();
                        if (!fluidState.is(GASEOUS)) continue;
                        source = sourceOf(fluidState.getType());
                        excited = isExcited(state);
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
}
