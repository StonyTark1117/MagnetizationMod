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
 * Adds a compact "How to use" line to each addon item's hover tooltip, alongside
 * the full Ponder scenes. Strings live in {@code en_us.json} under
 * {@code tooltip.magnetization.<key>}.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MagItemTooltips {

    private static Map<Item, String> map;

    private MagItemTooltips() {}

    private static Map<Item, String> build() {
        final Map<Item, String> m = new IdentityHashMap<>();
        m.put(MagItems.ELECTROMAGNET.get(),         "tooltip.magnetization.electromagnet.use");
        m.put(MagItems.DIPOLE_ELECTROMAGNET.get(),  "tooltip.magnetization.dipole_electromagnet.use");
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

        // 1.2 machines, propulsion, fluids & misc — keep the "how to use" line
        // consistent across the whole addon. (Items with their own
        // appendHoverText — ore compass, MR armor, boots, backpack, fluids'
        // magnetized state, lens, catalysts, imprint — are intentionally absent
        // here so their tooltip isn't doubled.)
        m.put(MagItems.MAGNETIC_ITEM_FRAME.get(),      "tooltip.magnetization.magnetic_item_frame.use");
        m.put(MagItems.INDUCTION_PAD.get(),            "tooltip.magnetization.induction_pad.use");
        m.put(MagItems.KINETIC_COIL.get(),             "tooltip.magnetization.kinetic_coil.use");
        m.put(MagItems.EMP_CHARGE.get(),               "tooltip.magnetization.emp_charge.use");
        m.put(MagItems.GYROSTABILIZER.get(),           "tooltip.magnetization.gyrostabilizer.use");
        m.put(MagItems.G_FORCE_CUSHION.get(),          "tooltip.magnetization.g_force_cushion.use");
        m.put(MagItems.SOLAR_SAIL.get(),               "tooltip.magnetization.solar_sail.use");
        m.put(MagItems.MHD_JET.get(),                  "tooltip.magnetization.mhd_jet.use");
        m.put(MagItems.MICRO_THRUSTER.get(),           "tooltip.magnetization.micro_thruster.use");
        m.put(MagItems.FUSION_THRUSTER.get(),          "tooltip.magnetization.fusion_thruster.use");
        m.put(MagItems.RAILGUN_EMITTER.get(),          "tooltip.magnetization.railgun_emitter.use");
        m.put(MagItems.ELECTROLYZER.get(),             "tooltip.magnetization.electrolyzer.use");
        m.put(MagItems.HOMOPOLAR_MOTOR.get(),          "tooltip.magnetization.homopolar_motor.use");
        m.put(MagItems.STRUCTURAL_INDUCER.get(),       "tooltip.magnetization.structural_inducer.use");
        m.put(MagItems.TOKAMAK_CONTROLLER.get(),       "tooltip.magnetization.tokamak_controller.use");
        m.put(MagItems.TOKAMAK_COIL.get(),             "tooltip.magnetization.tokamak_coil.use");
        m.put(MagItems.DEUTERIUM_CELL.get(),           "tooltip.magnetization.deuterium_cell.use");
        m.put(MagItems.DEUTERIUM_OXIDE_BUCKET.get(),   "tooltip.magnetization.deuterium_oxide.use");
        m.put(MagItems.MAGNETOSTRICTIVE_SENSOR.get(),  "tooltip.magnetization.magnetostrictive_sensor.use");
        m.put(MagItems.BARKHAUSEN_GENERATOR.get(),     "tooltip.magnetization.barkhausen.use");
        m.put(MagItems.VECTOR_CORE.get(),              "tooltip.magnetization.vector_core.use");
        m.put(MagItems.PYROLYTIC_CARBON.get(),         "tooltip.magnetization.pyrolytic_carbon.use");
        m.put(MagItems.MR_FLUID_BUCKET.get(),          "tooltip.magnetization.mr_fluid.use");
        m.put(MagItems.PETRIFIED_WOOD.get(),           "tooltip.magnetization.petrified_wood.use");
        for (final var anvil : new Item[]{
                MagItems.MAGNETITE_ANVIL.get(), MagItems.MAGHEMITE_ANVIL.get(),
                MagItems.HEMATITE_ANVIL.get(), MagItems.TITANOMAGNETITE_ANVIL.get(),
        }) m.put(anvil, "tooltip.magnetization.magnetic_anvil.use");

        // Remaining registered items, including materials and the specialized
        // gear families. Every addon item gets at least one highlighted use line.
        for (final var i : new Item[]{
                MagItems.DIAMAGNETIC_BLOCK.get(), MagItems.ANOMALY_STONE.get(),
                MagItems.MAGNETIC_GRAVEL.get(), MagItems.COBBLED_ANOMALY_STONE.get(),
                MagItems.ANOMALY_STONE_STAIRS.get(), MagItems.ANOMALY_STONE_SLAB.get(),
                MagItems.COBBLED_ANOMALY_STONE_STAIRS.get(), MagItems.COBBLED_ANOMALY_STONE_SLAB.get(),
                MagItems.COBBLED_ANOMALY_STONE_WALL.get(), MagItems.LITHIUM_ORE.get(),
                MagItems.DEEPSLATE_LITHIUM_ORE.get(),
        }) m.put(i, "tooltip.magnetization.material.use");
        for (final var i : new Item[]{
                MagItems.RAW_GALLIUM.get(), MagItems.GALLIUM_INGOT.get(), MagItems.RAW_LITHIUM.get(),
                MagItems.LITHIUM.get(), MagItems.SOLID_GALLIUM.get(), MagItems.SOLID_HELIUM_3.get(),
                MagItems.HELIUM_3_CRYSTAL.get(), MagItems.HELIUM_3_CELL.get(),
                MagItems.TRITIUM_CELL.get(), MagItems.PYROLYTIC_CARBON.get(),
        }) m.put(i, "tooltip.magnetization.material.use");
        for (final var i : new Item[]{
                MagItems.GALLIUM_SWORD.get(), MagItems.GALLIUM_PICKAXE.get(), MagItems.GALLIUM_AXE.get(),
                MagItems.GALLIUM_SHOVEL.get(), MagItems.GALLIUM_HOE.get(), MagItems.MR_FLUID_SWORD.get(),
                MagItems.MR_FLUID_PICKAXE.get(), MagItems.MR_FLUID_AXE.get(), MagItems.MR_FLUID_SHOVEL.get(),
                MagItems.MR_FLUID_HOE.get(),
        }) m.put(i, "tooltip.magnetization.special_tool.use");
        for (final var i : new Item[]{
                MagItems.GALLIUM_HELMET.get(), MagItems.GALLIUM_CHESTPLATE.get(), MagItems.GALLIUM_LEGGINGS.get(),
                MagItems.GALLIUM_BOOTS.get(), MagItems.GALLIUM_HORSE_ARMOR.get(), MagItems.MR_LIQUID_HELMET.get(),
                MagItems.MR_LIQUID_CHESTPLATE.get(), MagItems.MR_LIQUID_LEGGINGS.get(), MagItems.MR_LIQUID_BOOTS.get(),
                MagItems.MR_FLUID_HORSE_ARMOR.get(), MagItems.MAGNETORESISTIVE_BOOTS.get(), MagItems.ALFVEN_BACKPACK.get(),
        }) m.put(i, "tooltip.magnetization.special_armor.use");
        for (final var i : new Item[]{
                MagItems.FERROFLUID_BUCKET.get(), MagItems.MR_FLUID_BUCKET.get(), MagItems.DEUTERIUM_OXIDE_BUCKET.get(),
                MagItems.GALLIUM_BUCKET.get(), MagItems.MIXED_GALLIUM_BUCKET.get(), MagItems.HYDROGEN_BUCKET.get(),
                MagItems.TRITIUM_BUCKET.get(), MagItems.HELIUM_3_BUCKET.get(), MagItems.LIQUID_LITHIUM_BUCKET.get(),
        }) m.put(i, "tooltip.magnetization.fluid.use");
        for (final var i : new Item[]{
                MagItems.PYRRHOTITE_CATALYST.get(), MagItems.ENHANCED_PYRRHOTITE_CATALYST.get(),
                MagItems.COSMIC_PYRRHOTITE_CATALYST.get(), MagItems.HELIUM_3_GEODE.get(),
        }) m.put(i, "tooltip.magnetization.advanced_material.use");
        m.put(MagItems.RAILGUN_REMOTE.get(), "tooltip.magnetization.railgun_remote.use");
        m.put(MagItems.COSMIC_COMPASS.get(), "tooltip.magnetization.cosmic_compass.use");
        m.put(MagItems.ORE_COMPASS.get(), "tooltip.magnetization.ore_compass.use");
        m.put(MagItems.HEMATITE_LENS.get(), "tooltip.magnetization.hematite_lens.use");
        m.put(MagItems.IMPRINT_MODULE.get(), "tooltip.magnetization.imprint_module.use");
        m.put(MagItems.MR_FLUID_GOLEM_SPAWN_EGG.get(), "tooltip.magnetization.golem_spawn_egg.use");

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
        // Fusion-fuel identity: relative thrust / runtime / FE numbers straight off the
        // live config, paired with the icon badge from FusionFuelInfo. No-op for
        // everything that isn't a fuel.
        FusionFuelInfo.appendPerformance(stack, lines);
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
                                Component.translatable("tooltip.magnetization.strength." + field.strength().name().toLowerCase(java.util.Locale.ROOT)),
                                Component.translatable("tooltip.magnetization.polarity." + field.polarity().getSerializedName()))
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
