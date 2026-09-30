package com.stonytark.magnetization.compat;

import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

/** Material-aware optional bridge for Extra Golems Reborn 21.1.0.1. */
public final class ExtraGolemsRebornCompat {
    public static final String MOD_ID = "golems";
    public static final String SUPPORTED_VERSION = "21.1.0.1";

    private ExtraGolemsRebornCompat() {}

    public static boolean isMagnetizable(final Entity entity) {
        return MagConfig.extraGolemsRebornCompatEnabled()
                && ModList.get().getModContainerById(MOD_ID)
                    .filter(mod -> SUPPORTED_VERSION.equals(mod.getModInfo().getVersion().toString()))
                    .isPresent()
                && Loaded.isMagnetizable(entity);
    }

    /** Only resolved after the optional, version-specific dependency gate passes. */
    private static final class Loaded {
        private static boolean isMagnetizable(final Entity entity) {
            return entity instanceof com.mcmoddev.golems.entity.GolemBase golem
                    && golem.getGolemId().filter(id ->
                        MagConfig.extraGolemsRebornMaterials().contains(id.toString()))
                        .filter(id -> golem.registryAccess().registry(com.mcmoddev.golems.EGRegistry.Keys.GOLEM)
                                .filter(registry -> registry.containsKey(id)).isPresent()).isPresent();
        }
    }
}
