package com.stonytark.magnetization.content;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes soft-disabled content truly uncraftable. Hiding a disabled block/item from
 * the creative tab isn't enough — its crafting recipe would still let players make
 * an inert block in survival. So when the recipe manager (re)loads we strip every
 * recipe whose result is a disabled magnetization item
 * ({@link MagConfig#isItemDisabled}/{@link MagConfig#isBlockDisabled}, which also
 * covers the induction pad's dedicated toggle).
 *
 * <p>Runs at {@link ServerStartedEvent} (before any client connects, so the synced
 * recipe list — and JEI — is already clean) and on every {@link OnDatapackSyncEvent}
 * (covers {@code /reload} and joins). Stripping is idempotent; a {@code /reload}
 * after re-enabling content rebuilds the full datapack recipe set and restores it.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class DisabledContentRecipes {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(Magnetization.MOD_ID);

    private DisabledContentRecipes() {}

    @SubscribeEvent
    public static void onServerStarted(final ServerStartedEvent event) {
        stripDisabled(event.getServer());
    }

    @SubscribeEvent
    public static void onDatapackSync(final OnDatapackSyncEvent event) {
        stripDisabled(event.getPlayerList().getServer());
    }

    private static void stripDisabled(final MinecraftServer server) {
        if (server == null) return;
        final RecipeManager recipes = server.getRecipeManager();
        final var registries = server.registryAccess();
        final List<RecipeHolder<?>> kept = new ArrayList<>();
        int removed = 0;
        for (final RecipeHolder<?> holder : recipes.getRecipes()) {
            ItemStack result;
            try {
                result = holder.value().getResultItem(registries);
            } catch (final Throwable t) {
                result = ItemStack.EMPTY; // dynamic/special recipes with no fixed output — always keep
            }
            if (!result.isEmpty()) {
                final ResourceLocation id = BuiltInRegistries.ITEM.getKey(result.getItem());
                if (Magnetization.MOD_ID.equals(id.getNamespace())
                        && (MagConfig.isItemDisabled(id.getPath()) || MagConfig.isBlockDisabled(id.getPath()))) {
                    removed++;
                    continue;
                }
            }
            kept.add(holder);
        }
        if (removed > 0) {
            recipes.replaceRecipes(kept);
            LOGGER.info("Stripped {} recipe(s) producing disabled magnetization content.", removed);
        }
    }
}
