package com.stonytark.magnetization.compat.aeroportals;

import com.breakinblocks.aeroportals.api.SubLevelTransferEvent;
import com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity;
import com.stonytark.magnetization.content.railgun.RailgunRemoteItem;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.ShipMagneticRegistry;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;

/** Optional lifecycle bridge for AeroPortals' reconstructed Sable sublevels. */
public final class MagAeroPortalsCompat {

    private static final java.util.concurrent.ConcurrentMap<java.util.UUID,
            java.lang.ref.WeakReference<dev.ryanhcode.sable.sublevel.ServerSubLevel>> RECENT_TRANSFERS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private MagAeroPortalsCompat() {}

    public static void wire(final IEventBus gameBus) {
        gameBus.addListener(MagAeroPortalsCompat::onSubLevelTransfer);
    }

    static void onSubLevelTransfer(final SubLevelTransferEvent event) {
        if (!MagConfig.aeroPortalsCompatEnabled()) return;
        RECENT_TRANSFERS.put(event.subUuid(), new java.lang.ref.WeakReference<>(event.newSub()));
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

        repairSimulatedSwivels(event);

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
        final java.lang.ref.WeakReference<dev.ryanhcode.sable.sublevel.ServerSubLevel> transfer =
                RECENT_TRANSFERS.remove(uuid);
        return transfer == null ? null : transfer.get();
    }

    /**
     * AeroPortals 1.2.3 looks up Simulated's constraint repair method with the
     * old {@code SubLevel} parameter. Simulated 1.3.0 exposes the narrower
     * {@code ServerSubLevel} signature, so the upstream reflective bridge skips
     * both plate remapping and constraint reattachment. Use the hard dependency's
     * current public API directly while this optional integration is active.
     */
    private static void repairSimulatedSwivels(final SubLevelTransferEvent event) {
        final var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(event.dstLevel());
        if (container == null) return;
        for (final var holder : event.newSub().getPlot().getLoadedChunks()) {
            for (final BlockEntity blockEntity : holder.getChunk().getBlockEntities().values()) {
                if (!(blockEntity instanceof
                        dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity bearing)) {
                    continue;
                }
                final net.minecraft.core.BlockPos oldPlate = bearing.getPlatePos();
                if (oldPlate != null) bearing.setPlatePos(event.remapPlotPos(oldPlate));
                final java.util.UUID attachedId = bearing.getSubLevelID();
                final dev.ryanhcode.sable.sublevel.SubLevel attached = attachedId == null
                        ? null : container.getSubLevel(attachedId);
                if (attached instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel serverAttached) {
                    bearing.reattachConstraint(serverAttached, true);
                    bearing.setChanged();
                }
            }
        }
    }

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
