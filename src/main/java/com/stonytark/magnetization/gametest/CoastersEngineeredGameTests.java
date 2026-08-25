package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.data.CompatConfigCondition;
import dev.jwaterfall.coastersengineered.span.AnchorEnergyBehaviour;
import dev.silvergold.simulatedcoasters.SimulatedCoastersBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Recipe and energy-contract checks against Coasters: Engineered 1.0.1. */
@GameTestHolder("magnetization_coasters_engineered")
@PrefixGameTestTemplate(false)
public final class CoastersEngineeredGameTests {
    private CoastersEngineeredGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void linearMotorRecipeAndEnergyRemainCompatible(final GameTestHelper helper) {
        final boolean master = MagConfig.COASTERS_ENGINEERED_COMPAT_ENABLED.get();
        final boolean recipes = MagConfig.COASTERS_ENGINEERED_RECIPES_ENABLED.get();
        try {
            final ResourceLocation recipe = ResourceLocation.fromNamespaceAndPath(
                    "magnetization", "coasters_engineered_linear_motor_from_magnetic_alloy");
            helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(recipe).isPresent(),
                    "Magnetic-alloy Linear Motor recipe did not load");

            MagConfig.COASTERS_ENGINEERED_COMPAT_ENABLED.set(true);
            MagConfig.COASTERS_ENGINEERED_RECIPES_ENABLED.set(false);
            helper.assertTrue(!new CompatConfigCondition(CompatConfigCondition.Feature.COASTERS_ENGINEERED)
                            .test(ICondition.IContext.EMPTY),
                    "Coasters: Engineered recipe condition ignored its recipe switch");
            MagConfig.COASTERS_ENGINEERED_RECIPES_ENABLED.set(true);
            MagConfig.COASTERS_ENGINEERED_COMPAT_ENABLED.set(false);
            helper.assertTrue(!MagConfig.coastersEngineeredRecipesEnabled(),
                    "Coasters: Engineered master did not suppress recipes");

            helper.setBlock(new BlockPos(2, 2, 2), SimulatedCoastersBlocks.COASTER_ANCHORPOINT.get());
            final var anchor = helper.getBlockEntity(new BlockPos(2, 2, 2));
            final AnchorEnergyBehaviour energy = AnchorEnergyBehaviour.of(anchor);
            helper.assertTrue(energy != null && energy.canReceive() && energy.canExtract(),
                    "Engineered anchor energy is no longer standard extractable FE");
            helper.assertTrue(energy.receiveEnergy(5_000, false) == 5_000,
                    "Engineered anchor rejected FE");
            helper.assertTrue(energy.extractEnergy(Integer.MAX_VALUE, false) == 5_000
                            && energy.getEnergyStored() == 0,
                    "Engineered anchor FE could not be drained through its native contract");
            helper.succeed();
        } finally {
            MagConfig.COASTERS_ENGINEERED_COMPAT_ENABLED.set(master);
            MagConfig.COASTERS_ENGINEERED_RECIPES_ENABLED.set(recipes);
        }
    }
}
