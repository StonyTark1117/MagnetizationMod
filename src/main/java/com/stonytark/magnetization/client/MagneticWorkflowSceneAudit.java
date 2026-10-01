package com.stonytark.magnetization.client;

import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.content.excavator.MagneticExcavatorBlockEntity;
import com.stonytark.magnetization.content.permanent.PermanentMagnetBlock;
import com.stonytark.magnetization.content.inverter.PolarityInverterBlock;
import com.stonytark.magnetization.content.repulsor.RepulsorCoilBlockEntity;
import com.stonytark.magnetization.registry.MagBlocks;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import static com.stonytark.magnetization.gametest.LifecyclePresentationAudit.check;

/** Opt-in checks of the actual world at every rendered tutorial instruction. */
final class MagneticWorkflowSceneAudit {
    private MagneticWorkflowSceneAudit() {}

    static void verify(final PonderScene scene, final String id, final int stage) {
        final var world = scene.getWorld();
        switch (id) {
            case "magnetic_basics" -> {
                final var source = MagneticWorkflowScenes.WORLD_MAGNET;
                check(world.getBlockState(source).getValue(PermanentMagnetBlock.POLARITY)
                        == (stage == 1 ? MagneticPolarity.SOUTH : MagneticPolarity.NORTH), "Fixed magnet polarity stage " + stage);
                check(PolarityInverterBlock.shouldInvert(world, source) == (stage == 2), "Fixed emitter inverter parity stage " + stage);
                check(world.getBlockState(source.above()).is(stage == 1 || stage == 2
                        ? Blocks.RED_STAINED_GLASS : Blocks.BLUE_STAINED_GLASS), "Fixed field marker stage " + stage);
                int inverterCount = 0;
                for (int x : new int[]{3, 5}) if (world.getBlockState(new BlockPos(x, 1, 3)).is(MagBlocks.POLARITY_INVERTER.get())) inverterCount++;
                check(inverterCount == (stage < 4 ? 0 : stage == 4 ? 1 : 2), "Onboard inverter count stage " + stage);
                check(world.getBlockState(MagneticWorkflowScenes.SHIP_MARKER).is(inverterCount % 2 == 0
                        ? Blocks.BLUE_STAINED_GLASS : Blocks.RED_STAINED_GLASS), "Ship polarity marker contradicts onboard parity");
                if (stage == 6) check(world.getBlockState(MagneticWorkflowScenes.SHIP_MAGNET)
                        .getValue(PermanentMagnetBlock.POLARITY) == MagneticPolarity.SOUTH, "Mounted magnet never flipped independently");
            }
            case "magnetic_excavator" -> {
                final var pos = MagneticWorkflowScenes.EXCAVATOR;
                final var be = (MagneticExcavatorBlockEntity) world.getBlockEntity(pos);
                check(be != null && world.getBlockState(pos).getValue(DirectionalBlock.FACING) == Direction.DOWN, "Excavator cone faces away from ore");
                if (stage >= 1) check(be.getRangeOverride() == 4 && be.getStrengthOverride() == MagneticStrength.STRONG
                        && be.getInFlightCapOverride() == 2, "Excavator GUI example missing");
                if (stage >= 2) check(be.isPowered() && be.getRedstoneFuelSlot().getItem(0).is(Items.REDSTONE)
                        && be.getRedstoneFuelSlot().getItem(0).getCount() == 1
                        && world.getBlockState(pos).getValue(BlockStateProperties.POWERED), "Excavator fuel/powered example missing or consumed");
                for (int x : new int[]{2, 4}) {
                    check(world.getBlockState(new BlockPos(x, 1, 2)).is(stage < 4 ? Blocks.IRON_ORE : Blocks.AIR), "Ore arrival stage " + stage);
                    check(world.getBlockState(new BlockPos(x, 2, 2)).is(stage < 3 ? Blocks.STONE : Blocks.AIR), "Excavated terrain stage " + stage);
                }
                final var barrel = (BarrelBlockEntity) world.getBlockEntity(MagneticWorkflowScenes.BARREL);
                check(barrel != null, "Adjacent inventory missing");
                if (stage >= 4) check(barrel.getItem(0).is(Items.RAW_IRON) && barrel.getItem(0).getCount() == 2, "Arrival drops absent from adjacent inventory");
                else check(barrel.getItem(0).isEmpty(), "Arrival shown before mining");
            }
            case "repulsor_transport" -> {
                for (int x = 1; x <= 3; x++) {
                    final var pos = new BlockPos(x, 1, 3);
                    final var be = (RepulsorCoilBlockEntity) world.getBlockEntity(pos);
                    check(be != null && be.isPowered() && be.getRedstoneLevel() == 15
                            && world.getBlockState(pos).getValue(DirectionalBlock.FACING) == Direction.UP, "Repulsor track power/axis wrong");
                    check(be.hasVectorCore() == (stage > 0), "Vector Core installation stage " + stage);
                    if (stage > 0) check(be.thrustDirection() == Direction.EAST, "Conveyor thrust not perpendicular to coil axis");
                }
                if (stage == 3) for (int x = 4; x <= 6; x++) check(world.getBlockState(new BlockPos(x, 1, 3)).is(Blocks.COPPER_BLOCK), "Copper braking pad missing");
            }
            default -> throw new IllegalArgumentException("Unknown workflow scene " + id);
        }
    }
}
