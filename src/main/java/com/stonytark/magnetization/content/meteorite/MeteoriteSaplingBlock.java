package com.stonytark.magnetization.content.meteorite;

import com.stonytark.magnetization.registry.MagBlockEntities;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
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

    // Small centred outline matching the flat cross sprite — without this the
    // default full-cube shape gives an invisible solid block (you can't walk
    // through it and the highlight is a 1×1×1 box around a thin sprite).
    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0);

    public MeteoriteSaplingBlock(final Properties props) {
        super(props);
    }

    @Override
    public VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext ctx) {
        return Shapes.empty();   // walk-through, like a vanilla sapling
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
