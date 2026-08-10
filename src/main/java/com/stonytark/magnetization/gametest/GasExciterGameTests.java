package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.gas.GasExciterBlockEntity;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Live operating-state and synchronization coverage for the Gas Exciter HUD. */
@GameTestHolder(Magnetization.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GasExciterGameTests {
    private GasExciterGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void gasAndRedstoneStateDriveExciterHud(final GameTestHelper helper) {
        final BlockPos power = new BlockPos(0, 1, 1);
        final BlockPos exciterPos = new BlockPos(1, 1, 1);
        final BlockPos gasPos = new BlockPos(2, 1, 1);
        helper.setBlock(exciterPos, MagBlocks.GAS_EXCITER.get());
        helper.setBlock(gasPos, MagFluids.HELIUM.get().defaultFluidState().createLegacyBlock());
        helper.setBlock(power, Blocks.REDSTONE_BLOCK);

        final var level = helper.getLevel();
        final BlockPos absoluteExciter = helper.absolutePos(exciterPos);
        final GasExciterBlockEntity exciter = (GasExciterBlockEntity) helper.getBlockEntity(exciterPos);
        exciter.energyBuffer().receiveEnergy(100, false);
        GasExciterBlockEntity.serverTick(level, absoluteExciter, level.getBlockState(absoluteExciter), exciter);

        helper.assertTrue(exciter.hudGas() == MagFluids.HELIUM.get(),
                "HUD did not identify the adjacent Helium network");
        helper.assertTrue(exciter.hudRedstoneDisabled() && !exciter.hudActive(),
                "A redstone-disabled Gas Exciter reported itself as on");
        helper.assertTrue(exciter.energyBuffer().getEnergyStored() == 100,
                "A redstone-disabled Gas Exciter consumed FE");
        helper.assertTrue(!level.getBlockState(absoluteExciter).getValue(BlockStateProperties.LIT),
                "A redstone-disabled Gas Exciter used its active model");
        final var disabledSync = exciter.getUpdateTag(level.registryAccess());
        helper.assertTrue(disabledSync.getBoolean("HudRedstoneDisabled")
                        && "magnetization:helium".equals(disabledSync.getString("HudGas")),
                "Gas/redstone HUD state was not included in the client update tag");

        helper.setBlock(power, Blocks.AIR);
        GasExciterBlockEntity.serverTick(level, absoluteExciter, level.getBlockState(absoluteExciter), exciter);
        helper.assertTrue(exciter.hudActive() && !exciter.hudRedstoneDisabled(),
                "Removing the redstone signal did not turn the Gas Exciter on");
        helper.assertTrue(exciter.energyBuffer().getEnergyStored() == 100 - MagConfig.gasExciterFePerTick(),
                "Enabled Gas Exciter did not consume exactly one tick of FE");
        helper.assertTrue(level.getBlockState(absoluteExciter).getValue(BlockStateProperties.LIT),
                "Active Gas Exciter did not select its lit model");

        helper.setBlock(gasPos, Blocks.AIR);
        GasExciterBlockEntity.serverTick(level, absoluteExciter, level.getBlockState(absoluteExciter), exciter);
        helper.assertTrue(exciter.hudGas() == Fluids.EMPTY && !exciter.hudActive(),
                "Gas Exciter kept stale gas/on HUD state after its gas was removed");
        helper.succeed();
    }
}
