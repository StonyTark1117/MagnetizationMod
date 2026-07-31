package com.stonytark.magnetization.compat.aeroportals;

import com.breakinblocks.aeroportals.api.SubLevelTransferEvent;
import com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity;
import com.stonytark.magnetization.content.railgun.RailgunRemoteItem;
import com.stonytark.magnetization.physics.ShipMagneticRegistry;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;

/** Optional lifecycle bridge for AeroPortals' reconstructed Sable sublevels. */
public final class MagAeroPortalsCompat {

    private static final java.util.concurrent.atomic.AtomicReference<RecentTransfer> RECENT_TRANSFER =
            new java.util.concurrent.atomic.AtomicReference<>();

    private MagAeroPortalsCompat() {}

    public static void wire(final IEventBus gameBus) {
        gameBus.addListener(MagAeroPortalsCompat::onSubLevelTransfer);
    }

    static void onSubLevelTransfer(final SubLevelTransferEvent event) {
        RECENT_TRANSFER.set(new RecentTransfer(event.subUuid(), new java.lang.ref.WeakReference<>(event.newSub())));
        // A transfer replaces the ServerSubLevel object and changes its owning
        // ServerLevel. Never allow either dimension's derived ship state to be
        // served from a cache populated before reconstruction.
        ShipMagneticRegistry.invalidateAll(event.srcLevel());
        ShipMagneticRegistry.invalidateAll(event.dstLevel());

        // Remotes still installed in railgun emitters are part of the serialized
        // ship. Their absolute plot position/dimension must follow the move.
        for (final var holder : event.newSub().getPlot().getLoadedChunks()) {
            final LevelChunk chunk = holder.getChunk();
            for (final BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (blockEntity instanceof RailgunEmitterBlockEntity railgun) {
                    remapContainer(railgun.remoteContainer(), event);
                }
            }
        }

        // A player can remove the paired remote before piloting the ship through
        // the portal. Update every online inventory whose binding points into one
        // of the plots moved by this transfer; unrelated source-dimension rails
        // remain untouched because remapPlotPos returns the original position.
        for (final var player : event.srcLevel().getServer().getPlayerList().getPlayers()) {
            remapInventory(player.getInventory(), event);
        }
    }

    /** Consume the destination object captured from the real transfer event.
     * Package integration tests use this instead of assuming the destination
     * container kept a far-away sublevel active rather than in holding. */
    public static dev.ryanhcode.sable.sublevel.ServerSubLevel consumeRecentTransfer(final java.util.UUID uuid) {
        final RecentTransfer transfer = RECENT_TRANSFER.getAndSet(null);
        return transfer != null && transfer.uuid().equals(uuid) ? transfer.sub().get() : null;
    }

    private record RecentTransfer(java.util.UUID uuid,
                                  java.lang.ref.WeakReference<dev.ryanhcode.sable.sublevel.ServerSubLevel> sub) {}

    private static void remapContainer(final Container container, final SubLevelTransferEvent event) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (remapStack(container.getItem(slot), event)) container.setChanged();
        }
    }

    private static void remapInventory(final Inventory inventory, final SubLevelTransferEvent event) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (remapStack(inventory.getItem(slot), event)) inventory.setChanged();
        }
    }

    private static boolean remapStack(final ItemStack stack, final SubLevelTransferEvent event) {
        if (stack.isEmpty()) return false;
        final var oldPos = RailgunRemoteItem.boundPos(stack);
        if (oldPos == null) return false;
        final boolean belongsToMovedPlot = event.chainPlotMoves().stream()
                .anyMatch(move -> move.containedOldPos(oldPos));
        if (!belongsToMovedPlot) return false;
        final var newPos = event.remapPlotPos(oldPos);
        return RailgunRemoteItem.remapBinding(stack, event.srcLevel().dimension(),
                event.dstLevel().dimension(), ignored -> newPos);
    }
}
