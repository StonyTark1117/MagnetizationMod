package com.stonytark.magnetization.content.shaft;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** A mechanical shaft with a wireless connection; never a magnetic force emitter. */
public final class MagneticShaftBlock extends RotatedPillarKineticBlock implements IBE<MagneticShaftBlockEntity> {
    public static final MapCodec<MagneticShaftBlock> CODEC = simpleCodec(MagneticShaftBlock::new);
    private final MagneticShaftMaterial material;
    public MagneticShaftBlock(Properties properties) { this(properties, MagneticShaftMaterial.FERROMAGNETIC); }
    public MagneticShaftBlock(Properties properties, MagneticShaftMaterial material) { super(properties); this.material = material; }
    public MagneticShaftMaterial material() { return material; }
    @Override protected MapCodec<? extends RotatedPillarKineticBlock> codec() { return CODEC; }
    @Override public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(AXIS);
    }
    @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
            BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> box(0,3,3,16,13,13);
            case Y -> box(3,0,3,13,16,13);
            case Z -> box(3,3,0,13,13,16);
        };
    }
    @Override protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED;
    }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(AXIS); }
    @Override public Class<MagneticShaftBlockEntity> getBlockEntityClass() { return MagneticShaftBlockEntity.class; }
    @Override public BlockEntityType<? extends MagneticShaftBlockEntity> getBlockEntityType() { return MagBlockEntities.MAGNETIC_SHAFT.get(); }
}
