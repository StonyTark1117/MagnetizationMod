package com.stonytark.magnetization.content.item;

import com.stonytark.magnetization.content.effect.RadonExposureHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Locale;

/** Handheld gas survey instrument; its detailed reading is rendered client-side. */
public final class GasDetectorItem extends Item {
    public GasDetectorItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player,
                                                    final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            final GasDetectorScanner.Reading reading = GasDetectorScanner.nearest(level, player.blockPosition());
            final RadonExposureHandler.ExposureSnapshot exposure = RadonExposureHandler.snapshot(player);
            final Component safety = safetySummary(exposure, reading);
            if (!reading.found()) {
                player.displayClientMessage(Component.translatable("message.magnetization.gas_detector.none",
                        exposure.dose(), exposure.threshold(), safety), true);
            } else {
                player.displayClientMessage(Component.translatable(
                        "message.magnetization.gas_detector.reading",
                        new net.neoforged.neoforge.fluids.FluidStack(reading.fluid(), 1).getHoverName(),
                        Component.translatable("message.magnetization.gas_detector.status." + reading.statusKey()),
                        String.format(Locale.ROOT, "%.1f", reading.distance()),
                        exposure.dose(), exposure.threshold(), safety,
                        reading.dangerous() ? Component.translatable("message.magnetization.gas_detector.danger") : ""), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static Component safetySummary(final RadonExposureHandler.ExposureSnapshot exposure,
                                           final GasDetectorScanner.Reading reading) {
        if (!exposure.radiationEnabled()) {
            return Component.translatable("message.magnetization.gas_detector.safety.disabled");
        }
        if (exposure.exposed()) {
            return Component.translatable("message.magnetization.gas_detector.safety.exposed",
                    metres(exposure.distanceToSafety()));
        }
        if (reading.dangerous()) {
            return Component.translatable("message.magnetization.gas_detector.safety.radon_clearance",
                    metres(reading.distance()));
        }
        if (exposure.dose() > 0) {
            return Component.translatable("message.magnetization.gas_detector.safety.recovering",
                    exposure.recoveryPerTick());
        }
        return Component.translatable("message.magnetization.gas_detector.safety.clear");
    }

    private static String metres(final double distance) {
        return String.format(Locale.ROOT, "%.1fm", Math.max(0.1d, distance));
    }
}
