package com.stonytark.magnetization.content.gas;

import com.mojang.serialization.MapCodec;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

/** Internal, non-item cloud block used for virtual gases from optional mods. */
public final class ProxyGasCloudBlock extends BaseEntityBlock {
    public static final BooleanProperty EXCITED = BooleanProperty.create("excited");
    public static final MapCodec<ProxyGasCloudBlock> CODEC = simpleCodec(ProxyGasCloudBlock::new);

    public ProxyGasCloudBlock(final Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(EXCITED, false));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public RenderShape getRenderShape(final BlockState state) { return RenderShape.MODEL; }
    @Override protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(EXCITED);
    }
    @Override public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new ProxyGasCloudBlockEntity(pos, state);
    }
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide || type != MagBlockEntities.PROXY_GAS_CLOUD.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<ProxyGasCloudBlockEntity>) ProxyGasCloudBlockEntity::serverTick;
    }

    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level,
                                              final BlockPos pos, final Player player, final InteractionHand hand,
                                              final BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ProxyGasCloudBlockEntity cloud) || !cloud.isSource()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return FluidUtil.interactWithFluidHandler(player, hand, cloud.fluidHandler())
                ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
