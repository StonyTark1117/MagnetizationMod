package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.compat.ExternalFieldCompat;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.MagneticMaterials;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Published-runtime checks for Alex's Caves magnetic-caves compatibility. */
@GameTestHolder("magnetization_alexscaves")
@PrefixGameTestTemplate(false)
public final class AlexsCavesGameTests {
    private AlexsCavesGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void activeAzureAndScarletMagnetsExposeOppositeShipFields(final GameTestHelper helper) {
        final boolean compat = MagConfig.ALEXSCAVES_COMPAT_ENABLED.get();
        final boolean fields = MagConfig.ALEXSCAVES_FIELDS_ENABLED.get();
        final BlockPos azure = new BlockPos(2, 2, 2);
        final BlockPos scarlet = new BlockPos(6, 2, 2);
        MagConfig.ALEXSCAVES_COMPAT_ENABLED.set(true);
        MagConfig.ALEXSCAVES_FIELDS_ENABLED.set(true);
        helper.setBlock(azure, block("azure_magnet"));
        helper.setBlock(scarlet, block("scarlet_magnet"));
        helper.setBlock(azure.below(), Blocks.REDSTONE_BLOCK);
        helper.setBlock(scarlet.below(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(8, () -> {
            try {
                final var azureField = ExternalFieldCompat.currentField(
                        helper.getLevel(), helper.absolutePos(azure));
                final var scarletField = ExternalFieldCompat.currentField(
                        helper.getLevel(), helper.absolutePos(scarlet));
                helper.assertTrue(azureField != null && azureField.polarity() == MagneticPolarity.NORTH,
                        "Active Azure Magnet did not expose a NORTH field");
                helper.assertTrue(scarletField != null && scarletField.polarity() == MagneticPolarity.SOUTH,
                        "Active Scarlet Magnet did not expose a SOUTH field");
                helper.assertTrue(ExternalFieldCompat.shipsOnly(
                                helper.getLevel().getBlockState(helper.absolutePos(azure))),
                        "Alex's Caves field is not marked ship-only for duplicate-force suppression");
                MagConfig.ALEXSCAVES_COMPAT_ENABLED.set(false);
                helper.assertTrue(ExternalFieldCompat.currentField(
                                helper.getLevel(), helper.absolutePos(azure)) == null,
                        "Alex's Caves master switch did not disable projected fields");
            } finally {
                MagConfig.ALEXSCAVES_COMPAT_ENABLED.set(compat);
                MagConfig.ALEXSCAVES_FIELDS_ENABLED.set(fields);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void neodymiumMachineMagnetsAndSharedRecipesLoad(final GameTestHelper helper) {
        int ingotPotency = -1;
        int magnetPotency = -1;
        for (final String color : new String[]{"azure", "scarlet"}) {
            final var ingot = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(
                    "alexscaves", color + "_neodymium_ingot")).getDefaultInstance();
            final var magnet = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(
                    "alexscaves", color + "_magnet")).getDefaultInstance();
            helper.assertTrue(ingot.is(MagTags.MACHINE_MAGNETS), color + " Neodymium is not a machine magnet");
            helper.assertTrue(magnet.is(MagTags.MACHINE_MAGNETS), color + " Magnet is not a machine magnet");
            final int thisIngot = MagneticMaterials.potency(ingot);
            final int thisMagnet = MagneticMaterials.potency(magnet);
            helper.assertTrue(thisMagnet > thisIngot,
                    color + " Magnet is not stronger than its Neodymium ingredient");
            if (ingotPotency >= 0) helper.assertTrue(thisIngot == ingotPotency,
                    "Azure and Scarlet Neodymium have inconsistent potency");
            if (magnetPotency >= 0) helper.assertTrue(thisMagnet == magnetPotency,
                    "Azure and Scarlet Magnets have inconsistent potency");
            ingotPotency = thisIngot;
            magnetPotency = thisMagnet;
        }
        for (final String path : new String[]{"alexscaves_permanent_magnet_from_neodymium",
                "alexscaves_azure_magnet_from_permanent_magnet",
                "alexscaves_scarlet_magnet_from_permanent_magnet",
                "alexscaves_levitation_rail_from_permanent_magnets",
                "alexscaves_ferrofluid_from_ferrouslime"}) {
            final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("magnetization", path);
            helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(id).isPresent(),
                    "Missing Alex's Caves compatibility recipe " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void railsQuarryAndMovableMagneticBlocksShareNativeRoles(final GameTestHelper helper) {
        for (final String path : new String[]{"magnetic_levitation_rail", "quarry",
                "magnetic_activator", "magnetic_light"}) {
            final var state = block(path).defaultBlockState();
            helper.assertTrue(state.is(MagTags.FERROMAGNETIC_BLOCKS),
                    "Alex's Caves magnetic machine is not ferromagnetic: " + path);
            helper.assertTrue(state.is(MagTags.EDDY_CONDUCTORS),
                    "Alex's Caves magnetic machine is not an eddy conductor: " + path);
        }
        for (final String path : new String[]{"block_of_azure_neodymium",
                "block_of_scarlet_neodymium", "azure_neodymium_node",
                "scarlet_neodymium_node", "azure_neodymium_pillar",
                "scarlet_neodymium_pillar", "galena_iron_ore"}) {
            helper.assertTrue(block(path).defaultBlockState().is(MagTags.FERROMAGNETIC_BLOCKS),
                    "Alex's Caves Neodymium/Galena block has no magnetic role: " + path);
        }
        final TagKey<Block> alexMovable = TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath("alexscaves", "ferromagnetic_blocks"));
        helper.assertTrue(MagBlocks.MAGNETITE_BLOCK.get().defaultBlockState().is(alexMovable),
                "Magnetization blocks were not added to Alex's Caves native movable-block tag");
        helper.assertTrue(MagBlocks.PERMANENT_MAGNET.get().defaultBlockState().is(alexMovable),
                "Permanent Magnet cannot be moved by Alex's Caves magnet machinery");
        helper.succeed();
    }

    private static Block block(final String path) {
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("alexscaves", path);
        if (!BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalStateException("Missing Alex's Caves block " + id);
        return BuiltInRegistries.BLOCK.get(id);
    }
}
