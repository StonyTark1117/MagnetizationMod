package com.stonytark.magnetization.client;

import com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Referenced only while playing the optional, registry-guarded Copycats scene. */
final class CopycatsPonderMaterials {
    private CopycatsPonderMaterials() {}

    static void applyIron(final BlockEntity blockEntity) {
        if (!(blockEntity instanceof ICopycatBlockEntity copycat)) {
            throw new IllegalStateException("Copycats scene target has no material API");
        }
        copycat.setMaterial(Blocks.IRON_BLOCK.defaultBlockState());
    }
}
