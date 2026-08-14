package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.content.fluid.FluidSourceChunkScanner;
import com.stonytark.magnetization.content.fluid.HardenedMrFluidBlock;
import com.stonytark.magnetization.content.fluid.HardenedMrFluidRegistry;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.physics.MagneticFields;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Behavioral coverage for MR Fluid promises restored from earlier releases. */
@GameTestHolder("magnetization_regressions")
@PrefixGameTestTemplate(false)
public final class MrFluidRegressionGameTests {

    private MrFluidRegressionGameTests() {}

    /**
     * The original 1.2 MR Fluid promise: a wired redstone signal hardens the
     * fluid into a walkable block, and removing that signal restores the source.
     * This is deliberately independent of any magnetic field.
     */
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void mrFluidHardensWithRedstoneAndReverts(final GameTestHelper helper) {
        // The generated GameTest world can contain passive magnetic ores below
        // the arena. Pick a loaded vertical position that is genuinely outside
        // every field so this proves the redstone path independently.
        final BlockPos[] selected = {null};
        for (int y = 2; y <= 98 && selected[0] == null; y += 8) {
            final BlockPos candidate = new BlockPos(2, y, 1);
            if (!MagneticFields.isInField(helper.getLevel(), helper.absolutePos(candidate))) {
                selected[0] = candidate;
            }
        }
        helper.assertTrue(selected[0] != null,
                "Could not find a field-free position for the redstone-only MR Fluid test");
        final BlockPos fluid = selected[0];
        final BlockPos power = fluid.west();
        helper.setBlock(fluid.below(), Blocks.STONE);
        helper.setBlock(fluid, MagBlocks.MR_FLUID_BLOCK.get());
        helper.setBlock(power, Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(20L, () -> {
            helper.assertTrue(helper.getBlockState(fluid).is(MagBlocks.HARDENED_MR_FLUID.get()),
                    "Redstone-powered MR fluid should harden without a magnetic field");
            helper.setBlock(power, Blocks.AIR);
            helper.runAfterDelay(20L, () -> {
                helper.assertTrue(!MagneticFields.isInField(helper.getLevel(), helper.absolutePos(fluid)),
                        "The redstone-only MR Fluid test position entered an unrelated magnetic field");
                final BlockState reverted = helper.getBlockState(fluid);
                helper.assertTrue(reverted.is(MagBlocks.MR_FLUID_BLOCK.get())
                                && reverted.getFluidState().isSource(),
                        "MR fluid should revert to its source when redstone is removed; got " + reverted);
                helper.succeed();
            });
        });
    }

    /**
     * Reload regression: the hardened registry is process-local and empty in a
     * newly loaded level. Replaying the real chunk-load callback must discover a
     * persisted hardened cell so the normal tick handler can melt an unpowered
     * bridge instead of leaving it permanently solid.
     */
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void persistedHardenedMrFluidRevertsAfterChunkLoad(final GameTestHelper helper) {
        final BlockPos fluid = findFieldFreePosition(helper);
        helper.setBlock(fluid.below(), Blocks.STONE);
        helper.setBlock(fluid, MagBlocks.HARDENED_MR_FLUID.get().defaultBlockState()
                .setValue(HardenedMrFluidBlock.SOURCE, true));

        final BlockPos absoluteFluid = helper.absolutePos(fluid);
        HardenedMrFluidRegistry.remove(helper.getLevel(), absoluteFluid);
        helper.assertTrue(!HardenedMrFluidRegistry.snapshot(helper.getLevel()).contains(absoluteFluid),
                "Test setup must simulate the empty transient registry after a reload");

        FluidSourceChunkScanner.onChunkLoad(new ChunkEvent.Load(
                helper.getLevel().getChunkAt(absoluteFluid), false));

        helper.runAfterDelay(20L, () -> {
            helper.assertTrue(!MagneticFields.isInField(helper.getLevel(), absoluteFluid),
                    "The reload regression position entered an unrelated magnetic field");
            final BlockState reverted = helper.getBlockState(fluid);
            helper.assertTrue(reverted.is(MagBlocks.MR_FLUID_BLOCK.get())
                            && reverted.getFluidState().isSource(),
                    "Persisted unpowered hardened MR fluid should revert after its chunk loads; got " + reverted);
            helper.succeed();
        });
    }

    /** The documented MR barding path must accept polarity in the real
     * electromagnet menu and make the equipped mount react to a field. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void mrHorseArmorMagnetizesAndPullsMount(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final net.minecraft.world.item.ItemStack barding = new net.minecraft.world.item.ItemStack(
                com.stonytark.magnetization.registry.MagItems.MR_FLUID_HORSE_ARMOR.get());

        final BlockPos emitterRel = new BlockPos(0, 1, 0);
        helper.setBlock(emitterRel, MagBlocks.ELECTROMAGNET.get());
        final BlockPos emitterPos = helper.absolutePos(emitterRel);
        final net.minecraft.world.entity.player.Player player =
                helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        final com.stonytark.magnetization.menu.EmitterMenu menu =
                new com.stonytark.magnetization.menu.EmitterMenu(1, player.getInventory(),
                        net.minecraft.world.inventory.ContainerLevelAccess.create(level, emitterPos), emitterPos,
                        com.stonytark.magnetization.menu.EmitterMenu.CAP_ARMOR
                                | com.stonytark.magnetization.menu.EmitterMenu.CAP_POLARITY);
        helper.assertTrue(menu.getSlot(0).mayPlace(barding),
                "The electromagnet armor slot should accept MR horse armor");
        menu.getSlot(0).set(barding);
        helper.assertTrue(menu.clickMenuButton(player,
                        com.stonytark.magnetization.menu.EmitterMenu.BUTTON_POLARITY_SOUTH),
                "The electromagnet polarity button should handle MR horse armor");
        helper.assertTrue(menu.armorStack().get(
                        com.stonytark.magnetization.registry.MagDataComponents.ARMOR_POLARITY.get())
                        == com.stonytark.magnetization.api.MagneticPolarity.SOUTH,
                "The electromagnet should stamp SOUTH polarity onto MR horse armor");

        final net.minecraft.world.entity.animal.horse.Horse horse =
                helper.spawn(net.minecraft.world.entity.EntityType.HORSE, new BlockPos(1, 1, 1));
        horse.setNoAi(true);
        horse.setNoGravity(true);
        horse.setItemSlot(net.minecraft.world.entity.EquipmentSlot.BODY, menu.armorStack().copy());
        final com.stonytark.magnetization.api.MagneticField field =
                new com.stonytark.magnetization.api.MagneticField(
                        net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 1, 1))),
                        new net.minecraft.world.phys.Vec3(0, 1, 0),
                        com.stonytark.magnetization.api.MagneticPolarity.SOUTH,
                        com.stonytark.magnetization.api.MagneticStrength.STRONG,
                        com.stonytark.magnetization.api.MagneticField.Shape.OMNIDIRECTIONAL);

        helper.runAfterDelay(3L, () -> {
            horse.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            com.stonytark.magnetization.physics.FieldApplicator.apply(level, field, true, true);
            helper.assertTrue(horse.getDeltaMovement().lengthSqr() > 1.0e-6,
                    "A horse wearing magnetized MR barding should react to a magnetic field");
            horse.discard();
            helper.succeed();
        });
    }

    private static BlockPos findFieldFreePosition(final GameTestHelper helper) {
        for (int y = 2; y <= 98; y += 8) {
            final BlockPos candidate = new BlockPos(2, y, 1);
            if (!MagneticFields.isInField(helper.getLevel(), helper.absolutePos(candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find a field-free position for MR Fluid regression coverage");
    }
}
