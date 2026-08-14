package com.stonytark.magnetization.content.emp;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Internal EMP hook for machines whose public FE capability is intentionally
 * receive-only. Making those capabilities extractable would let ordinary pipes
 * steal working energy, so an EMP clears the owning buffer through this narrow
 * path instead.
 */
public interface EmpDrainable {

    IEnergyStorage energyBuffer();

    /** Clear the machine's real backing buffer, including shared multiblocks. */
    void clearEnergyForEmp();

    /** Persist and synchronize an EMP change without exposing general extraction. */
    default void syncEmpEnergyChange() {
        if (!(this instanceof BlockEntity blockEntity)) return;
        blockEntity.setChanged();
        if (blockEntity.getLevel() instanceof ServerLevel server) {
            server.sendBlockUpdated(blockEntity.getBlockPos(), blockEntity.getBlockState(),
                    blockEntity.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
