package com.stonytark.magnetization.content;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Per-BlockEntity helper that suppresses redundant {@code sendBlockUpdated}
 * broadcasts. Machine BEs that carry a FluidTank + inventory push their full
 * update tag to every watching client on a fixed interval; for an idle machine
 * (no fuel moving, not firing) that re-serializes and re-ships an unchanged
 * blob twice a second per machine. This gate remembers the last broadcast tag
 * and reports {@code changed() == true} only when a client-visible field
 * actually differs, so the periodic sync becomes a no-op while nothing changes.
 *
 * <p>Comparing the whole update tag (rather than hand-listing fields) is correct
 * by construction — it can't miss a synced field — and the tag build is cheap
 * next to the avoided network traffic. Hold one instance per BE and gate the
 * interval sync on it.
 */
public final class MachineSyncGate {

    private CompoundTag last;

    /** True if {@code be}'s update tag differs from the last accepted broadcast
     *  (and records the new tag). Gate the interval {@code sendBlockUpdated} on this. */
    public boolean changed(final BlockEntity be, final HolderLookup.Provider registries) {
        final CompoundTag now = be.getUpdateTag(registries);
        if (now.equals(last)) return false;
        last = now;
        return true;
    }
}
