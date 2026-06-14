package com.stonytark.magnetization.content.emp;

import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * EMP flux-compression charge — a single-use electromagnetic bomb. Power it with
 * redstone and it detonates: every magnetic emitter in range goes dark for a few
 * seconds and every machine/battery's stored FE is wiped, then the charge itself
 * is consumed in the pulse. Doesn't break terrain — it fries electronics, not walls.
 */
public final class EmpChargeBlock extends Block {

    private static final int RADIUS = 12;
    private static final int BLACKOUT_TICKS = 200; // 10s
    private static final int DRAIN_PASSES = 32;

    public EmpChargeBlock(final Properties props) {
        super(props);
    }

    @Override
    protected void neighborChanged(final BlockState state, final Level level, final BlockPos pos,
                                   final Block neighborBlock, final BlockPos neighborPos, final boolean movedByPiston) {
        if (!level.isClientSide && level.hasNeighborSignal(pos) && level instanceof ServerLevel server) {
            detonate(server, pos);
        }
    }

    /** Sweep the radius: blank emitters, drain FE, flash + boom, consume the charge. */
    public static void detonate(final ServerLevel level, final BlockPos center) {
        final BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        final int r2 = RADIUS * RADIUS;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    cur.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (cur.equals(center)) continue;
                    if (!level.isLoaded(cur)) continue;
                    if (level.getBlockEntity(cur) == null) continue;
                    if (level.getBlockEntity(cur) instanceof AbstractEmitterBlockEntity emitter) {
                        emitter.disableForEmp(BLACKOUT_TICKS);
                    }
                    drainEnergy(level, cur.immutable());
                }
            }
        }
        // Visual + audio pulse — no block damage.
        level.sendParticles(ParticleTypes.FLASH, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
                1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
                120, 3.0, 3.0, 3.0, 0.5);
        level.playSound(null, center, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 3.0f, 0.4f);
        level.playSound(null, center, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 1.6f, 1.6f);
        level.removeBlock(center, false); // single-use
    }

    private static void drainEnergy(final ServerLevel level, final BlockPos pos) {
        final IEnergyStorage cap = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
        if (cap == null || !cap.canExtract()) return;
        for (int i = 0; i < DRAIN_PASSES && cap.getEnergyStored() > 0; i++) {
            if (cap.extractEnergy(Integer.MAX_VALUE, false) <= 0) break;
        }
    }
}
