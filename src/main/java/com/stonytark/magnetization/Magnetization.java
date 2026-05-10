package com.stonytark.magnetization;

import com.stonytark.magnetization.command.MagCommands;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagArmorMaterials;
import com.stonytark.magnetization.registry.MagBiomeModifiers;
import com.stonytark.magnetization.registry.MagBlockEntities;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagCreativeTab;
import com.stonytark.magnetization.registry.MagEffects;
import com.stonytark.magnetization.registry.MagItems;
import com.stonytark.magnetization.registry.MagParticles;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Magnetization.MOD_ID)
public final class Magnetization {

    public static final String MOD_ID = "magnetization";

    public Magnetization(final IEventBus modBus, final ModContainer modContainer) {
        MagBlocks.REGISTER.register(modBus);
        // ArmorMaterials must register before items that reference them.
        MagArmorMaterials.REGISTER.register(modBus);
        MagItems.REGISTER.register(modBus);
        MagBlockEntities.REGISTER.register(modBus);
        MagCreativeTab.REGISTER.register(modBus);
        MagEffects.EFFECTS.register(modBus);
        MagEffects.POTIONS.register(modBus);
        MagParticles.REGISTER.register(modBus);
        MagBiomeModifiers.REGISTER.register(modBus);

        modContainer.registerConfig(ModConfig.Type.SERVER, MagConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(MagCommands::onRegister);
    }

    public static ResourceLocation id(final String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
