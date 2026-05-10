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

import java.util.List;

/**
 * Optional convenience: when ferromagnetic item drops are pulled inside an
 * emitter's intake radius and there's an adjacent inventory container, push the
 * stacks into that container. Acts like a built-in funnel/hopper for items the
 * field has already gathered.
 */
public final class InventorySink {

    /** Radius around the emitter origin in which item entities are eligible for ingestion. */
    private static final double INTAKE_RADIUS = 1.25d;

    private InventorySink() {}

    public static void tryIngest(final ServerLevel level, final BlockPos emitterPos) {
        final IItemHandler target = adjacentInventory(level, emitterPos);
        if (target == null) return;

        final AABB intakeBox = AABB.ofSize(emitterPos.getCenter(), 2 * INTAKE_RADIUS, 2 * INTAKE_RADIUS, 2 * INTAKE_RADIUS);
        final List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, intakeBox,
                e -> e.isAlive() && e.getItem().is(MagTags.FERROMAGNETIC_ITEMS));

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
        for (Direction face : Direction.values()) {
            final BlockPos neighborPos = pos.relative(face);
            final IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, face.getOpposite());
            if (handler != null) return handler;
        }
        return null;
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
}
