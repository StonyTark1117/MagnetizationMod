package com.stonytark.magnetization.content.fluid;

import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagFluids;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ferrofluid bucket. Identical to a vanilla bucket of ferrofluid until the
 * player magnetizes it in the electromagnet GUI — which stamps a
 * {@link MagDataComponents#ARMOR_POLARITY} onto the stack. A magnetized bucket
 * reads as "Magnetized Ferrofluid Bucket", shows its pole + an enchant glint,
 * and (stage 2) places field-emitting magnetized ferrofluid instead of the
 * inert kind. Plain ferrofluid stays field-immune so the Anomaly biome's pools
 * don't drain themselves away.
 */
public final class FerrofluidBucketItem extends BucketItem {

    public FerrofluidBucketItem(final Fluid fluid, final Properties props) {
        super(fluid, props);
    }

    /** The stamped pole, or null if this bucket hasn't been magnetized. */
    public static @Nullable MagneticPolarity polarityOf(final ItemStack stack) {
        return stack.get(MagDataComponents.ARMOR_POLARITY.get());
    }

    public static boolean isMagnetized(final ItemStack stack) {
        return polarityOf(stack) != null;
    }

    @Override
    public Component getName(final ItemStack stack) {
        if (isMagnetized(stack)) {
            return Component.translatable("item.magnetization.ferrofluid_bucket.magnetized");
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext ctx,
                                final List<Component> tooltip, final TooltipFlag flag) {
        final MagneticPolarity pole = polarityOf(stack);
        if (pole != null) {
            tooltip.add(Component.translatable("tooltip.magnetization.ferrofluid_polarity",
                            Component.translatable("tooltip.magnetization.polarity." + pole.name().toLowerCase()))
                    .withStyle(ChatFormatting.AQUA));
        }
        super.appendHoverText(stack, ctx, tooltip, flag);
    }

    @Override
    public boolean isFoil(final ItemStack stack) {
        return isMagnetized(stack) || super.isFoil(stack);
    }

    /**
     * A magnetized bucket pours the field-emitting {@link MagnetizedFerrofluidBlock}
     * carrying its stamped pole; an un-magnetized bucket behaves like vanilla
     * (places inert ferrofluid).
     */
    @Override
    public boolean emptyContents(final @Nullable Player player, final Level level, final BlockPos pos,
                                 final @Nullable BlockHitResult result, final @Nullable ItemStack container) {
        final MagneticPolarity pole = container == null ? null : polarityOf(container);
        if (pole == null) {
            return super.emptyContents(player, level, pos, result, container);
        }
        final BlockState existing = level.getBlockState(pos);
        if (!existing.isAir() && !existing.canBeReplaced(MagFluids.MAGNETIZED_FERROFLUID.get())) {
            // Not an open cell — fall back to vanilla resolution for this click.
            return super.emptyContents(player, level, pos, result, container);
        }
        if (!level.isClientSide) {
            final BlockState placed = MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get().defaultBlockState()
                    .setValue(MagnetizedFerrofluidBlock.POLARITY, pole);
            level.setBlock(pos, placed, Block.UPDATE_ALL);
        }
        level.playSound(player, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        return true;
    }
}
