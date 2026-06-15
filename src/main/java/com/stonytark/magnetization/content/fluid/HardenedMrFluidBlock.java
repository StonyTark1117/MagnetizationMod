package com.stonytark.magnetization.content.fluid;

import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;

import java.util.Collections;
import java.util.List;

/**
 * Hardened MR Fluid — the solid grey block MR (magnetorheological) fluid snaps
 * into while inside a magnetic field, letting you walk across it (temporary
 * bridges/walkways). It is NOT craftable and drops nothing; it reverts to MR
 * fluid both when it leaves the field ({@link MrFluidHardenHandler}) and when a
 * player breaks it. The {@link #SOURCE} flag records whether the cell it hardened
 * from was a fluid source, so the field-revert restores the body correctly.
 */
public final class HardenedMrFluidBlock extends Block {

    public static final BooleanProperty SOURCE = BooleanProperty.create("source");

    public HardenedMrFluidBlock(final Properties props) {
        super(props);
        registerDefaultState(defaultBlockState().setValue(SOURCE, true));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SOURCE);
    }

    /** No item drops — it turns back into fluid instead (see onRemove). */
    @Override
    protected List<net.minecraft.world.item.ItemStack> getDrops(final BlockState state, final LootParams.Builder params) {
        return Collections.emptyList();
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean isMoving) {
        // Drop our registry tracking whenever we leave this position.
        if (!level.isClientSide) HardenedMrFluidRegistry.remove(level, pos);
        super.onRemove(state, level, pos, newState, isMoving);
        // Broken by a player (replaced with air) → turn into MR fluid, not nothing.
        // The field-revert path sets MR fluid directly (newState is the fluid), so
        // it doesn't hit this branch.
        if (!level.isClientSide && newState.isAir()) {
            level.setBlock(pos, MagBlocks.MR_FLUID_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
