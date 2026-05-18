package com.stonytark.magnetization.content.pyrrhotite;

import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Storage block for pyrrhotite (Fe7S8) — bronze-tinted iron sulfide. Plain
 * cube visually, but carries a {@link PyrrhotiteBlockEntity} that polls
 * adjacent Create heat sources and emits a passive magnetic field whose
 * strength tier scales with the maximum surrounding {@code HEAT_LEVEL}.
 *
 * <p>Real-world parallel: pyrrhotite is weakly magnetic at room temperature
 * and dramatically more magnetic above the Curie point (~320°C) — a property
 * the mineral is named for in metallurgy contexts. Cold pyrrhotite blocks
 * are inert; sit one on a blaze burner and it lights up.
 *
 * <p>Plain {@code pyrrhotite_block} storage works fine as a building block
 * even with the BE attached — the BE's tick is dirt-cheap when no heat is
 * present (six neighbour-state reads per emitter-tick interval).
 */
public final class PyrrhotiteBlock extends Block implements EntityBlock {

    public PyrrhotiteBlock(final Properties props) {
        super(props);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new PyrrhotiteBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type
    ) {
        if (level.isClientSide || type != MagBlockEntities.PYRRHOTITE.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<PyrrhotiteBlockEntity>)
                PyrrhotiteBlockEntity::serverTick;
    }
}
