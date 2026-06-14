package com.stonytark.magnetization.content.dampener;

import com.stonytark.magnetization.Magnetization;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

/**
 * Server-side half of the Magnetoresistive Dampening Boots: any fall that would
 * deal damage is fully negated while the boots are worn, at the cost of one
 * point of boot durability per save. (The G-Force Cushion block handles the
 * block-based version via {@code fallOn}.)
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class FallSaveHandler {

    private static final float MIN_SAVE_DISTANCE = 3.5f; // ignore harmless little drops

    private FallSaveHandler() {}

    @SubscribeEvent
    public static void onFall(final LivingFallEvent event) {
        if (event.getDistance() < MIN_SAVE_DISTANCE) return;
        final LivingEntity living = event.getEntity();
        final ItemStack boots = living.getItemBySlot(EquipmentSlot.FEET);
        if (!(boots.getItem() instanceof MagnetoresistiveBootsItem)) return;

        event.setDamageMultiplier(0.0f); // magnetoresistive arrest — no fall damage
        if (!living.level().isClientSide) {
            boots.hurtAndBreak(1, living, EquipmentSlot.FEET);
        }
    }
}
