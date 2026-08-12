package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.gas.GasExciterBlockEntity;
import com.stonytark.magnetization.content.gas.GasExcitationProfile;
import com.stonytark.magnetization.content.gas.GasExcitationProfiles;
import com.stonytark.magnetization.content.gas.GasVentBlockEntity;
import com.stonytark.magnetization.content.gas.ProxyGasCloudBlockEntity;
import com.stonytark.magnetization.content.item.GasDetectorScanner;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagFluids;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

/** Live operating-state and synchronization coverage for the Gas Exciter HUD. */
@GameTestHolder(Magnetization.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GasExciterGameTests {
    private static final GasExcitationProfile TEST_PROFILE = new GasExcitationProfile(
            List.of(ResourceLocation.fromNamespaceAndPath("magnetization", "helium"),
                    ResourceLocation.fromNamespaceAndPath("magnetization", "flowing_helium")),
            GasExcitationProfile.Buoyancy.RISE, 0x30112233, 0xFF445566);

    private GasExciterGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void gasVentCapabilityAndContainersAreInsertOnly(final GameTestHelper helper) {
        ensureTestProfile();
        final BlockPos ventPos = new BlockPos(1, 1, 1);
        helper.setBlock(ventPos, MagBlocks.GAS_VENT.get());
        final var level = helper.getLevel();
        final IFluidHandler capability = level.getCapability(Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(ventPos), Direction.NORTH);
        helper.assertTrue(capability != null, "Gas Vent exposed no pipe fluid capability");
        helper.assertTrue(capability.fill(new FluidStack(Fluids.WATER, 1000),
                        IFluidHandler.FluidAction.EXECUTE) == 0,
                "Gas Vent accepted an unprofiled fluid");
        helper.assertTrue(capability.fill(new FluidStack(MagFluids.HELIUM.get(), 1000),
                        IFluidHandler.FluidAction.EXECUTE) == 1000,
                "Pipe capability did not accept one bucket of profiled gas");
        helper.assertTrue(capability.drain(1000, IFluidHandler.FluidAction.EXECUTE).isEmpty()
                        && capability.getFluidInTank(0).getAmount() == 1000,
                "Gas Vent capability allowed extraction instead of insertion only");

        helper.setBlock(ventPos, Blocks.AIR);
        helper.setBlock(ventPos, MagBlocks.GAS_VENT.get());
        final IFluidHandler emptyCapability = level.getCapability(Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(ventPos), Direction.SOUTH);
        final FluidActionResult emptied = FluidUtil.tryEmptyContainer(
                new ItemStack(MagItems.HELIUM_BUCKET.get()), emptyCapability, 1000, null, true);
        helper.assertTrue(emptied.isSuccess() && emptied.getResult().is(Items.BUCKET),
                "Compatible full container did not fill the Gas Vent");
        helper.assertTrue(emptyCapability.getFluidInTank(0).getFluid() == MagFluids.HELIUM.get()
                        && emptyCapability.getFluidInTank(0).getAmount() == 1000,
                "Container filling changed gas identity or amount");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void gasVentFacesWrenchRotatesAndEmitsOnlyFromItsOutput(final GameTestHelper helper) {
        ensureTestProfile();
        final BlockPos ventPos = new BlockPos(1, 1, 1);
        final BlockState east = MagBlocks.GAS_VENT.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST);
        helper.setBlock(ventPos, east);
        final GasVentBlockEntity vent = (GasVentBlockEntity) helper.getBlockEntity(ventPos);
        helper.assertTrue(vent.fluidHandler().fill(new FluidStack(MagFluids.HELIUM.get(), 1000),
                IFluidHandler.FluidAction.EXECUTE) == 1000, "Could not seed a full Gas Vent");
        GasVentBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(ventPos), east, vent);
        helper.assertTrue(helper.getBlockEntity(new BlockPos(2, 1, 1)) instanceof ProxyGasCloudBlockEntity,
                "East-facing Gas Vent did not emit east");
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(0, 1, 1));
        helper.assertTrue(vent.fluidHandler().getFluidInTank(0).isEmpty(),
                "Gas Vent did not consume exactly one full tank when emitting");

        final BlockState rotated = MagBlocks.GAS_VENT.get().getRotatedBlockState(east, Direction.UP);
        helper.assertTrue(rotated.getValue(BlockStateProperties.FACING) != Direction.EAST,
                "Create wrench rotation contract left the Gas Vent unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void proxySourcePersistsAndFillsAnEmptyContainerExactly(final GameTestHelper helper) {
        final BlockPos sourcePos = new BlockPos(1, 1, 1);
        final ProxyGasCloudBlockEntity source = source(helper, sourcePos,
                profile(GasExcitationProfile.Buoyancy.NEUTRAL), Direction.EAST);
        ProxyGasCloudBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(sourcePos),
                helper.getBlockState(sourcePos), source);
        helper.assertTrue(helper.getBlockEntity(sourcePos) == source && source.isSource(),
                "Dormant proxy source did not persist while spreading");
        helper.assertTrue(source.fluidHandler().drain(999, IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                "Proxy source allowed a partial drain");

        final FluidActionResult filled = FluidUtil.tryFillContainer(new ItemStack(Items.BUCKET),
                source.fluidHandler(), 1000, null, true);
        helper.assertTrue(filled.isSuccess() && filled.getResult().is(MagItems.HELIUM_BUCKET.get()),
                "Empty compatible container did not recover the source gas");
        helper.assertBlockPresent(Blocks.AIR, sourcePos);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void proxyGasRises(final GameTestHelper helper) {
        assertDrift(helper, GasExcitationProfile.Buoyancy.RISE, Direction.EAST, Direction.UP);
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void proxyGasSinks(final GameTestHelper helper) {
        assertDrift(helper, GasExcitationProfile.Buoyancy.SINK, Direction.EAST, Direction.DOWN);
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void neutralProxyGasFollowsTheVentDirection(final GameTestHelper helper) {
        assertDrift(helper, GasExcitationProfile.Buoyancy.NEUTRAL, Direction.EAST, Direction.EAST);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void proxyNetworkChargesOnlyOneExciterAndGraceDecays(final GameTestHelper helper) {
        final BlockPos sourcePos = new BlockPos(1, 1, 1);
        final BlockPos childPos = new BlockPos(2, 1, 1);
        final ProxyGasCloudBlockEntity source = source(helper, sourcePos,
                profile(GasExcitationProfile.Buoyancy.NEUTRAL), Direction.EAST);
        ProxyGasCloudBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(sourcePos),
                helper.getBlockState(sourcePos), source);
        final ProxyGasCloudBlockEntity child = (ProxyGasCloudBlockEntity) helper.getBlockEntity(childPos);
        final BlockPos firstPos = new BlockPos(1, 1, 0);
        final BlockPos secondPos = new BlockPos(2, 1, 0);
        helper.setBlock(firstPos, MagBlocks.GAS_EXCITER.get());
        helper.setBlock(secondPos, MagBlocks.GAS_EXCITER.get());
        final GasExciterBlockEntity first = (GasExciterBlockEntity) helper.getBlockEntity(firstPos);
        final GasExciterBlockEntity second = (GasExciterBlockEntity) helper.getBlockEntity(secondPos);
        first.energyBuffer().receiveEnergy(100, false);
        second.energyBuffer().receiveEnergy(100, false);

        com.stonytark.magnetization.content.fluid.GasExcitation.recompute(
                helper.getLevel(), helper.absolutePos(sourcePos));
        final int total = first.energyBuffer().getEnergyStored() + second.energyBuffer().getEnergyStored();
        helper.assertTrue(total == 200 - MagConfig.gasExciterFePerTick(),
                "Connected proxy network charged more than one Gas Exciter");
        helper.assertTrue(source.isExcited() && child.isExcited() && source.grace() == 3,
                "Excitation did not synchronize across the proxy network");

        helper.setBlock(firstPos, Blocks.AIR);
        helper.setBlock(secondPos, Blocks.AIR);
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(!source.isExcited() && source.grace() == 0,
                    "Proxy excitation grace did not decay to dormant");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void rearExciterRedstoneShutoffIsVisibleOnTheVentHud(final GameTestHelper helper) {
        final BlockPos exciterPos = new BlockPos(0, 1, 1);
        final BlockPos ventPos = new BlockPos(1, 1, 1);
        final BlockPos cloudPos = new BlockPos(2, 1, 1);
        helper.setBlock(exciterPos, MagBlocks.GAS_EXCITER.get());
        helper.setBlock(ventPos, MagBlocks.GAS_VENT.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST));
        final ProxyGasCloudBlockEntity cloud = source(helper, cloudPos,
                profile(GasExcitationProfile.Buoyancy.NEUTRAL), Direction.EAST);
        final GasExciterBlockEntity exciter = (GasExciterBlockEntity) helper.getBlockEntity(exciterPos);
        exciter.energyBuffer().receiveEnergy(100, false);
        helper.setBlock(new BlockPos(0, 1, 0), Blocks.REDSTONE_BLOCK);

        com.stonytark.magnetization.content.fluid.GasExcitation.recompute(
                helper.getLevel(), helper.absolutePos(cloudPos));
        helper.assertTrue(!cloud.isExcited() && exciter.energyBuffer().getEnergyStored() == 100,
                "Redstone-disabled rear Gas Exciter energized the cloud or consumed FE");
        final GasVentBlockEntity vent = (GasVentBlockEntity) helper.getBlockEntity(ventPos);
        helper.assertTrue(vent.hudLines().size() == 4
                        && translationKey(vent.hudLines().get(3))
                        .equals("tooltip.magnetization.gas_vent.exciter_redstone"),
                "Gas Vent HUD did not expose attached-exciter redstone status");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void proxyHudDetectorAndClientTagCarryLiveIdentity(final GameTestHelper helper) {
        final BlockPos cloudPos = new BlockPos(1, 1, 1);
        final ProxyGasCloudBlockEntity cloud = source(helper, cloudPos,
                profile(GasExcitationProfile.Buoyancy.RISE), Direction.NORTH);
        cloud.setExcitation(true, 3);
        final CompoundTag update = cloud.getUpdateTag(helper.getLevel().registryAccess());
        helper.assertTrue("magnetization:helium".equals(update.getString("Fluid"))
                        && update.getInt("Density") == 8 && update.getInt("Grace") == 3,
                "Proxy source identity/state was missing from its client synchronization tag");
        helper.assertTrue(cloud.hudLines().size() == 2
                        && translationKey(cloud.hudLines().get(1))
                        .equals("tooltip.magnetization.gas_cloud.excited"),
                "Proxy cloud HUD did not expose its synchronized excitement state");
        final GasDetectorScanner.Reading reading = GasDetectorScanner.nearest(
                helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 0)));
        helper.assertTrue(reading != null && reading.found() && reading.fluid() == MagFluids.HELIUM.get()
                        && reading.excited(),
                "Gas Detector did not discover the excited proxy gas identity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void proxySourceStateSurvivesSaveAndLoad(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(1, 1, 1);
        final ProxyGasCloudBlockEntity original = source(helper, pos,
                profile(GasExcitationProfile.Buoyancy.SINK), Direction.WEST);
        original.setExcitation(true, 2);
        final CompoundTag saved = original.saveWithoutMetadata(helper.getLevel().registryAccess());

        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos, MagBlocks.PROXY_GAS_CLOUD.get().defaultBlockState()
                .setValue(com.stonytark.magnetization.content.gas.ProxyGasCloudBlock.EXCITED, true));
        final ProxyGasCloudBlockEntity loaded = (ProxyGasCloudBlockEntity) helper.getBlockEntity(pos);
        loaded.loadCustomOnly(saved, helper.getLevel().registryAccess());
        helper.assertTrue(loaded.fluid() == MagFluids.HELIUM.get() && loaded.isSource()
                        && loaded.buoyancy() == GasExcitationProfile.Buoyancy.SINK
                        && loaded.driftDirection() == Direction.DOWN && loaded.density() == 8
                        && loaded.grace() == 2 && loaded.dormantArgb() == 0x30112233
                        && loaded.excitedArgb() == 0xFF445566,
                "Proxy source did not preserve all identity, movement, color, and excitation fields");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void unsupportedFlowCellsDecayAfterSourceRemoval(final GameTestHelper helper) {
        final BlockPos sourcePos = new BlockPos(1, 1, 1);
        final BlockPos childPos = new BlockPos(2, 1, 1);
        final ProxyGasCloudBlockEntity source = source(helper, sourcePos,
                profile(GasExcitationProfile.Buoyancy.NEUTRAL), Direction.EAST);
        ProxyGasCloudBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(sourcePos),
                helper.getBlockState(sourcePos), source);
        final ProxyGasCloudBlockEntity child = (ProxyGasCloudBlockEntity) helper.getBlockEntity(childPos);
        helper.assertTrue(child != null && !child.isSource(), "Proxy source did not create a flowing child");
        helper.setBlock(sourcePos, Blocks.AIR);
        ProxyGasCloudBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(childPos),
                helper.getBlockState(childPos), child);
        helper.assertBlockPresent(Blocks.AIR, childPos);
        helper.succeed();
    }

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

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void proxyCloudSharesExcitationAndRecoversExactSource(final GameTestHelper helper) {
        final BlockPos exciterPos = new BlockPos(1, 1, 1);
        final BlockPos cloudPos = new BlockPos(2, 1, 1);
        helper.setBlock(exciterPos, MagBlocks.GAS_EXCITER.get());
        helper.setBlock(cloudPos, MagBlocks.PROXY_GAS_CLOUD.get());
        final GasExciterBlockEntity exciter = (GasExciterBlockEntity) helper.getBlockEntity(exciterPos);
        final ProxyGasCloudBlockEntity cloud = (ProxyGasCloudBlockEntity) helper.getBlockEntity(cloudPos);
        cloud.configureSource(MagFluids.HELIUM.get(), new GasExcitationProfile(
                List.of(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("magnetization", "helium")),
                GasExcitationProfile.Buoyancy.RISE, 0x30112233, 0xFF445566), net.minecraft.core.Direction.EAST);
        exciter.energyBuffer().receiveEnergy(100, false);

        final var level = helper.getLevel();
        final BlockPos absoluteExciter = helper.absolutePos(exciterPos);
        GasExciterBlockEntity.serverTick(level, absoluteExciter, level.getBlockState(absoluteExciter), exciter);
        helper.assertTrue(cloud.isExcited(), "Proxy gas did not join the Gas Exciter network");
        helper.assertTrue(cloud.tint() == 0xFF445566, "Excited proxy gas did not use its profile tint");

        final var recovered = cloud.fluidHandler().drain(1000, IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(recovered.getFluid() == MagFluids.HELIUM.get() && recovered.getAmount() == 1000,
                "Proxy source did not recover exactly 1000 mB with its original identity");
        helper.assertBlockPresent(Blocks.AIR, cloudPos);
        helper.succeed();
    }

    private static void assertDrift(final GameTestHelper helper,
                                    final GasExcitationProfile.Buoyancy buoyancy,
                                    final Direction ventFacing, final Direction expected) {
        final BlockPos sourcePos = new BlockPos(1, 2, 1);
        final ProxyGasCloudBlockEntity source = source(helper, sourcePos, profile(buoyancy), ventFacing);
        ProxyGasCloudBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(sourcePos),
                helper.getBlockState(sourcePos), source);
        final BlockPos target = sourcePos.relative(expected);
        helper.assertTrue(helper.getBlockEntity(target) instanceof ProxyGasCloudBlockEntity child
                        && !child.isSource() && child.fluid() == MagFluids.HELIUM.get(),
                buoyancy + " proxy gas did not drift " + expected);
        helper.assertTrue(helper.getBlockEntity(sourcePos) == source && source.isSource(),
                buoyancy + " proxy source did not persist");
        helper.succeed();
    }

    private static ProxyGasCloudBlockEntity source(final GameTestHelper helper, final BlockPos pos,
                                                    final GasExcitationProfile profile,
                                                    final Direction ventFacing) {
        helper.setBlock(pos, MagBlocks.PROXY_GAS_CLOUD.get());
        final ProxyGasCloudBlockEntity cloud = (ProxyGasCloudBlockEntity) helper.getBlockEntity(pos);
        cloud.configureSource(MagFluids.HELIUM.get(), profile, ventFacing);
        return cloud;
    }

    private static GasExcitationProfile profile(final GasExcitationProfile.Buoyancy buoyancy) {
        return new GasExcitationProfile(TEST_PROFILE.fluids(), buoyancy,
                TEST_PROFILE.dormantArgb(), TEST_PROFILE.excitedArgb());
    }

    private static void ensureTestProfile() {
        GasExcitationProfiles.registerGameTestProfile(TEST_PROFILE);
    }

    private static String translationKey(final net.minecraft.network.chat.Component component) {
        return component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated
                ? translated.getKey() : "";
    }
}
