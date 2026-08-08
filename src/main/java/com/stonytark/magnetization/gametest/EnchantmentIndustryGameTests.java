package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.api.MagTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Published-runtime tag-parity checks for Create: Enchantment Industry. */
@GameTestHolder("magnetization_enchantment_industry")
@PrefixGameTestTemplate(false)
public final class EnchantmentIndustryGameTests {
    private EnchantmentIndustryGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void metalMachinesAreConductiveWithoutMagnetizingExperience(
            final GameTestHelper helper) {
        assertConductive(helper, "printer");
        assertConductive(helper, "mechanical_grindstone");
        assertConductive(helper, "grindstone_drain");
        assertConductive(helper, "experience_hatch");
        assertConductive(helper, "experience_lantern");
        assertConductive(helper, "blaze_enchanter");
        assertConductive(helper, "blaze_forger");

        final var experience = block("experience").defaultBlockState();
        helper.assertTrue(!experience.is(MagTags.FERROMAGNETIC_BLOCKS)
                        && !experience.is(MagTags.EDDY_CONDUCTORS),
                "Liquid experience was incorrectly given a magnetic material role");
        final var superExperience = block("super_experience_block").defaultBlockState();
        helper.assertTrue(!superExperience.is(MagTags.FERROMAGNETIC_BLOCKS)
                        && !superExperience.is(MagTags.EDDY_CONDUCTORS),
                "Super Experience was incorrectly given a magnetic material role");
        helper.succeed();
    }

    private static void assertConductive(final GameTestHelper helper, final String path) {
        helper.assertTrue(block(path).defaultBlockState().is(MagTags.EDDY_CONDUCTORS),
                "Enchantment Industry metal machine is missing conductive tag parity: " + path);
    }

    private static Block block(final String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(
                "create_enchantment_industry", path));
    }
}
