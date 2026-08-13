package com.stonytark.magnetization.physics;

import com.stonytark.magnetization.api.MagTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Optional convenience: when ferromagnetic item drops are pulled inside an
 * emitter's intake radius and there's an adjacent inventory container, push the
 * stacks into that container. Acts like a built-in funnel/hopper for items the
 * field has already gathered.
 */
public final class InventorySink {

    /** Radius around the emitter origin in which item entities are eligible for ingestion. */
    private static final double INTAKE_RADIUS = 1.25d;
    private static final long ABSENT_RESCAN_TICKS = 20L;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<ServerLevel, Long2ObjectOpenHashMap<CachedTarget>> TARGETS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private InventorySink() {}

    public static void tryIngest(final ServerLevel level, final BlockPos emitterPos) {
        final IItemHandler target = cachedAdjacentInventory(level, emitterPos);
        if (target == null) return;

        final AABB intakeBox = AABB.ofSize(emitterPos.getCenter(), 2 * INTAKE_RADIUS, 2 * INTAKE_RADIUS, 2 * INTAKE_RADIUS);
        final List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, intakeBox,
                e -> e.isAlive()
                        && com.stonytark.magnetization.compat.FerromagneticCompat.isFerromagnetic(e.getItem()));

        for (ItemEntity drop : drops) {
            final ItemStack stack = drop.getItem();
            final ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, stack.copy(), false);
            if (remainder.getCount() == stack.getCount()) continue; // nothing accepted
            if (remainder.isEmpty()) {
                drop.discard();
            } else {
                drop.setItem(remainder);
            }
        }
    }

    /** First inventory found among the six face-adjacent neighbors, or {@code null}. */
    private static IItemHandler adjacentInventory(final ServerLevel level, final BlockPos pos) {
        int checked = 0;
        for (Direction face : DIRECTIONS) {
            checked++;
            final BlockPos neighborPos = pos.relative(face);
            final IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, face.getOpposite());
            if (handler != null) {
                PerformanceDiagnostics.recordInventoryFullScan(level, checked);
                return handler;
            }
        }
        PerformanceDiagnostics.recordInventoryFullScan(level, checked);
        return null;
    }

    private static IItemHandler cachedAdjacentInventory(final ServerLevel level, final BlockPos pos) {
        final Long2ObjectOpenHashMap<CachedTarget> levelTargets =
                TARGETS.computeIfAbsent(level, ignored -> new Long2ObjectOpenHashMap<>());
        final CachedTarget cached = levelTargets.computeIfAbsent(pos.asLong(), ignored -> new CachedTarget());
        final long now = level.getGameTime();

        if (cached.face != null) {
            final IItemHandler handler = capability(level, pos, cached.face);
            if (handler != null) return handler;
            // The block or capability disappeared. Rescan immediately in case a
            // different adjacent face became the preferred target.
            cached.face = null;
            cached.nextFullScan = now;
        }
        if (now < cached.nextFullScan) return null;

        int checked = 0;
        for (final Direction face : DIRECTIONS) {
            checked++;
            final IItemHandler handler = capability(level, pos, face);
            if (handler == null) continue;
            cached.face = face;
            cached.nextFullScan = now;
            PerformanceDiagnostics.recordInventoryFullScan(level, checked);
            return handler;
        }
        cached.nextFullScan = now + ABSENT_RESCAN_TICKS;
        PerformanceDiagnostics.recordInventoryFullScan(level, checked);
        return null;
    }

    private static IItemHandler capability(final ServerLevel level, final BlockPos pos, final Direction face) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK,
                pos.relative(face), face.getOpposite());
    }

    /** Force the next active tick to rediscover an emitter's neighboring inventory. */
    public static void invalidate(final ServerLevel level, final BlockPos emitterPos) {
        final Long2ObjectOpenHashMap<CachedTarget> levelTargets = TARGETS.get(level);
        if (levelTargets != null) levelTargets.remove(emitterPos.asLong());
    }

    public static void onLevelUnload(final ServerLevel level) {
        TARGETS.remove(level);
    }

    /** Try to push a single stack into the first inventory adjacent to {@code anchor}.
     *  Returns the leftover (empty if fully accepted). Used by the Magnetic Excavator
     *  to direct-ingest drops on arrival rather than leaving them as ItemEntities for
     *  the polling intake to find on a later tick. */
    public static ItemStack tryDirectIngest(final ServerLevel level, final BlockPos anchor, final ItemStack stack) {
        final IItemHandler target = adjacentInventory(level, anchor);
        if (target == null) return stack;
        return ItemHandlerHelper.insertItemStacked(target, stack, false);
    }

    private static final class CachedTarget {
        private Direction face;
        private long nextFullScan = Long.MIN_VALUE;
    }
}
