package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.content.fluid.ExcitableGasBlock;
import com.stonytark.magnetization.content.fluid.GasExcitation;
import com.stonytark.magnetization.content.fluid.GasFlowingFluid;
import com.stonytark.magnetization.content.gas.AirSeparatorBlockEntity;
import com.stonytark.magnetization.content.item.GasDetectorScanner;
import com.stonytark.magnetization.content.jet.IonThrusterBlockEntity;
import com.stonytark.magnetization.menu.AirSeparatorMenu;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagFluids;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Focused regression coverage for the 1.4.0 noble-gas systems. */
@GameTestHolder(Magnetization.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NobleGasGameTests {
    private NobleGasGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void gasDetectorFindsNearestGasAndFlagsRadon(final GameTestHelper helper) {
        final BlockPos helium = new BlockPos(4, 1, 1);
        final BlockPos radon = new BlockPos(2, 1, 1);
        helper.setBlock(helium, MagFluids.HELIUM.get().defaultFluidState().createLegacyBlock());
        final GasDetectorScanner.Reading heliumReading = GasDetectorScanner.nearest(
                helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));
        helper.assertTrue(heliumReading.found() && heliumReading.fluid() == MagFluids.HELIUM.get(),
                "Gas Detector did not identify the nearest helium source");
        helper.assertTrue(!heliumReading.dangerous() && heliumReading.statusKey().equals("dormant"),
                "Dormant helium reading was classified incorrectly");

        helper.setBlock(radon, MagFluids.RADON.get().defaultFluidState().createLegacyBlock());
        final GasDetectorScanner.Reading radonReading = GasDetectorScanner.nearest(
                helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 2)));
        helper.assertTrue(radonReading.found() && radonReading.fluid() == MagFluids.RADON.get(),
                "Gas Detector did not retarget the nearest radon source");
        helper.assertTrue(radonReading.dangerous(), "Radon must be reported as dangerous");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void nobleGasDirectionCollisionAndTags(final GameTestHelper helper) {
        helper.assertTrue(MagFluids.HELIUM.get() instanceof GasFlowingFluid.Source,
                "Helium must use rising gas flow");
        helper.assertTrue(MagFluids.NEON.get() instanceof GasFlowingFluid.Source,
                "Neon must use rising gas flow");
        helper.assertTrue(MagFluids.ARGON.get() instanceof GasFlowingFluid.DenseSource
                        && MagFluids.KRYPTON.get() instanceof GasFlowingFluid.DenseSource
                        && MagFluids.XENON.get() instanceof GasFlowingFluid.DenseSource
                        && MagFluids.RADON.get() instanceof GasFlowingFluid.DenseSource,
                "Heavy noble gases must use downward gas flow");
        helper.assertTrue(MagBlocks.HELIUM_BLOCK.get().defaultBlockState()
                        .getCollisionShape(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))).isEmpty(),
                "Gas blocks must not collide with entities");
        for (final var fluid : new net.minecraft.world.level.material.Fluid[]{MagFluids.HELIUM.get(), MagFluids.NEON.get(),
                MagFluids.ARGON.get(), MagFluids.KRYPTON.get(), MagFluids.XENON.get(), MagFluids.RADON.get()}) {
            helper.assertTrue(fluid.builtInRegistryHolder().is(MagTags.ION_THRUSTER_PROPELLANTS),
                    "Built-in noble gas missing Ion Thruster tag: " + fluid);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void redstoneExcitesWholeSameGasNetwork(final GameTestHelper helper) {
        final BlockPos first = new BlockPos(1, 1, 1);
        final BlockPos second = new BlockPos(1, 2, 1);
        helper.setBlock(first, MagFluids.HELIUM.get().defaultFluidState().createLegacyBlock());
        helper.setBlock(second, MagFluids.HELIUM.get().defaultFluidState().createLegacyBlock());
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.REDSTONE_BLOCK);
        GasExcitation.recompute(helper.getLevel(), helper.absolutePos(first));
        final var a = helper.getBlockState(first);
        final var b = helper.getBlockState(second);
        helper.assertTrue(a.getValue(ExcitableGasBlock.EXCITED) && b.getValue(ExcitableGasBlock.EXCITED),
                "Adjacent redstone must excite the complete connected same-gas network");
        helper.assertTrue(a.getValue(ExcitableGasBlock.EXCITATION_GRACE) == 3 && a.getLightEmission() == 15,
                "Excited gas must refresh its three-tick grace and emit light level 15");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void sameTickGasMemoInvalidatesAndGraceAdvancesOnce(final GameTestHelper helper) {
        final BlockPos first = new BlockPos(1, 1, 1);
        final BlockPos second = new BlockPos(1, 2, 1);
        final BlockPos power = new BlockPos(2, 2, 1);
        helper.setBlock(first, MagFluids.NEON.get().defaultFluidState().createLegacyBlock());
        helper.setBlock(second, MagFluids.NEON.get().defaultFluidState().createLegacyBlock());

        // The first dormant pass memoizes this component for the current tick.
        GasExcitation.recompute(helper.getLevel(), helper.absolutePos(first));
        helper.assertTrue(!isExcited(helper.getBlockState(first)),
                "Unpowered gas unexpectedly started excited");

        // A same-tick neighbor mutation must clear the memo so the next seed sees
        // the new signal instead of returning from the duplicate-work fast path.
        helper.setBlock(power, Blocks.REDSTONE_BLOCK);
        GasExcitation.recompute(helper.getLevel(), helper.absolutePos(second));
        helper.assertTrue(isExcited(helper.getBlockState(first))
                        && isExcited(helper.getBlockState(second)),
                "Same-tick topology/redstone invalidation left the component stale");

        // Removing power invalidates once more. The first pass advances grace
        // from 3 to 2; a second seed in the same component must be deduplicated
        // rather than consuming another grace step.
        helper.setBlock(power, Blocks.AIR);
        GasExcitation.recompute(helper.getLevel(), helper.absolutePos(first));
        GasExcitation.recompute(helper.getLevel(), helper.absolutePos(second));
        helper.assertTrue(helper.getBlockState(first).getValue(ExcitableGasBlock.EXCITATION_GRACE) == 2
                        && helper.getBlockState(second).getValue(ExcitableGasBlock.EXCITATION_GRACE) == 2,
                "Duplicate same-tick seeds advanced gas grace more than once");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void fusionGasesHaveDistinctLuminescenceBehavior(final GameTestHelper helper) {
        final BlockPos hydrogen = new BlockPos(0, 1, 1);
        final BlockPos power = new BlockPos(1, 1, 1);
        final BlockPos helium3 = new BlockPos(2, 1, 1);
        final BlockPos tritium = new BlockPos(1, 2, 1);
        helper.setBlock(hydrogen, MagFluids.HYDROGEN.get().defaultFluidState().createLegacyBlock());
        helper.setBlock(helium3, MagFluids.HELIUM_3.get().defaultFluidState().createLegacyBlock());
        helper.setBlock(tritium, MagFluids.TRITIUM.get().defaultFluidState().createLegacyBlock());

        helper.assertTrue(MagBlocks.HYDROGEN_BLOCK.get() instanceof ExcitableGasBlock
                        && MagBlocks.HELIUM_3_BLOCK.get() instanceof ExcitableGasBlock,
                "Hydrogen and Helium-3 must participate in field-powered gas excitation");
        helper.assertTrue(!isExcited(helper.getBlockState(hydrogen))
                        && !isExcited(helper.getBlockState(helium3))
                        && helper.getBlockState(hydrogen).getLightEmission() == 0
                        && helper.getBlockState(helium3).getLightEmission() == 0,
                "Dormant Hydrogen and Helium-3 must remain dark until energized");
        helper.assertTrue(!helper.getBlockState(tritium).hasProperty(ExcitableGasBlock.EXCITED)
                        && helper.getBlockState(tritium).getLightEmission() == 6,
                "Tritium must radioluminesce continuously without an excitation state");

        helper.setBlock(power, Blocks.REDSTONE_BLOCK);
        GasExcitation.recompute(helper.getLevel(), helper.absolutePos(hydrogen));
        GasExcitation.recompute(helper.getLevel(), helper.absolutePos(helium3));
        helper.assertTrue(isExcited(helper.getBlockState(hydrogen))
                        && helper.getBlockState(hydrogen).getLightEmission() == 15,
                "Redstone did not excite Hydrogen into its luminous state");
        helper.assertTrue(isExcited(helper.getBlockState(helium3))
                        && helper.getBlockState(helium3).getLightEmission() == 15,
                "Redstone did not excite Helium-3 into its luminous state");
        helper.assertTrue(helper.getBlockState(tritium).getLightEmission() == 6,
                "External power changed Tritium's steady radioluminescence");

        tickFluidNow(helper, hydrogen);
        tickFluidNow(helper, helium3);
        helper.assertTrue(isExcited(helper.getBlockState(hydrogen.above()))
                        && isExcited(helper.getBlockState(helium3.above())),
                "An excited fusion gas lost its luminous state while rising");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void flowingGasRetainsExcitationWithoutDormantFrame(final GameTestHelper helper) {
        final BlockPos rising = new BlockPos(0, 1, 1);
        helper.setBlock(rising, excited(MagFluids.HELIUM.get().defaultFluidState().createLegacyBlock()));
        tickFluidNow(helper, rising);
        helper.assertTrue(isExcited(helper.getBlockState(rising.above())),
                "Rising gas lost excitation while moving into its next cell");

        final BlockPos sinking = new BlockPos(2, 2, 1);
        helper.setBlock(sinking, excited(MagFluids.ARGON.get().defaultFluidState().createLegacyBlock()));
        tickFluidNow(helper, sinking);
        helper.assertTrue(isExcited(helper.getBlockState(sinking.below())),
                "Sinking gas lost excitation while moving into its next cell");

        final BlockPos adjusting = new BlockPos(1, 1, 0);
        final var flowing = MagFluids.HELIUM_FLOWING.get().defaultFluidState()
                .setValue(net.minecraft.world.level.material.FlowingFluid.LEVEL, 7)
                .setValue(net.minecraft.world.level.material.FlowingFluid.FALLING, false);
        helper.setBlock(adjusting, excited(flowing.createLegacyBlock()));
        helper.setBlock(adjusting.above(),
                excited(MagFluids.HELIUM.get().defaultFluidState().createLegacyBlock()));
        helper.assertTrue(!helper.getBlockState(adjusting).getFluidState()
                        .getValue(net.minecraft.world.level.material.FlowingFluid.FALLING),
                "Flow adjustment precondition unexpectedly created a falling cell");
        tickFluidNow(helper, adjusting);
        final var adjustedState = helper.getBlockState(adjusting);
        helper.assertTrue(isExcited(adjustedState),
                "A flowing gas level adjustment rebuilt the block in its dormant state: " + adjustedState);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void excitationUsesOneFeOwnerAndHasGrace(final GameTestHelper helper) {
        final BlockPos gas = new BlockPos(1, 1, 1);
        final BlockPos low = new BlockPos(0, 1, 1);
        final BlockPos high = new BlockPos(2, 1, 1);
        helper.setBlock(gas, MagFluids.NEON.get().defaultFluidState().createLegacyBlock());
        helper.setBlock(low, MagBlocks.GAS_EXCITER.get());
        helper.setBlock(high, MagBlocks.GAS_EXCITER.get());
        final var lowBe = (com.stonytark.magnetization.content.gas.GasExciterBlockEntity) helper.getBlockEntity(low);
        final var highBe = (com.stonytark.magnetization.content.gas.GasExciterBlockEntity) helper.getBlockEntity(high);
        lowBe.energyBuffer().receiveEnergy(100, false);
        highBe.energyBuffer().receiveEnergy(100, false);
        GasExcitation.recompute(helper.getLevel(), helper.absolutePos(gas));
        final int cost = com.stonytark.magnetization.config.MagConfig.gasExciterFePerTick();
        final boolean lowOwns = helper.absolutePos(low).asLong() < helper.absolutePos(high).asLong();
        helper.assertTrue((lowOwns ? lowBe : highBe).energyBuffer().getEnergyStored() == 100 - cost
                        && (lowOwns ? highBe : lowBe).energyBuffer().getEnergyStored() == 100,
                "Exactly the lowest-position eligible Gas Exciter must pay FE");
        helper.setBlock(low, Blocks.AIR);
        helper.setBlock(high, Blocks.AIR);
        GasExcitation.recompute(helper.getLevel(), helper.absolutePos(gas));
        helper.assertTrue(helper.getBlockState(gas).getValue(ExcitableGasBlock.EXCITED),
                "Gas did not retain its full three-tick shutoff grace");
        helper.startSequence().thenExecuteAfter(4, () -> helper.assertTrue(
                        !helper.getBlockState(gas).getValue(ExcitableGasBlock.EXCITED),
                        "Gas stayed excited after its shutoff grace expired"))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void airSeparatorPortsAreIsolatedAndSwappable(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MagBlocks.AIR_SEPARATOR.get().defaultBlockState());
        final AirSeparatorBlockEntity separator = (AirSeparatorBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(separator.gasForFace(Direction.UP) == AirSeparatorBlockEntity.HELIUM
                        && separator.gasForFace(Direction.DOWN) == AirSeparatorBlockEntity.ARGON,
                "Default top/bottom gas port mapping is wrong");
        separator.tank(AirSeparatorBlockEntity.HELIUM).fill(new FluidStack(MagFluids.HELIUM.get(), 1000),
                IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(separator.fluidHandler(Direction.UP).getFluidInTank(0).is(MagFluids.HELIUM.get())
                        && separator.fluidHandler(Direction.DOWN).getFluidInTank(0).isEmpty(),
                "Each output face must expose only its assigned tank");
        final var level = helper.getLevel();
        final var heliumCapability = level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(pos), Direction.UP);
        final var allTanksCapability = level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(pos), null);
        helper.assertTrue(heliumCapability != null
                        && heliumCapability.getFluidInTank(0).is(MagFluids.HELIUM.get())
                        && heliumCapability.drain(new FluidStack(MagFluids.HELIUM.get(), 1000),
                        IFluidHandler.FluidAction.SIMULATE).getAmount() == 1000
                        && allTanksCapability != null
                        && allTanksCapability.getTanks() == AirSeparatorBlockEntity.COUNT,
                "Create fluid capability must expose the assigned gas face and the aggregate separator tanks");
        final int next = separator.cycleFace(Direction.UP, 1);
        helper.assertTrue(next == AirSeparatorBlockEntity.NEON
                        && separator.gasForFace(Direction.UP) == AirSeparatorBlockEntity.NEON,
                "Cycling a port did not preserve the one-to-one face mapping");
        separator.tank(AirSeparatorBlockEntity.NEON).fill(new FluidStack(MagFluids.NEON.get(), 1000),
                IFluidHandler.FluidAction.EXECUTE);
        final var remappedCapability = level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(pos), Direction.UP);
        helper.assertTrue(remappedCapability != null
                        && remappedCapability.getFluidInTank(0).is(MagFluids.NEON.get()),
                "Changing an output assignment must invalidate Create's cached face capability");
        helper.setBlock(pos, helper.getBlockState(pos).setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                Direction.EAST));
        helper.assertTrue(separator.gasForFace(Direction.EAST) == AirSeparatorBlockEntity.XENON,
                "Wrench/structure rotation did not rotate persisted output assignments with the machine");
        final ItemStack module = new ItemStack(MagItems.ISOTOPE_SEPARATION_MODULE.get());
        helper.assertTrue(separator.itemHandler().insertItem(0, module, false).isEmpty()
                        && separator.itemHandler().getSlots() == 2
                        && separator.fluidHandler(Direction.UP).getTanks() == 1,
                "Separator upgrade/output automation or five-tank isolation regressed");
        final net.minecraft.nbt.CompoundTag saved = separator.saveWithoutMetadata(helper.getLevel().registryAccess());
        separator.tank(AirSeparatorBlockEntity.HELIUM).drain(1000, IFluidHandler.FluidAction.EXECUTE);
        separator.cycleFace(Direction.UP, 1);
        separator.loadCustomOnly(saved, helper.getLevel().registryAccess());
        helper.assertTrue(separator.tank(AirSeparatorBlockEntity.HELIUM).getFluidAmount() == 1000
                        && separator.gasForFace(Direction.UP) == AirSeparatorBlockEntity.NEON
                        && separator.gasForFace(Direction.EAST) == AirSeparatorBlockEntity.XENON
                        && separator.upgradeContainer().getItem(0).is(MagItems.ISOTOPE_SEPARATION_MODULE.get()),
                "Separator tanks, port assignments, or isotope upgrade did not survive NBT round-trip");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void airSeparatorLitStateTracksRunningStatus(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MagBlocks.AIR_SEPARATOR.get().defaultBlockState());
        final AirSeparatorBlockEntity separator = (AirSeparatorBlockEntity) helper.getBlockEntity(pos);
        helper.startSequence()
                // Allow Create's first kinetic attachment pass to settle before
                // injecting the test speed into this isolated block.
                .thenExecuteAfter(1, () -> separator.setSpeed(
                        com.stonytark.magnetization.config.MagConfig.airSeparatorMinRpm()))
                .thenExecuteAfter(2, () -> helper.assertTrue(
                        helper.getBlockState(pos).getValue(
                                net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT),
                        "Air Separator must use its active visual while running"))
                .thenExecute(() -> separator.setSpeed(0.0f))
                .thenExecuteAfter(2, () -> helper.assertTrue(
                        !helper.getBlockState(pos).getValue(
                                net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT),
                        "Air Separator must clear its active visual when it stops"))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void separatorGasesFeedIonThrusterThroughFluidCapability(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MagBlocks.ION_THRUSTER.get());
        final var capability = helper.getLevel().getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(pos), Direction.NORTH);
        helper.assertTrue(capability != null, "Ion Thruster must expose a Create-compatible fluid capability");
        for (final var gas : new net.minecraft.world.level.material.Fluid[]{
                MagFluids.HELIUM.get(), MagFluids.NEON.get(), MagFluids.ARGON.get(),
                MagFluids.KRYPTON.get(), MagFluids.XENON.get()}) {
            helper.assertTrue(capability.fill(new FluidStack(gas, 1000), IFluidHandler.FluidAction.SIMULATE) == 1000,
                    "Ion Thruster capability rejected separator gas " + gas);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void airSeparatorMenuSynchronizesTanksAndControlsPorts(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MagBlocks.AIR_SEPARATOR.get().defaultBlockState());
        final AirSeparatorBlockEntity separator = (AirSeparatorBlockEntity) helper.getBlockEntity(pos);
        for (int gas = 0; gas < AirSeparatorBlockEntity.COUNT; gas++) {
            separator.tank(gas).fill(new FluidStack(new net.minecraft.world.level.material.Fluid[]{
                    MagFluids.HELIUM.get(), MagFluids.NEON.get(), MagFluids.ARGON.get(),
                    MagFluids.KRYPTON.get(), MagFluids.XENON.get()}[gas], 1000 + gas * 111),
                    IFluidHandler.FluidAction.EXECUTE);
        }

        final var player = new net.minecraft.server.level.ServerPlayer(
                helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "separator-menu"),
                net.minecraft.server.level.ClientInformation.createDefault());
        final var serverMenu = new com.stonytark.magnetization.menu.AirSeparatorMenu(1,
                player.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.create(
                helper.getLevel(), helper.absolutePos(pos)), helper.absolutePos(pos),
                separator.upgradeContainer(), separator.crystalOutputContainer());

        helper.assertTrue(serverMenu.clickMenuButton(player,
                        AirSeparatorMenu.assignOutputButton(AirSeparatorBlockEntity.HELIUM, Direction.EAST))
                        && separator.outputFace(AirSeparatorBlockEntity.HELIUM) == Direction.EAST
                        && separator.outputFace(AirSeparatorBlockEntity.KRYPTON) == Direction.UP,
                "GUI face selector did not assign Helium to the requested face with a visible deterministic swap");
        helper.assertTrue(serverMenu.clickMenuButton(player,
                        AirSeparatorMenu.setInputButton(Direction.EAST))
                        && separator.mechanicalFace() == Direction.EAST
                        && helper.getBlockState(pos).getValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
                        == Direction.WEST,
                "GUI face selector did not assign the mechanical input face");

        final int[][] transported = {null};
        serverMenu.setSynchronizer(new net.minecraft.world.inventory.ContainerSynchronizer() {
            @Override public void sendInitialData(final net.minecraft.world.inventory.AbstractContainerMenu menu,
                                                  final net.minecraft.core.NonNullList<ItemStack> items,
                                                  final ItemStack carried, final int[] data) {
                transported[0] = data.clone();
            }
            @Override public void sendSlotChange(final net.minecraft.world.inventory.AbstractContainerMenu menu,
                                                 final int slot, final ItemStack stack) {}
            @Override public void sendCarriedChange(final net.minecraft.world.inventory.AbstractContainerMenu menu,
                                                    final ItemStack stack) {}
            @Override public void sendDataChange(final net.minecraft.world.inventory.AbstractContainerMenu menu,
                                                 final int index, final int value) {}
        });
        serverMenu.broadcastFullState();
        helper.assertTrue(transported[0] != null && transported[0].length == 33,
                "Air Separator menu did not emit its complete 33-value process snapshot");

        final var clientMenu = new com.stonytark.magnetization.menu.AirSeparatorMenu(1,
                player.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.NULL, BlockPos.ZERO,
                new net.minecraft.world.SimpleContainer(1), new net.minecraft.world.SimpleContainer(1));
        for (int i = 0; i < transported[0].length; i++) clientMenu.setData(i, transported[0][i]);
        helper.assertTrue(clientMenu.amount(AirSeparatorBlockEntity.HELIUM) == 1000
                        && clientMenu.amount(AirSeparatorBlockEntity.XENON) == 1444
                        && clientMenu.capacity() == separator.tank(AirSeparatorBlockEntity.HELIUM).getCapacity(),
                "Client Air Separator GUI did not retain server-owned tank amounts or capacity");
        helper.assertTrue(clientMenu.outputFace(AirSeparatorBlockEntity.HELIUM) == Direction.NORTH
                        && clientMenu.outputFace(AirSeparatorBlockEntity.KRYPTON) == Direction.UP
                        && clientMenu.mechanicalFace() == Direction.EAST
                        && separator.hudLines().size() >= 4,
                "Client port display or shared goggles/Jade/WTHIT/TOP summary is incomplete");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void ionThrusterProfilesAndBucketGate(final GameTestHelper helper) {
        final net.minecraft.world.level.material.Fluid[] fluids = {MagFluids.HELIUM.get(), MagFluids.NEON.get(),
                MagFluids.ARGON.get(), MagFluids.KRYPTON.get(), MagFluids.XENON.get(), MagFluids.RADON.get()};
        final double[] thrust = {.55, .80, 1.0, 1.30, 1.70, 1.90};
        final double[] speed = {1.40, 1.25, 1.0, 1.20, 1.30, 1.15};
        final int[] fluidCost = {2, 2, 3, 1, 1, 1};
        final int[] feCost = {100, 90, 80, 110, 130, 150};
        for (int i = 0; i < fluids.length; i++) {
            final var profile = IonThrusterBlockEntity.profile(new FluidStack(fluids[i], 1));
            helper.assertTrue(Math.abs(profile.thrust() - thrust[i]) < 0.0001
                            && Math.abs(profile.speed() - speed[i]) < 0.0001
                            && profile.fluidPerTick() == fluidCost[i] && profile.fePerTick() == feCost[i],
                    "Ion Thruster profile drifted for built-in gas index " + i);
        }
        final var helium = IonThrusterBlockEntity.profile(new FluidStack(fluids[0], 1));
        final var xenon = IonThrusterBlockEntity.profile(new FluidStack(fluids[4], 1));
        final var radon = IonThrusterBlockEntity.profile(new FluidStack(fluids[5], 1));
        helper.assertTrue(helium.speed() > xenon.speed() && xenon.thrust() > helium.thrust(),
                "Helium/Xenon speed-thrust tradeoff regressed");
        helper.assertTrue(radon.thrust() > xenon.thrust() && radon.fePerTick() > xenon.fePerTick(),
                "Radon must be the strongest and most power-hungry profile");
        helper.assertTrue(IonThrusterBlockEntity.isPropellantBucket(new ItemStack(MagItems.ARGON_BUCKET.get()))
                        && !IonThrusterBlockEntity.isPropellantBucket(new ItemStack(MagItems.HYDROGEN_BUCKET.get())),
                "Ion Thruster bucket gate accepts an unsupported fusion gas or rejects Argon");
        final BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MagBlocks.ION_THRUSTER.get());
        final var thruster = (IonThrusterBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(thruster.fluidHandler().fill(new FluidStack(MagFluids.HYDROGEN.get(), 1000),
                        IFluidHandler.FluidAction.SIMULATE) == 0,
                "Hydrogen must remain unsupported unless explicitly added to the propellant tag");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "configMutating")
    public static void radonMasterSwitchSuppressesExposure(final GameTestHelper helper) {
        final var zombie = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE,
                new BlockPos(1, 1, 1));
        final boolean previous = com.stonytark.magnetization.config.MagConfig.RADON_RADIATION_ENABLED.get();
        try {
            com.stonytark.magnetization.config.MagConfig.RADON_RADIATION_ENABLED.set(false);
            com.stonytark.magnetization.content.effect.RadonExposureHandler.addExposure(zombie, 1000);
            helper.assertTrue(com.stonytark.magnetization.content.effect.RadonExposureHandler.exposure(zombie) == 0,
                    "Radon exposure accumulated while its master switch was disabled");
        } finally {
            com.stonytark.magnetization.config.MagConfig.RADON_RADIATION_ENABLED.set(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "gasDetectorExposureSync")
    public static void gasDetectorPayloadCarriesServerOwnedExposureAndSafetyDistance(final GameTestHelper helper) {
        final var zombie = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE,
                new BlockPos(1, 1, 1));
        final boolean enabled = com.stonytark.magnetization.config.MagConfig.RADON_RADIATION_ENABLED.get();
        final int threshold = com.stonytark.magnetization.config.MagConfig.RADON_EXPOSURE_THRESHOLD_TICKS.get();
        final int recovery = com.stonytark.magnetization.config.MagConfig.RADON_EXPOSURE_DECAY_PER_TICK.get();
        try {
            com.stonytark.magnetization.config.MagConfig.RADON_RADIATION_ENABLED.set(true);
            com.stonytark.magnetization.config.MagConfig.RADON_EXPOSURE_THRESHOLD_TICKS.set(40);
            com.stonytark.magnetization.config.MagConfig.RADON_EXPOSURE_DECAY_PER_TICK.set(3);
            com.stonytark.magnetization.content.effect.RadonExposureHandler.addExposure(zombie, 10, 2.75d);

            final var snapshot = com.stonytark.magnetization.content.effect.RadonExposureHandler.snapshot(zombie);
            helper.assertTrue(snapshot.radiationEnabled() && snapshot.dose() == 10
                            && snapshot.threshold() == 40 && snapshot.recoveryPerTick() == 3,
                    "Gas Detector exposure snapshot did not use server-owned dose/config values");
            helper.assertTrue(snapshot.exposed() && Math.abs(snapshot.distanceToSafety() - 2.75d) < 0.0001d,
                    "Gas Detector snapshot lost the active exhaust hazard or distance to safety");

            final var payload = com.stonytark.magnetization.network.GasDetectorStatusPayload.from(zombie);
            final var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                    io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
            com.stonytark.magnetization.network.GasDetectorStatusPayload.CODEC.encode(buffer, payload);
            final var received = com.stonytark.magnetization.network.GasDetectorStatusPayload.CODEC.decode(buffer);
            helper.assertTrue(received.radiationEnabled() && received.dose() == 10
                            && received.threshold() == 40 && received.recoveryPerTick() == 3
                            && received.exposed()
                            && Math.abs(received.distanceToSafety() - 2.75d) < 0.0001d,
                    "Clientbound Gas Detector payload drifted from the authoritative exposure snapshot");
        } finally {
            com.stonytark.magnetization.config.MagConfig.RADON_RADIATION_ENABLED.set(enabled);
            com.stonytark.magnetization.config.MagConfig.RADON_EXPOSURE_THRESHOLD_TICKS.set(threshold);
            com.stonytark.magnetization.config.MagConfig.RADON_EXPOSURE_DECAY_PER_TICK.set(recovery);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "configMutating")
    public static void radonExhaustDoseSurvivesCleanAirTick(final GameTestHelper helper) {
        final var zombie = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE,
                new BlockPos(1, 1, 1));
        final boolean previous = com.stonytark.magnetization.config.MagConfig.RADON_RADIATION_ENABLED.get();
        try {
            com.stonytark.magnetization.config.MagConfig.RADON_RADIATION_ENABLED.set(true);
            com.stonytark.magnetization.content.effect.RadonExposureHandler.addExposure(zombie, 1);
            com.stonytark.magnetization.content.effect.RadonExposureHandler.tickExposure(zombie);
            helper.assertTrue(com.stonytark.magnetization.content.effect.RadonExposureHandler.exposure(zombie) == 1,
                    "Clean-air decay canceled continuous Radon exhaust exposure");
        } finally {
            com.stonytark.magnetization.config.MagConfig.RADON_RADIATION_ENABLED.set(previous);
        }
        helper.succeed();
    }

    private static net.minecraft.world.level.block.state.BlockState excited(
            final net.minecraft.world.level.block.state.BlockState state) {
        return state.setValue(ExcitableGasBlock.EXCITED, true)
                .setValue(ExcitableGasBlock.EXCITATION_GRACE, 3);
    }

    private static boolean isExcited(final net.minecraft.world.level.block.state.BlockState state) {
        return state.hasProperty(ExcitableGasBlock.EXCITED)
                && state.getValue(ExcitableGasBlock.EXCITED);
    }

    private static void tickFluidNow(final GameTestHelper helper, final BlockPos relativePos) {
        final BlockPos absolutePos = helper.absolutePos(relativePos);
        final var fluid = helper.getLevel().getFluidState(absolutePos);
        if (!fluid.isEmpty()) fluid.tick(helper.getLevel(), absolutePos);
    }
}
