package com.stonytark.magnetization.compat.copycats;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

import java.util.List;

/** Classloading-safe facade for optional Copycats+ material inspection. */
public final class MagCopycatsCompat {
    private MagCopycatsCompat() {}

    public static List<BlockState> materialsOf(final BlockEntity blockEntity) {
        if (!ModList.get().isLoaded("copycats")) return List.of();
        return MagCopycatsLoadedCompat.materialsOf(blockEntity);
    }
}
