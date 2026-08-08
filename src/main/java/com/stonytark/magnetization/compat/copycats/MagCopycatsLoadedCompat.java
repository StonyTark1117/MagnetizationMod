package com.stonytark.magnetization.compat.copycats;

import com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity;
import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Loaded-only Copycats+ API bridge. */
final class MagCopycatsLoadedCompat {
    private MagCopycatsLoadedCompat() {}

    static List<BlockState> materialsOf(final BlockEntity blockEntity) {
        if (blockEntity instanceof IMultiStateCopycatBlockEntity multi) {
            return List.copyOf(multi.getMaterialItemStorage().getAllMaterials());
        }
        if (blockEntity instanceof ICopycatBlockEntity copycat) {
            return List.of(copycat.getMaterial());
        }
        return List.of();
    }
}
