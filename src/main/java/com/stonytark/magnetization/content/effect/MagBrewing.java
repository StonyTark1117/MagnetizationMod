package com.stonytark.magnetization.content.effect;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.registry.MagEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

/**
 * Brewing transformation: awkward potion + lodestone → magnetized potion.
 * Vanilla potion-brewing UI handles the rest. NeoForge auto-derives splash,
 * lingering, and tipped-arrow variants from the base potion registration.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MagBrewing {

    private MagBrewing() {}

    @SubscribeEvent
    public static void register(final RegisterBrewingRecipesEvent event) {
        event.getBuilder().addMix(Potions.AWKWARD, Items.LODESTONE, MagEffects.MAGNETIZED_POTION);
    }
}
