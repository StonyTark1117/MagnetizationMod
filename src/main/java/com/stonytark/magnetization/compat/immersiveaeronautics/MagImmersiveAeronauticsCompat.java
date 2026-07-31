package com.stonytark.magnetization.compat.immersiveaeronautics;

import com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity;
import com.stonytark.magnetization.content.railgun.RailgunRemoteItem;
import com.stonytark.magnetization.physics.ShipMagneticRegistry;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Optional state migration after Immersive Aeronautics reconstructs a Sable ship. */
public final class MagImmersiveAeronauticsCompat {

    private static final AtomicReference<RecentTransfer> RECENT_TRANSFER = new AtomicReference<>();

    private MagImmersiveAeronauticsCompat() {}

    /** Called by the optional mixin. The return record is reflected so this class
     * remains loadable without Immersive Aeronautics on the normal classpath. */
    public static void onShipMoved(final ServerLevel source, final ServerLevel destination,
                                   final Object movedRecord) {
        try {
            final Class<?> type = movedRecord.getClass();
            final Method subMethod = type.getMethod("sub");
            final Method shiftMethod = type.getMethod("shift");
            final Method oldMinMethod = type.getMethod("oldRegionMin");
            final Method regionBlocksMethod = type.getMethod("regionBlocks");
            final ServerSubLevel moved = (ServerSubLevel) subMethod.invoke(movedRecord);
            final BlockPos shift = (BlockPos) shiftMethod.invoke(movedRecord);
            final BlockPos oldMin = (BlockPos) oldMinMethod.invoke(movedRecord);
            final int regionBlocks = (Integer) regionBlocksMethod.invoke(movedRecord);
            applyTransfer(source, destination, moved, shift, oldMin, regionBlocks);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            throw new IllegalStateException("Immersive Aeronautics changed its ship-transfer result API", exception);
        }
    }

    static void applyTransfer(final ServerLevel source, final ServerLevel destination,
                              final ServerSubLevel moved, final BlockPos shift,
                              final BlockPos oldRegionMin, final int regionBlocks) {
        ShipMagneticRegistry.invalidateAll(source);
        ShipMagneticRegistry.invalidateAll(destination);

        for (final var holder : moved.getPlot().getLoadedChunks()) {
            final LevelChunk chunk = holder.getChunk();
            for (final BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (blockEntity instanceof RailgunEmitterBlockEntity railgun) {
                    remapContainer(railgun.remoteContainer(), source, destination,
                            oldRegionMin, regionBlocks, shift, railgun.getBlockPos());
                }
            }
        }

        final java.util.Set<net.minecraft.world.entity.player.Player> players =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        players.addAll(source.players());
        players.addAll(destination.players());
        players.addAll(source.getServer().getPlayerList().getPlayers());
        for (final var player : players) {
            remapInventoryAfterTransfer(player.getInventory(), source, destination,
                    oldRegionMin, regionBlocks, shift);
        }
        RECENT_TRANSFER.set(new RecentTransfer(moved.getUniqueId(), moved));
    }

    private static void remapContainer(final Container container,
                                       final ServerLevel source, final ServerLevel destination,
                                       final BlockPos oldMin, final int size, final BlockPos shift,
                                       final BlockPos emitterPosition) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            final ItemStack stack = container.getItem(slot);
            if (remapStack(stack, source, destination, oldMin, size, shift, emitterPosition)) {
                container.setChanged();
            }
        }
    }

    /** Shared inventory migration entry point; public so the isolated GameTest can
     * cover a held remote without registering a connectionless fake player. */
    public static void remapInventoryAfterTransfer(final Inventory inventory,
                                                   final ServerLevel source, final ServerLevel destination,
                                                   final BlockPos oldMin, final int size, final BlockPos shift) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            final ItemStack stack = inventory.getItem(slot);
            if (remapStack(stack, source, destination, oldMin, size, shift, null)) {
                inventory.setChanged();
            }
        }
    }

    private static boolean remapStack(final ItemStack stack,
                                      final ServerLevel source, final ServerLevel destination,
                                      final BlockPos oldMin, final int size, final BlockPos shift,
                                      final BlockPos exactDestination) {
        if (stack.isEmpty()) return false;
        final BlockPos oldPos = RailgunRemoteItem.boundPos(stack);
        if (oldPos == null || !contains(oldMin, size, oldPos)) return false;
        final BlockPos newPos = exactDestination == null ? oldPos.offset(shift) : exactDestination;
        return RailgunRemoteItem.remapBinding(stack, source.dimension(), destination.dimension(), ignored -> newPos);
    }

    private static boolean contains(final BlockPos min, final int size, final BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() < min.getX() + size
                && pos.getY() >= min.getY() && pos.getY() < min.getY() + size
                && pos.getZ() >= min.getZ() && pos.getZ() < min.getZ() + size;
    }

    public static ServerSubLevel consumeRecentTransfer(final UUID uuid) {
        final RecentTransfer transfer = RECENT_TRANSFER.getAndSet(null);
        return transfer != null && transfer.uuid().equals(uuid) ? transfer.sub() : null;
    }

    private record RecentTransfer(UUID uuid, ServerSubLevel sub) {}
}
