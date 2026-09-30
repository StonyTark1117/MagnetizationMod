package com.stonytark.magnetization.compat;

import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

/** Uses the current assembled materials, never the shared golem entity type. */
public final class ModularGolemsCompat {
    private ModularGolemsCompat() {}

    public static boolean isMagnetizable(final Entity entity) {
        return MagConfig.modularGolemsCompatEnabled()
                && ModList.get().isLoaded("modulargolems") && Loaded.isMagnetizable(entity);
    }

    private static final class Loaded {
        private static boolean isMagnetizable(final Entity entity) {
            if (!(entity instanceof dev.xkmc.modulargolems.content.entity.common.AbstractGolemEntity<?, ?> golem)) {
                return false;
            }
            final var definitions = dev.xkmc.modulargolems.content.config.GolemMaterialConfig.get();
            for (final var material : golem.getMaterials()) {
                // Unknown IDs resolve to EMPTY upstream. Ingredient membership uses
                // the same data-driven metal policy as ordinary magnetic items.
                for (final var ingredient : definitions.getCraftIngredient(material.id()).getItems()) {
                    if (FerromagneticCompat.isFerromagnetic(ingredient)) return true;
                }
            }
            return false;
        }
    }
}
