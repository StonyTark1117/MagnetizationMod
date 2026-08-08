package com.stonytark.magnetization.gametest;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.stonytark.magnetization.api.MagTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Published-runtime checks for Create: Diesel Generators. */
@GameTestHolder("magnetization_diesel_generators")
@PrefixGameTestTemplate(false)
public final class DieselGeneratorsGameTests {
    private DieselGeneratorsGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "dieselGeneratorCompat")
    public static void enginesUseKineticsAndFluidMachineryIsExplicitlyExcluded(
            final GameTestHelper helper) {
        final BlockPos enginePos = new BlockPos(2, 2, 2);
        final BlockPos modularPos = new BlockPos(4, 2, 2);
        helper.setBlock(enginePos, block("diesel_engine"));
        helper.setBlock(modularPos, block("large_diesel_engine"));
        final var engine = helper.getBlockEntity(enginePos);
        final var modular = helper.getBlockEntity(modularPos);

        helper.assertTrue(engine instanceof GeneratingKineticBlockEntity,
                "Diesel engine no longer exposes Create's kinetic generator contract");
        helper.assertTrue(engine instanceof IHaveGoggleInformation,
                "Diesel engine lost its native Create goggles information");
        helper.assertTrue(modular instanceof GeneratingKineticBlockEntity,
                "Industrial modular diesel engine is not a Create kinetic generator");
        helper.assertTrue(block("diesel_engine").defaultBlockState().is(MagTags.FERROMAGNETIC_BLOCKS),
                "Diesel engine casing does not contribute ship susceptibility");
        helper.assertTrue(block("large_diesel_engine").defaultBlockState().is(MagTags.FERROMAGNETIC_BLOCKS),
                "Industrial diesel multiblock does not contribute ship susceptibility");
        helper.assertTrue(block("powered_engine_shaft").defaultBlockState().is(MagTags.FERROMAGNETIC_BLOCKS),
                "Huge-engine powered shaft is missing magnetic material parity");

        assertExcluded(helper, "distillation_tank");
        assertExcluded(helper, "bulk_fermenter");
        assertExcluded(helper, "oil_barrel");
        assertExcluded(helper, "canister");
        assertExcluded(helper, "gasoline");
        assertExcluded(helper, "diesel");
        assertExcluded(helper, "crude_oil");
        helper.succeed();
    }

    private static void assertExcluded(final GameTestHelper helper, final String id) {
        final var state = block(id).defaultBlockState();
        helper.assertTrue(state.is(MagTags.MAGNETIC_SUSCEPTIBILITY_EXCLUDED),
                "Diesel fuel/fluid machinery is not explicitly susceptibility-excluded: " + id);
        helper.assertTrue(!state.is(MagTags.FERROMAGNETIC_BLOCKS),
                "Diesel fuel/fluid machinery was incorrectly tagged ferromagnetic: " + id);
    }

    private static Block block(final String path) {
        return BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("createdieselgenerators", path));
    }
}
