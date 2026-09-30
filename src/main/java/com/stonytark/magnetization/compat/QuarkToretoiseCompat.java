package com.stonytark.magnetization.compat;

import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

/** Quark 4.1-485: ore type 2 is iron, 5 is copper; 0 is a harvested shell. */
public final class QuarkToretoiseCompat {
    private QuarkToretoiseCompat() {}

    public static boolean isMagnetizable(final Entity entity) {
        return MagConfig.quarkToretoiseCompatEnabled()
                && ModList.get().isLoaded("quark") && Loaded.isMagnetizable(entity);
    }

    private static final class Loaded {
        private static boolean isMagnetizable(final Entity entity) {
            if (!(entity instanceof org.violetmoon.quark.content.mobs.entity.Toretoise toretoise)) return false;
            return switch (toretoise.getOreType()) {
                case 2 -> FerromagneticCompat.isFerromagnetic(Items.RAW_IRON.getDefaultInstance());
                case 5 -> FerromagneticCompat.isFerromagnetic(Items.RAW_COPPER.getDefaultInstance());
                default -> false;
            };
        }
    }
}
