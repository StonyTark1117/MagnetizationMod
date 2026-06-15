package com.stonytark.magnetization.content.fluid;

import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FlowingFluid;
import org.jetbrains.annotations.Nullable;

/**
 * Magnetized ferrofluid — a fully flowing fluid (it pours and spreads like the
 * plain kind) that a player has charged with a polarity, so it acts as a weak
 * magnetic field source. The pole is held in the {@code POLARITY} blockstate
 * (set at placement from the bucket's stamp). Only <em>source</em> blocks emit
 * a field; they register into {@link MagnetizedFerrofluidRegistry} so
 * {@link MagnetizedFerrofluidFieldHandler} can drive them without a block
 * entity. Plain (unmagnetized) ferrofluid emits nothing and is field-immune —
 * which is why the Anomaly biome's pools sit still instead of draining away.
 */
public final class MagnetizedFerrofluidBlock extends LiquidBlock implements FluidRedstone.Conductor {

    public static final EnumProperty<MagneticPolarity> POLARITY =
            EnumProperty.create("polarity", MagneticPolarity.class, MagneticPolarity.NORTH, MagneticPolarity.SOUTH);

    /** Shared overlay line for magnetized ferrofluid's pole (WTHIT/Jade/TOP), or
     *  {@code null} for plain ferrofluid / any non-magnetized block. */
    public static @Nullable net.minecraft.network.chat.Component polarityTooltip(final BlockState state) {
        if (!state.is(MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get()) || !state.hasProperty(POLARITY)) return null;
        final MagneticPolarity pole = state.getValue(POLARITY);
        final net.minecraft.ChatFormatting colour = pole == MagneticPolarity.NORTH
                ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.AQUA;
        return net.minecraft.network.chat.Component.translatable("tooltip.magnetization.ferrofluid_magnetized",
                        net.minecraft.network.chat.Component.translatable(
                                        "tooltip.magnetization.polarity." + pole.name().toLowerCase())
                                .withStyle(colour))
                .withStyle(net.minecraft.ChatFormatting.GRAY);
    }

    /** Ticks between mixing-spread steps — keeps the conversion gradual + cheap. */
    private static final int MIX_DELAY = 5;

    public MagnetizedFerrofluidBlock(final FlowingFluid fluid, final Properties props) {
        super(fluid, props);
        registerDefaultState(defaultBlockState()
                .setValue(POLARITY, MagneticPolarity.NORTH)
                .setValue(FluidRedstone.POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POLARITY);
        builder.add(FluidRedstone.POWER);
    }

    @Override
    public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
        super.animateTick(state, level, pos, random);
        FluidRedstone.spawnSignalParticles(state, level, pos, random);
    }

    @Override
    protected boolean isSignalSource(final BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(final BlockState state, final net.minecraft.world.level.BlockGetter level,
                            final BlockPos pos, final Direction direction) {
        return FluidRedstone.signal(state);
    }

    @Override
    public boolean canConnectRedstone(final BlockState state, final net.minecraft.world.level.BlockGetter level,
                                      final BlockPos pos, final @Nullable Direction direction) {
        return true;
    }

    @Override
    protected void onPlace(final BlockState state, final Level level, final BlockPos pos,
                           final BlockState oldState, final boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        // Track source blocks only — flowing tongues don't emit. The field
        // handler prunes any entry that stops being a magnetized source, so
        // a flow that drains this cell heals the registry on its own.
        if (!level.isClientSide && state.getFluidState().isSource()) {
            MagnetizedFerrofluidRegistry.add(level, pos, state.getValue(POLARITY));
        }
        if (!level.isClientSide) level.scheduleTick(pos, this, MIX_DELAY);
        FluidRedstone.onNeighborChanged(level, pos, this);
    }

    @Override
    protected void neighborChanged(final BlockState state, final Level level, final BlockPos pos,
                                   final Block neighborBlock, final BlockPos neighborPos, final boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, MIX_DELAY);
        FluidRedstone.onNeighborChanged(level, pos, this);
    }

    /**
     * Mixing rule — magnetization takes priority: any plain ferrofluid touching
     * this cell is converted to magnetized ferrofluid carrying this cell's pole
     * (keeping its fluid level). Each freshly-converted cell schedules its own
     * pass via {@code onPlace}, so the charge spreads through a connected body.
     */
    @Override
    protected void tick(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random) {
        super.tick(state, level, pos, random);
        final MagneticPolarity pole = state.getValue(POLARITY);
        for (final Direction dir : Direction.values()) {
            final BlockPos np = pos.relative(dir);
            final BlockState ns = level.getBlockState(np);
            // Convert adjacent plain ferrofluid to this pole, AND propagate this
            // pole onto adjacent magnetized FLOWING cells that carry a different
            // (default) pole — so auxiliary/flowing cells of a body read the right
            // pole instead of being stuck at NORTH. Magnetized SOURCE cells
            // (LEVEL 0) are left alone, so two opposite-pole sources don't ping-pong.
            final boolean plainCell = ns.is(MagBlocks.FERROFLUID_BLOCK.get());
            final boolean flowingMagDiff = ns.is(MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get())
                    && ns.getValue(LEVEL) != 0 && ns.getValue(POLARITY) != pole;
            if (plainCell || flowingMagDiff) {
                level.setBlock(np, defaultBlockState()
                        .setValue(LEVEL, ns.getValue(LEVEL))
                        .setValue(POLARITY, pole), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public ItemStack pickupBlock(final @Nullable Player player, final LevelAccessor level,
                                 final BlockPos pos, final BlockState state) {
        final MagneticPolarity pole = state.getValue(POLARITY);
        final ItemStack bucket = super.pickupBlock(player, level, pos, state);
        // The fluid's bucket is the plain ferrofluid bucket; re-stamp the pole so
        // scooping magnetized ferrofluid keeps you a magnetized bucket.
        if (!bucket.isEmpty()) bucket.set(MagDataComponents.ARMOR_POLARITY.get(), pole);
        return bucket;
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            MagnetizedFerrofluidRegistry.remove(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            level.updateNeighborsAt(pos, this);
        }
    }
}
