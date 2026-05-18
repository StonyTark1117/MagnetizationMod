package com.stonytark.magnetization.content.titanomagnetite;

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
 * Storage block for titanomagnetite (Fe3O4·Fe2TiO4) — paleomagnetic
 * "tape loop" mineral that records the magnetic field it's exposed to and
 * re-emits the imprint after the source is removed. Real-world parallel:
 * titanomagnetite is the primary record of geomagnetic-reversal history
 * because its dipole orientation locks in as it cools.
 *
 * <p>Gameplay: place a titanomagnetite block adjacent to an active emitter.
 * The block records that emitter's field (strength, polarity, shape) into
 * its BE state. Move or destroy the source emitter — the titanomagnetite
 * continues to emit the recorded field passively. Re-expose it to a
 * different emitter to overwrite the imprint.
 *
 * <p>BE storage holds the {@code MagneticField} snapshot via NBT, so the
 * imprint survives world reload, server restart, and chunk unloading.
 */
public final class TitanomagnetiteBlock extends Block implements EntityBlock {

    public TitanomagnetiteBlock(final Properties props) {
        super(props);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new TitanomagnetiteBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type
    ) {
        if (level.isClientSide || type != MagBlockEntities.TITANOMAGNETITE.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<TitanomagnetiteBlockEntity>)
                TitanomagnetiteBlockEntity::serverTick;
    }
}
