package com.stonytark.magnetization.content;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Give the Magnetization field manual (a Patchouli guide book) to each player on
 * their first login to a world or server. Tracks per-player via a flag stored
 * in {@link ServerPlayer#getPersistentData()} (the {@code PlayerPersistentData}
 * NBT, which survives death and reconnect but is per-world).
 *
 * <p>Soft-fails when Patchouli isn't loaded — the manual is a Patchouli book,
 * so without that mod there's nothing meaningful to give. Players who decline
 * the auto-give (or join before Patchouli is installed) can still craft the
 * manual via the cheap recipes in {@code data/magnetization/recipe/}.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class FieldManualGiver {

    /** Tag on the player's persistent NBT marking that the manual has been
     *  given. Persistent across deaths and logout/login; cleared only by
     *  manual NBT editing. */
    private static final String GIVEN_FLAG = "magnetization:field_manual_given";

    private FieldManualGiver() {}

    @SubscribeEvent
    public static void onPlayerLogin(final PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player instanceof FakePlayer) return;
        if (!autoGiveEnabled()) return;
        if (!ModList.get().isLoaded("patchouli")) return;

        final CompoundTag persistent = player.getPersistentData();
        final CompoundTag root = persistent.contains(ServerPlayer.PERSISTED_NBT_TAG)
                ? persistent.getCompound(ServerPlayer.PERSISTED_NBT_TAG)
                : new CompoundTag();
        if (root.getBoolean(GIVEN_FLAG)) return;

        final ItemStack manual = buildFieldManual();
        if (manual.isEmpty()) return;
        // Try to add to inventory first; if full, drop at the player's feet so
        // the gift isn't silently swallowed.
        if (!player.getInventory().add(manual)) {
            player.drop(manual, false);
        }

        root.putBoolean(GIVEN_FLAG, true);
        persistent.put(ServerPlayer.PERSISTED_NBT_TAG, root);
    }

    /** Construct {@code patchouli:guide_book} with the
     *  {@code patchouli:book = magnetization:field_manual} data component so it
     *  opens to our book. Resolves the item + component types via the registry
     *  to keep the compile-time dependency on Patchouli soft. */
    private static ItemStack buildFieldManual() {
        final var itemReg = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        final var guideBook = itemReg.getOptional(ResourceLocation.fromNamespaceAndPath("patchouli", "guide_book"));
        if (guideBook.isEmpty()) return ItemStack.EMPTY;

        final var componentReg = net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE;
        @SuppressWarnings("unchecked")
        final var bookComponent = (net.minecraft.core.component.DataComponentType<ResourceLocation>)
                componentReg.get(ResourceLocation.fromNamespaceAndPath("patchouli", "book"));
        if (bookComponent == null) return ItemStack.EMPTY;

        final ItemStack stack = new ItemStack(guideBook.get());
        try {
            stack.set(bookComponent, ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "field_manual"));
        } catch (final ClassCastException cce) {
            // Patchouli changed the component type on us — bail rather than
            // hand the player a vanilla book that opens to nothing.
            return ItemStack.EMPTY;
        }
        return stack;
    }

    private static boolean autoGiveEnabled() {
        try { return MagConfig.FIELD_MANUAL_AUTO_GIVE.get(); }
        catch (final Throwable t) { return true; }
    }
}
