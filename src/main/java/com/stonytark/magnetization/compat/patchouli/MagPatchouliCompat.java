package com.stonytark.magnetization.compat.patchouli;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.util.Map;

/** Keeps Patchouli optional while allowing its generated manual to be disabled. */
public final class MagPatchouliCompat {
    private static final ResourceLocation FIELD_MANUAL = Magnetization.id("field_manual");

    private MagPatchouliCompat() {}

    /**
     * Patchouli discovers books before NeoForge finishes loading COMMON config.
     * Remove our discovered book once that config is available when the master
     * is off. Recipes and automatic gifting are gated separately.
     */
    public static void applyMasterToggle() {
        if (!ModList.get().isLoaded("patchouli") || MagConfig.patchouliCompatEnabled()) return;
        try {
            final Class<?> registryType = Class.forName("vazkii.patchouli.common.book.BookRegistry");
            final Object registry = registryType.getField("INSTANCE").get(null);
            final Field booksField = registryType.getField("books");
            final Object booksValue = booksField.get(registry);
            if (booksValue instanceof Map<?, ?> books) {
                books.remove(FIELD_MANUAL);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Soft dependency: an incompatible Patchouli build should not stop startup.
        }
    }
}
