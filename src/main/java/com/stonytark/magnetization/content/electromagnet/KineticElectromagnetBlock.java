package com.stonytark.magnetization.content.electromagnet;

import com.stonytark.magnetization.menu.EmitterMenu;
import com.stonytark.magnetization.menu.EmitterMenuProvider;
import com.stonytark.magnetization.registry.MagBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Variant of the electromagnet that ingests Create's rotational power. Field
 * strength scales with absolute RPM. Accepts a shaft on either face of its
 * placement axis (chosen at place time from the clicked face).
 */
public final class KineticElectromagnetBlock extends KineticBlock implements IBE<KineticElectromagnetBlockEntity> {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final MapCodec<KineticElectromagnetBlock> CODEC = simpleCodec(KineticElectromagnetBlock::new);

    public KineticElectromagnetBlock(final Properties props) {
        super(props);
        registerDefaultState(getStateDefinition().any().setValue(AXIS, Direction.Axis.Y));
    }

    @Override
    protected MapCodec<? extends KineticBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AXIS);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
    }

    @Override
    public boolean hasShaftTowards(
            final LevelReader level, final BlockPos pos, final BlockState state, final Direction face
    ) {
        return face.getAxis() == state.getValue(AXIS);
    }

    @Override
    public Direction.Axis getRotationAxis(final BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    protected InteractionResult useWithoutItem(
            final BlockState state, final Level level, final BlockPos pos,
            final Player player, final BlockHitResult hit
    ) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        // Kinetic emitter: only the armor magnetize panel — strength is RPM-driven.
        final int caps = EmitterMenu.CAP_ARMOR | EmitterMenu.CAP_POLARITY;
        new EmitterMenuProvider(ContainerLevelAccess.create(level, pos), pos, caps,
                Component.translatable("block.magnetization.kinetic_electromagnet")).openFor(sp);
        return InteractionResult.CONSUME;
    }

    @Override
    public Class<KineticElectromagnetBlockEntity> getBlockEntityClass() {
        return KineticElectromagnetBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends KineticElectromagnetBlockEntity> getBlockEntityType() {
        return MagBlockEntities.KINETIC_ELECTROMAGNET.get();
    }
}
