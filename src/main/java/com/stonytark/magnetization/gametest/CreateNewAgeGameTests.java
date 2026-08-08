package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.api.MagTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Registry/tag contract tests against the published Create: New Age runtime. */
@GameTestHolder("magnetization_create_new_age")
@PrefixGameTestTemplate(false)
public final class CreateNewAgeGameTests {
    private CreateNewAgeGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void currentMagnetsCoilsAndWiresHaveCorrectRoles(final GameTestHelper helper) {
        final Block magnet = block("create_new_age", "redstone_magnet");
        final Block coil = block("create_new_age", "generator_coil");
        final Block copperWire = block("create_new_age", "copper_wire_block");
        helper.assertTrue(magnet.defaultBlockState().is(MagTags.FERROMAGNETIC_BLOCKS),
                "New Age magnet is not ferromagnetic");
        helper.assertTrue(magnet.defaultBlockState().is(MagTags.MAGNETIC_EMITTER_BLOCKS),
                "New Age magnet does not contribute to Sable ship magnetism");
        helper.assertTrue(coil.defaultBlockState().is(MagTags.FERROMAGNETIC_BLOCKS),
                "New Age generator coil is not recognized in magnetic multiblocks");
        helper.assertTrue(coil.defaultBlockState().is(MagTags.EDDY_CONDUCTORS),
                "New Age generator coil is not conductive for Lenz interactions");
        helper.assertTrue(copperWire.defaultBlockState().is(MagTags.EDDY_CONDUCTORS),
                "New Age copper wire block is not conductive for Lenz interactions");
        helper.succeed();
    }

    private static Block block(final String namespace, final String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }
}
