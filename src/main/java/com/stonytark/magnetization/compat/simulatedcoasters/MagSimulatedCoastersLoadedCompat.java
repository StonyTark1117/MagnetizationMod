package com.stonytark.magnetization.compat.simulatedcoasters;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Loaded-only implementation. This class must never initialize without Coasters present. */
final class MagSimulatedCoastersLoadedCompat {
    private MagSimulatedCoastersLoadedCompat() {}

    static boolean isCoasterCart(final ServerSubLevel subLevel) {
        return dev.silvergold.simulatedcoasters.track.cart.CoasterCartResolve.isCartSubLevel(subLevel);
    }

    static boolean receivesMagneticFields(final ServerSubLevel subLevel) {
        return !isPartOfCoasterStructure(subLevel)
                || com.stonytark.magnetization.config.MagConfig.simulatedCoastersFieldReaction();
    }

    static boolean structuralInducerCanAdopt(final ServerSubLevel subLevel) {
        return com.stonytark.magnetization.config.MagConfig.simulatedCoastersStructuralInducer()
                && isCoasterCart(subLevel) && isRailEngaged(subLevel);
    }

    /** Read Coasters' synchronized, persisted cart-block engagement property. */
    static boolean isRailEngaged(final ServerSubLevel cart) {
        if (!isCoasterCart(cart)) return false;
        final var plot = cart.getPlot();
        final var accessor = plot.getEmbeddedLevelAccessor();
        final var center = plot.getCenterBlock();
        final var cartBlock = dev.silvergold.simulatedcoasters.SimulatedCoastersBlocks.COASTER_CART_BLOCK.get();
        final boolean[] engaged = {false};
        dev.silvergold.simulatedcoasters.track.cart.CoasterCartPlotScan.forEachBearingWorldCell(
                plot, cartBlock, worldCell -> {
                    final var state = accessor.getBlockState(worldCell.immutable().subtract(center));
                    if (state.is(cartBlock) && state.getValue(dev.silvergold.simulatedcoasters.track.cart
                            .CoasterCartBlock.SNAP_ENGAGED)) engaged[0] = true;
                });
        return engaged[0];
    }

    /** Expand train links plus Sable-connected bodies such as riveted parts. */
    static Set<UUID> coasterStructureIds(final ServerSubLevel seed, final long now) {
        final LinkedHashSet<UUID> result = new LinkedHashSet<>();
        if (seed == null) return result;
        final SubLevelContainer container = SubLevelContainer.getContainer(seed.getLevel());
        if (container == null) return result;
        final ArrayDeque<ServerSubLevel> pending = new ArrayDeque<>();
        final Set<UUID> processed = new java.util.HashSet<>();
        pending.add(seed);
        while (!pending.isEmpty()) {
            final ServerSubLevel current = pending.removeFirst();
            if (!processed.add(current.getUniqueId())) continue;
            result.add(current.getUniqueId());
            for (final UUID id : com.stonytark.magnetization.physics.SableBridge.connectedChainIds(current, now)) {
                result.add(id);
                final var connected = container.getSubLevel(id);
                if (connected instanceof ServerSubLevel server && !processed.contains(id)) pending.add(server);
            }
            if (isCoasterCart(current)) {
                for (final UUID id : dev.silvergold.simulatedcoasters.track.cart
                        .CoasterCartTrainLinkConstraint.getTrainLinkedCartIds(current)) {
                    result.add(id);
                    final var linked = container.getSubLevel(id);
                    if (linked instanceof ServerSubLevel server && !processed.contains(id)) pending.add(server);
                }
            }
        }
        return result;
    }

    static boolean isPartOfCoasterStructure(final ServerSubLevel subLevel) {
        if (isCoasterCart(subLevel)) return true;
        final SubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
        if (container == null) return false;
        final long now = subLevel.getLevel().getGameTime();
        for (final UUID id : com.stonytark.magnetization.physics.SableBridge.connectedChainIds(subLevel, now)) {
            final var connected = container.getSubLevel(id);
            if (connected instanceof ServerSubLevel server && isCoasterCart(server)) return true;
        }
        return false;
    }
}
