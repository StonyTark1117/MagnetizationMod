package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.content.emp.EmpChargeBlock;
import com.stonytark.magnetization.content.emp.EmpDrainable;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** End-to-end coverage for the EMP's machine-energy wipe. */
@GameTestHolder("magnetization_regressions")
@PrefixGameTestTemplate(false)
public final class EmpGameTests {

    private static final List<Block> RECEIVE_ONLY_MACHINES = List.of(
            MagBlocks.INDUCTION_PAD.get(),
            MagBlocks.MHD_JET.get(),
            MagBlocks.MICRO_THRUSTER.get(),
            MagBlocks.ION_THRUSTER.get(),
            MagBlocks.FUSION_THRUSTER.get(),
            MagBlocks.RAILGUN_EMITTER.get(),
            MagBlocks.MAGNETIC_ITEM_FRAME.get(),
            MagBlocks.GYROSTABILIZER.get(),
            MagBlocks.ELECTROLYZER.get(),
            MagBlocks.GAS_EXCITER.get());

    private EmpGameTests() {}

    /**
     * The public FE capabilities on consumer machines intentionally reject
     * extraction. The EMP must still clear their real backing buffers without
     * weakening that automation boundary for ordinary cables.
     */
    @GameTest(template = "empty", timeoutTicks = 80, batch = "empEnergyWipe")
    public static void empWipesEveryReceiveOnlyMachineBuffer(final GameTestHelper helper) {
        final BlockPos emp = new BlockPos(5, 2, 5);
        final java.util.List<EmpDrainable> charged = new java.util.ArrayList<>();

        for (int i = 0; i < RECEIVE_ONLY_MACHINES.size(); i++) {
            final BlockPos pos = new BlockPos(2 + (i % 4) * 2, 2, 2 + (i / 4) * 2);
            helper.setBlock(pos, RECEIVE_ONLY_MACHINES.get(i));
            final BlockEntity blockEntity = helper.getBlockEntity(pos);
            helper.assertTrue(blockEntity instanceof EmpDrainable,
                    RECEIVE_ONLY_MACHINES.get(i).getName().getString() + " lacks the internal EMP drain hook");
            final EmpDrainable drainable = (EmpDrainable) blockEntity;
            helper.assertTrue(!drainable.energyBuffer().canExtract(),
                    RECEIVE_ONLY_MACHINES.get(i).getName().getString()
                            + " must remain receive-only for ordinary FE automation");
            final int accepted = drainable.energyBuffer().receiveEnergy(5_000, false);
            helper.assertTrue(accepted > 0 && drainable.energyBuffer().getEnergyStored() > 0,
                    RECEIVE_ONLY_MACHINES.get(i).getName().getString() + " could not be charged for the EMP test");
            charged.add(drainable);
        }

        helper.setBlock(emp, MagBlocks.EMP_CHARGE.get());
        EmpChargeBlock.detonate(helper.getLevel(), helper.absolutePos(emp));

        for (int i = 0; i < charged.size(); i++) {
            helper.assertTrue(charged.get(i).energyBuffer().getEnergyStored() == 0,
                    RECEIVE_ONLY_MACHINES.get(i).getName().getString() + " retained FE after the EMP");
        }
        helper.assertTrue(helper.getBlockState(emp).isAir(), "The single-use EMP charge was not consumed");
        helper.succeed();
    }
}
