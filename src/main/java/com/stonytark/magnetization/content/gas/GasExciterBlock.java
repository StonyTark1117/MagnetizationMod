package com.stonytark.magnetization.content.gas;

import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class GasExciterBlock extends BaseEntityBlock {
    public static final com.mojang.serialization.MapCodec<GasExciterBlock> CODEC = simpleCodec(GasExciterBlock::new);
    public GasExciterBlock(final Properties properties) { super(properties); }

    @Override protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public RenderShape getRenderShape(final BlockState state) { return RenderShape.MODEL; }

    @Override public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new GasExciterBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide || type != MagBlockEntities.GAS_EXCITER.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<GasExciterBlockEntity>) GasExciterBlockEntity::serverTick;
    }
}
