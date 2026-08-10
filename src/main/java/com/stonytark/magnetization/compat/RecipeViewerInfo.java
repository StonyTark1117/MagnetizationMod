package com.stonytark.magnetization.compat;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Shared information-page catalog for JEI, REI, and EMI.
 *
 * <p>Keeping the topics here makes the three optional recipe viewers expose
 * the same discovery help. Stack factories stay lazy because this class is
 * also inspected by unit tests before NeoForge has populated the item
 * registry.</p>
 */
public final class RecipeViewerInfo {

    private static final String KEY_PREFIX = "recipe_viewer.magnetization.";

    private static final List<Topic> TOPICS = List.of(
            topic("ferromagnetic_items", 3, FerromagneticInfoHelper::stacks),
            topic("excavator_targets", 3, FerromagneticInfoHelper::blockStacks),
            topic("magnetite", 3, () -> stacks(
                    MagItems.RAW_MAGNETITE.get(), MagItems.MAGNETITE_ORE.get(),
                    MagItems.DEEPSLATE_MAGNETITE_ORE.get(), MagItems.MAGNETITE_INGOT.get())),
            topic("iron_oxide_ores", 5, () -> stacks(
                    MagItems.RAW_MAGHEMITE.get(), MagItems.MAGHEMITE_ORE.get(), MagItems.DEEPSLATE_MAGHEMITE_ORE.get(),
                    MagItems.RAW_PYRRHOTITE.get(), MagItems.PYRRHOTITE_ORE.get(), MagItems.DEEPSLATE_PYRRHOTITE_ORE.get(),
                    MagItems.RAW_HEMATITE.get(), MagItems.HEMATITE_ORE.get(), MagItems.DEEPSLATE_HEMATITE_ORE.get(),
                    MagItems.RAW_TITANOMAGNETITE.get(), MagItems.TITANOMAGNETITE_ORE.get(),
                    MagItems.DEEPSLATE_TITANOMAGNETITE_ORE.get())),
            topic("lithium", 3, () -> stacks(
                    MagItems.RAW_LITHIUM.get(), MagItems.LITHIUM_ORE.get(),
                    MagItems.DEEPSLATE_LITHIUM_ORE.get(), MagItems.LITHIUM.get(),
                    MagItems.LIQUID_LITHIUM_BUCKET.get())),
            topic("gallium", 4, () -> stacks(
                    MagItems.RAW_GALLIUM.get(), MagItems.GALLIUM_INGOT.get(),
                    MagItems.SOLID_GALLIUM.get(), MagItems.GALLIUM_BUCKET.get(),
                    MagItems.MIXED_GALLIUM_BUCKET.get())),
            topic("fusion_fuels", 4, () -> stacks(
                    MagItems.HYDROGEN_BUCKET.get(), MagItems.DEUTERIUM_OXIDE_BUCKET.get(),
                    MagItems.TRITIUM_BUCKET.get(), MagItems.HELIUM_3_BUCKET.get(),
                    MagItems.HELIUM_3_CRYSTAL.get(), MagItems.DEUTERIUM_CELL.get(),
                    MagItems.TRITIUM_CELL.get(), MagItems.HELIUM_3_CELL.get())),
            topic("electrolyzer", 3, () -> stacks(MagItems.ELECTROLYZER.get())),
            topic("noble_gases", 4, () -> stacks(
                    MagItems.HELIUM_BUCKET.get(), MagItems.NEON_BUCKET.get(), MagItems.ARGON_BUCKET.get(),
                    MagItems.KRYPTON_BUCKET.get(), MagItems.XENON_BUCKET.get(), MagItems.RADON_BUCKET.get())),
            topic("air_separator", 4, () -> stacks(MagItems.AIR_SEPARATOR.get(), MagItems.ISOTOPE_SEPARATION_MODULE.get())),
            topic("ion_thruster", 4, () -> stacks(MagItems.ION_THRUSTER.get())),
            topic("dipole_electromagnet", 3, () -> stacks(MagItems.DIPOLE_ELECTROMAGNET.get())),
            topic("structural_inducer", 3, () -> stacks(MagItems.STRUCTURAL_INDUCER.get())),
            topic("mhd_jet", 3, () -> stacks(MagItems.MHD_JET.get())),
            topic("fusion_thruster", 4, () -> stacks(MagItems.FUSION_THRUSTER.get())),
            topic("tokamak", 4, () -> stacks(MagItems.TOKAMAK_CONTROLLER.get(), MagItems.TOKAMAK_COIL.get())),
            topic("railgun", 4, () -> stacks(MagItems.RAILGUN_EMITTER.get(), MagItems.RAILGUN_REMOTE.get()))
    );

    private RecipeViewerInfo() {}

    public static List<Topic> topics() {
        return TOPICS;
    }

    private static Topic topic(final String path, final int lineCount,
                               final Supplier<List<ItemStack>> stackFactory) {
        final List<String> descriptions = new ArrayList<>(lineCount);
        for (int line = 1; line <= lineCount; line++) {
            descriptions.add(KEY_PREFIX + path + ".description." + line);
        }
        return new Topic(
                ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "info/" + path),
                KEY_PREFIX + path + ".name",
                List.copyOf(descriptions),
                stackFactory);
    }

    private static List<ItemStack> stacks(final net.minecraft.world.level.ItemLike... items) {
        final List<ItemStack> stacks = new ArrayList<>(items.length);
        for (final var item : items) stacks.add(new ItemStack(item));
        return List.copyOf(stacks);
    }

    public record Topic(ResourceLocation id, String titleKey, List<String> descriptionKeys,
                        Supplier<List<ItemStack>> stackFactory) {

        public List<ItemStack> resolveStacks() {
            return stackFactory.get();
        }

        public Component title() {
            return Component.translatable(titleKey);
        }

        public List<Component> descriptions() {
            return descriptionKeys.stream()
                    .<Component>map(key -> Component.translatable(key).withStyle(ChatFormatting.GRAY))
                    .toList();
        }
    }
}
