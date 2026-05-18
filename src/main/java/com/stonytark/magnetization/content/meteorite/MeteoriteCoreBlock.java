package com.stonytark.magnetization.content.meteorite;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Meteorite core — rare worldgen block (~1 per several hundred chunks) that
 * emits a magnetic field which decays linearly over many in-game minutes
 * after it generates. Players can refresh the timer by right-clicking the
 * block with any ferromagnetic item (consumes one item from the stack and
 * resets the decay to full).
 *
 * <p>Phase A MVP: standalone block, no surrounding crater structure. The
 * gameplay loop is the decaying-field-with-refill — players who find one
 * have a window to harvest its field before it goes inert, then maintain
 * it indefinitely by feeding it.
 *
 * <p>Phase B (deferred): surround with a jigsaw crater structure of mixed
 * magnetic raw materials; add ChunkEvent.Load hook to also treat AE2's
 * meteor structures as field sources. Tracked separately.
 */
public final class MeteoriteCoreBlock extends Block implements EntityBlock {

    public MeteoriteCoreBlock(final Properties props) {
        super(props);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new MeteoriteCoreBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type
    ) {
        if (level.isClientSide || type != MagBlockEntities.METEORITE_CORE.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<MeteoriteCoreBlockEntity>)
                MeteoriteCoreBlockEntity::serverTick;
    }

    @Override
    protected ItemInteractionResult useItemOn(
            final ItemStack stack, final BlockState state, final Level level,
            final BlockPos pos, final Player player, final InteractionHand hand,
            final BlockHitResult hit
    ) {
        // Refill mechanic: any ferromagnetic item resets the decay timer to full.
        // Magnetite + family qualify via #magnetization:ferromagnetic.
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!stack.is(MagTags.FERROMAGNETIC_ITEMS)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(level.getBlockEntity(pos) instanceof MeteoriteCoreBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (be.isAtFullCharge()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        be.refill(level.getGameTime());
        if (!player.isCreative()) stack.shrink(1);
        level.playSound(null, pos, SoundEvents.LODESTONE_PLACE, SoundSource.BLOCKS, 0.7f, 1.6f);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "meteorite.magnetization.refilled")
                .withStyle(net.minecraft.ChatFormatting.AQUA), true);
        return ItemInteractionResult.sidedSuccess(false);
    }
}
