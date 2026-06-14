package com.stonytark.magnetization.content.fluid;

import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.registry.MagDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluid;
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
}
