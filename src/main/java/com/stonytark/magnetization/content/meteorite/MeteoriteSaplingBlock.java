package com.stonytark.magnetization.content.meteorite;

import com.stonytark.magnetization.registry.MagBlockEntities;
import com.stonytark.magnetization.registry.MagBlocks;
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
 * Plantable cradle that slowly germinates into a fresh
 * {@link MagBlocks#METEORITE_CORE} over {@link MeteoriteSaplingBlockEntity#GROW_TICKS}
 * ticks. Lets a player sustain a meteorite supply at the cost of a fragment +
 * raw_magnetite cradle and a long wait.
 *
 * <p>Phase C-lite for the meteorite arc — the full Phase B crater structure
 * (#275) is still deferred; this gives players an early-game-accessible path
 * to grow new cores without needing to scour the world for natural ones.
 */
public final class MeteoriteSaplingBlock extends Block implements EntityBlock {

    public MeteoriteSaplingBlock(final Properties props) {
        super(props);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new MeteoriteSaplingBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type
    ) {
        if (level.isClientSide || type != MagBlockEntities.METEORITE_SAPLING.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<MeteoriteSaplingBlockEntity>)
                MeteoriteSaplingBlockEntity::serverTick;
    }
}
