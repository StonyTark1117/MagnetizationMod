package com.stonytark.magnetization.content;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds a "How to use" line to each addon item's hover tooltip — a stand-in for
 * Ponder. Strings live in {@code en_us.json} under {@code tooltip.magnetization.<key>}.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MagItemTooltips {

    private static Map<Item, String> map;

    private MagItemTooltips() {}

    private static Map<Item, String> build() {
        final Map<Item, String> m = new IdentityHashMap<>();
        m.put(MagItems.ELECTROMAGNET.get(),         "tooltip.magnetization.electromagnet.use");
        m.put(MagItems.KINETIC_ELECTROMAGNET.get(), "tooltip.magnetization.kinetic_electromagnet.use");
        m.put(MagItems.MAGNETIC_ANCHOR.get(),       "tooltip.magnetization.magnetic_anchor.use");
        m.put(MagItems.REPULSOR_COIL.get(),         "tooltip.magnetization.repulsor_coil.use");
        m.put(MagItems.TRACTOR_BEAM.get(),          "tooltip.magnetization.tractor_beam.use");
        m.put(MagItems.MAGNETIC_EXCAVATOR.get(),    "tooltip.magnetization.magnetic_excavator.use");
        m.put(MagItems.LODESTONE_CORE.get(),        "tooltip.magnetization.lodestone_core.use");
        m.put(MagItems.MAGNETIC_SWITCH.get(),       "tooltip.magnetization.magnetic_switch.use");
        m.put(MagItems.PERMANENT_MAGNET.get(),      "tooltip.magnetization.permanent_magnet.use");
        m.put(MagItems.TEMPORARY_MAGNET.get(),      "tooltip.magnetization.temporary_magnet.use");
        m.put(MagItems.POLARITY_INVERTER.get(),     "tooltip.magnetization.polarity_inverter.use");
        m.put(MagItems.FERROMAGNETIC_INGOT.get(),   "tooltip.magnetization.ferromagnetic_ingot.use");
        m.put(MagItems.MAGNETIC_PLATE.get(),        "tooltip.magnetization.magnetic_plate.use");
        m.put(MagItems.FIELD_COMPASS.get(),         "tooltip.magnetization.field_compass.use");
        m.put(MagItems.MAGNETIC_GRAPPLE.get(),      "tooltip.magnetization.magnetic_grapple.use");
        m.put(MagItems.MAGNETITE_INGOT.get(),       "tooltip.magnetization.magnetite_ingot.use");
        m.put(MagItems.RAW_MAGNETITE.get(),         "tooltip.magnetization.raw_magnetite.use");
        m.put(MagItems.MAGNETITE_ORE.get(),         "tooltip.magnetization.magnetite_ore.use");
        m.put(MagItems.DEEPSLATE_MAGNETITE_ORE.get(), "tooltip.magnetization.magnetite_ore.use");
        m.put(MagItems.MAGNETITE_BLOCK.get(),       "tooltip.magnetization.magnetite_block.use");
        m.put(MagItems.RAW_MAGNETITE_BLOCK.get(),   "tooltip.magnetization.raw_magnetite_block.use");

        // Iron-oxide family ores + storage blocks — all share the same
        // "smelt → ingot, mineable, magnetic" semantics described by the
        // family.use key, so they all point at it.
        for (final var b : new Item[]{
                MagItems.MAGHEMITE_ORE.get(), MagItems.DEEPSLATE_MAGHEMITE_ORE.get(),
                MagItems.PYRRHOTITE_ORE.get(), MagItems.DEEPSLATE_PYRRHOTITE_ORE.get(),
                MagItems.HEMATITE_ORE.get(), MagItems.DEEPSLATE_HEMATITE_ORE.get(),
                MagItems.TITANOMAGNETITE_ORE.get(), MagItems.DEEPSLATE_TITANOMAGNETITE_ORE.get(),
        }) m.put(b, "tooltip.magnetization.iron_oxide_ore.use");
        for (final var b : new Item[]{
                MagItems.MAGHEMITE_BLOCK.get(), MagItems.PYRRHOTITE_BLOCK.get(),
                MagItems.HEMATITE_BLOCK.get(), MagItems.TITANOMAGNETITE_BLOCK.get(),
                MagItems.RAW_MAGHEMITE_BLOCK.get(), MagItems.RAW_PYRRHOTITE_BLOCK.get(),
                MagItems.RAW_HEMATITE_BLOCK.get(), MagItems.RAW_TITANOMAGNETITE_BLOCK.get(),
        }) m.put(b, "tooltip.magnetization.iron_oxide_storage.use");
        for (final var i : new Item[]{
                MagItems.RAW_MAGHEMITE.get(), MagItems.RAW_PYRRHOTITE.get(),
                MagItems.RAW_HEMATITE.get(), MagItems.RAW_TITANOMAGNETITE.get(),
                MagItems.MAGHEMITE_INGOT.get(), MagItems.PYRRHOTITE_INGOT.get(),
                MagItems.HEMATITE_INGOT.get(), MagItems.TITANOMAGNETITE_INGOT.get(),
        }) m.put(i, "tooltip.magnetization.iron_oxide_ingot.use");

        // Per-tier tool/armor sets — share one "magnetize in electromagnet"
        // tooltip line. The per-category signature ability is appended
        // separately via signatureFor() once the stack carries ARMOR_POLARITY.
        for (final var t : new Item[]{
                MagItems.MAGHEMITE_SWORD.get(), MagItems.MAGHEMITE_PICKAXE.get(),
                MagItems.MAGHEMITE_AXE.get(), MagItems.MAGHEMITE_SHOVEL.get(),
                MagItems.MAGHEMITE_HOE.get(),
                MagItems.MAGNETITE_SWORD.get(), MagItems.MAGNETITE_PICKAXE.get(),
                MagItems.MAGNETITE_AXE.get(), MagItems.MAGNETITE_SHOVEL.get(),
                MagItems.MAGNETITE_HOE.get(),
                MagItems.FERROMAGNETIC_SWORD.get(), MagItems.FERROMAGNETIC_PICKAXE.get(),
                MagItems.FERROMAGNETIC_AXE.get(), MagItems.FERROMAGNETIC_SHOVEL.get(),
                MagItems.FERROMAGNETIC_HOE.get(),
        }) m.put(t, "tooltip.magnetization.metal_tool.use");
        for (final var a : new Item[]{
                MagItems.MAGHEMITE_HELMET.get(), MagItems.MAGHEMITE_CHESTPLATE.get(),
                MagItems.MAGHEMITE_LEGGINGS.get(), MagItems.MAGHEMITE_BOOTS.get(),
                MagItems.MAGNETITE_HELMET.get(), MagItems.MAGNETITE_CHESTPLATE.get(),
                MagItems.MAGNETITE_LEGGINGS.get(), MagItems.MAGNETITE_BOOTS.get(),
                MagItems.FERROMAGNETIC_HELMET.get(), MagItems.FERROMAGNETIC_CHESTPLATE.get(),
                MagItems.FERROMAGNETIC_LEGGINGS.get(), MagItems.FERROMAGNETIC_BOOTS.get(),
        }) m.put(a, "tooltip.magnetization.metal_armor.use");
        for (final var h : new Item[]{
                MagItems.MAGHEMITE_HORSE_ARMOR.get(),
                MagItems.MAGNETITE_HORSE_ARMOR.get(),
                MagItems.FERROMAGNETIC_HORSE_ARMOR.get(),
        }) m.put(h, "tooltip.magnetization.horse_armor.use");

        m.put(MagItems.MAGNETIC_ELYTRA.get(),     "tooltip.magnetization.magnetic_elytra.use");
        m.put(MagItems.REPULSOR_GUN.get(),        "tooltip.magnetization.repulsor_gun.use");
        m.put(MagItems.METEORITE_FRAGMENT.get(),  "tooltip.magnetization.meteorite_fragment.use");
        m.put(MagItems.METEORITE_CORE.get(),      "tooltip.magnetization.meteorite_core.use");
        m.put(MagItems.METEORITE_SAPLING.get(),   "tooltip.magnetization.meteorite_sapling.use");

        return m;
    }

    @SubscribeEvent
    public static void onTooltip(final ItemTooltipEvent event) {
        if (map == null) {
            // Build lazily — items aren't resolved at class-init time on the mod bus.
            try {
                map = build();
            } catch (final Throwable t) {
                // Item registry must be intact at tooltip time; if build() fails,
                // a registration bug is the likely cause. Surface it once instead
                // of silently dropping every tooltip going forward.
                org.slf4j.LoggerFactory.getLogger("magnetization/Tooltips")
                        .warn("Tooltip key map build failed; tooltips will be skipped", t);
                map = java.util.Map.of(); // poison empty map so we don't retry every call
                return;
            }
        }
        final ItemStack stack = event.getItemStack();
        final List<Component> lines = event.getToolTip();
        final String key = map.get(stack.getItem());
        if (key != null) {
            lines.add(Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY));
        }
        // Magnetized armor: show its polarity stamp regardless of which item it is —
        // any tagged metal armor can be magnetized, including iron, gold, netherite, etc.
        final MagneticPolarity pol = stack.get(MagDataComponents.ARMOR_POLARITY.get());
        if (pol != null && pol != MagneticPolarity.NONE) {
            // Physical-convention colour code (NORTH=red, SOUTH=blue) so the
            // armor stamp line agrees with particles + FieldLineOverlay +
            // every other surface that surfaces polarity.
            final ChatFormatting color = pol == MagneticPolarity.NORTH
                    ? ChatFormatting.RED : ChatFormatting.AQUA;
            lines.add(Component.translatable("tooltip.magnetization.magnetized_armor",
                    Component.translatable("tooltip.magnetization.polarity." + pol.getSerializedName())
                            .withStyle(color)
            ).withStyle(ChatFormatting.GRAY));

            // LIRM (temporary magnetism) indicator — flagged when the polarity came
            // from a lightning strike rather than the Electromagnet GUI. The marker
            // gets cleared server-side by LirmDecayHandler when decay completes, so
            // the line vanishes on its own.
            if (stack.has(MagDataComponents.LIRM_CREATED_AT.get())) {
                lines.add(Component.translatable("tooltip.magnetization.lirm_temporary")
                        .withStyle(ChatFormatting.GOLD));
            }

            // Per-tool signature ability line — only when the item is a magnetized
            // tool from the metal_tools tag and matches a known tool category.
            if (stack.is(MagTags.METAL_TOOLS)) {
                final String sigKey = signatureFor(stack);
                if (sigKey != null) {
                    lines.add(Component.translatable(sigKey).withStyle(ChatFormatting.DARK_AQUA));
                }
            }
        }

        // Maghemite oxidation countdown — shown on magnetite_ingot /
        // raw_magnetite stacks that the MaghemiteDecayHandler has stamped.
        // The decay clock starts the first sweep that sees the stack; ages
        // past one full Minecraft day convert in place to maghemite.
        // Surfaces remaining-time as a colour-graded percent so a player can
        // see *why* their stockpile is rusting without consulting the wiki.
        // Titanomagnetite block item: surface the recorded field carried via
        // the RECORDED_FIELD data component so a player holding a charged
        // titanomagnetite knows the imprint persists across pick-up/place.
        final net.minecraft.nbt.CompoundTag recordedTag =
                stack.get(MagDataComponents.RECORDED_FIELD.get());
        if (recordedTag != null) {
            final var field = com.stonytark.magnetization.api.MagneticField.fromNbt(recordedTag);
            if (field != null) {
                final ChatFormatting fieldColour = field.polarity() == MagneticPolarity.NORTH
                        ? ChatFormatting.RED : ChatFormatting.AQUA;
                lines.add(Component.translatable("tooltip.magnetization.titanomagnetite.item_recorded",
                                field.strength().name(), field.polarity().name())
                        .withStyle(fieldColour));
            }
        }

        final Long stampedAt = stack.get(MagDataComponents.MAGNETITE_OXIDATION_AGE.get());
        if (stampedAt != null && stack.getItem() != net.minecraft.world.item.Items.AIR) {
            // Use whichever level the tooltip event sees — works in singleplayer
            // and on integrated clients alike. We need *some* clock; client-side
            // game time is fine for a display estimate. Decay window is read
            // from config (default 168000 ticks = 1 in-game week).
            final net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.level != null) {
                final long elapsed = mc.level.getGameTime() - stampedAt;
                final long decay;
                try { decay = com.stonytark.magnetization.config.MagConfig.MAGNETITE_OXIDATION_TICKS.get(); }
                catch (final Throwable t) { return; }
                final float remaining = Math.max(0f, 1f - elapsed / (float) decay);
                final ChatFormatting colour = remaining > 0.66f ? ChatFormatting.GRAY
                        : (remaining > 0.33f ? ChatFormatting.YELLOW : ChatFormatting.RED);
                lines.add(Component.translatable("tooltip.magnetization.magnetite_oxidising",
                                String.format(java.util.Locale.ROOT, "%.0f", remaining * 100f))
                        .withStyle(colour));
            }
        }
    }

    /** @return tooltip translation key matching the tool category, or {@code null} if none. */
    private static String signatureFor(final ItemStack stack) {
        if (matches(stack, ItemTags.SWORDS))   return "tooltip.magnetization.tool_signature.sword";
        if (matches(stack, ItemTags.PICKAXES)) return "tooltip.magnetization.tool_signature.pickaxe";
        if (matches(stack, ItemTags.AXES))     return "tooltip.magnetization.tool_signature.axe";
        if (matches(stack, ItemTags.SHOVELS))  return "tooltip.magnetization.tool_signature.shovel";
        if (matches(stack, ItemTags.HOES))     return "tooltip.magnetization.tool_signature.hoe";
        return null;
    }

    private static boolean matches(final ItemStack stack, final TagKey<Item> tag) {
        return stack.is(tag);
    }
}
