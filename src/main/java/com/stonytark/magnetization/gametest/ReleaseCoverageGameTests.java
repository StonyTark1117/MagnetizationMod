package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.content.MachineFuelItemHandler;
import com.stonytark.magnetization.content.dipole.DipoleElectromagnetBlockEntity;
import com.stonytark.magnetization.content.electrolyzer.ElectrolyzerBlockEntity;
import com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity;
import com.stonytark.magnetization.content.jet.FusionThrusterPanel;
import com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity;
import com.stonytark.magnetization.content.tokamak.TokamakControllerBlockEntity;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagFluids;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Additional 1.3.0 release coverage: persistence, structure boundaries and automation safety. */
@GameTestHolder(Magnetization.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReleaseCoverageGameTests {
    private static final String EMPTY = "empty";

    private ReleaseCoverageGameTests() {}

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void railgunArcLifecyclePersists(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MagBlocks.RAILGUN_EMITTER.get());
        final RailgunEmitterBlockEntity be = (RailgunEmitterBlockEntity) helper.getBlockEntity(pos);
        final RailgunEmitterBlockEntity.ArcState[] states = {
                RailgunEmitterBlockEntity.ArcState.HOLDING,
                RailgunEmitterBlockEntity.ArcState.LAUNCHING,
                RailgunEmitterBlockEntity.ArcState.COOLDOWN
        };
        for (int i = 0; i < states.length; i++) {
            be.setArcState(states[i]);
            be.setLaunchTicks(11 + i);
            be.setCooldownTicks(31 + i);
            be.setManualMode(true);
            be.setRailLength(7 + i);
            be.energyBuffer().receiveEnergy(12_345 + i, false);
            final int expectedEnergy = be.energyBuffer().getEnergyStored();
            final CompoundTag saved = be.saveWithoutMetadata(helper.getLevel().registryAccess());

            be.setArcState(RailgunEmitterBlockEntity.ArcState.IDLE);
            be.setLaunchTicks(0);
            be.setCooldownTicks(0);
            be.setManualMode(false);
            be.setRailLength(0);
            be.loadCustomOnly(saved, helper.getLevel().registryAccess());

            helper.assertTrue(be.arcState() == states[i], "Railgun state did not persist: " + states[i]);
            helper.assertTrue(be.launchTicks() == 11 + i && be.cooldownTicks() == 31 + i,
                    "Railgun lifecycle counters did not persist for " + states[i]);
            helper.assertTrue(be.manualMode() && be.railLength() == 7 + i,
                    "Railgun pairing/length did not persist for " + states[i]);
            helper.assertTrue(be.energyBuffer().getEnergyStored() == expectedEnergy,
                    "Railgun FE did not persist for " + states[i]);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void activeMachineFuelAndEnergyPersist(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final BlockPos tokamakPos = new BlockPos(1, 1, 1);
        helper.setBlock(tokamakPos, MagBlocks.TOKAMAK_CONTROLLER.get());
        buildTokamakRing(helper, tokamakPos);
        final TokamakControllerBlockEntity tokamak =
                (TokamakControllerBlockEntity) helper.getBlockEntity(tokamakPos);
        tokamak.fuelContainer().setItem(0, new ItemStack(MagItems.TRITIUM_CELL.get(), 2));
        TokamakControllerBlockEntity.serverTick(level, helper.absolutePos(tokamakPos),
                tokamak.getBlockState(), tokamak);
        final CompoundTag tokamakSaved = tokamak.saveWithoutMetadata(level.registryAccess());
        final int burn = tokamak.guiStat1();
        final int energy = tokamak.energyBuffer().getEnergyStored();
        tokamak.fuelContainer().clearContent();
        tokamak.loadCustomOnly(tokamakSaved, level.registryAccess());
        helper.assertTrue(tokamak.guiStat1() == burn && burn > 0 && tokamak.currentTier() == 1,
                "Active Tritium burn/tier did not persist");
        helper.assertTrue(tokamak.energyBuffer().getEnergyStored() == energy && energy > 0,
                "Tokamak generated FE did not persist");
        helper.assertTrue(tokamak.fuelContainer().getItem(0).getCount() == 1,
                "Tokamak queued fuel did not persist");

        final BlockPos panelBase = absoluteSkyBase(helper, 240);
        buildFusionPanel(level, panelBase, 5, 3);
        final BlockPos masterPos = panelBase.offset(1, 1, 0);
        final FusionThrusterBlockEntity thruster = (FusionThrusterBlockEntity) level.getBlockEntity(masterPos);
        FusionThrusterBlockEntity.serverTick(level, masterPos, thruster.getBlockState(), thruster);
        thruster.fluidHandler().fill(new FluidStack(MagFluids.HELIUM_3.get(), 2_000),
                IFluidHandler.FluidAction.EXECUTE);
        thruster.energyBuffer().receiveEnergy(54_321, false);
        thruster.bucketContainer().setItem(0, new ItemStack(MagItems.TRITIUM_BUCKET.get()));
        final CompoundTag thrusterSaved = thruster.saveWithoutMetadata(level.registryAccess());
        thruster.bucketContainer().clearContent();
        thruster.loadCustomOnly(thrusterSaved, level.registryAccess());
        helper.assertTrue(thruster.formed() && thruster.interiorCount() == 3,
                "Fusion panel formed/master state did not persist");
        helper.assertTrue(thruster.guiStat1() == 2_000 && thruster.energyBuffer().getEnergyStored() == 54_321,
                "Fusion fuel/FE did not persist");
        helper.assertTrue(thruster.bucketContainer().getItem(0).is(MagItems.TRITIUM_BUCKET.get()),
                "Fusion queued bucket did not persist");
        clearPanel(level, panelBase, 5, 3);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void dipoleConfigurationAndFacingPersist(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MagBlocks.DIPOLE_ELECTROMAGNET.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.WEST));
        final DipoleElectromagnetBlockEntity be =
                (DipoleElectromagnetBlockEntity) helper.getBlockEntity(pos);
        be.setStrengthOverride(MagneticStrength.EXTREME);
        be.setRangeOverride(37);
        be.setPolarityOverride(MagneticPolarity.SOUTH);
        be.setRedstoneLevel(7);
        final CompoundTag saved = be.saveWithoutMetadata(helper.getLevel().registryAccess());

        be.resetOverrides();
        be.setRedstoneLevel(0);
        be.loadCustomOnly(saved, helper.getLevel().registryAccess());
        helper.assertTrue(be.getStrengthOverride() == MagneticStrength.EXTREME
                        && be.getRangeOverride() == 37
                        && be.getPolarityOverride() == MagneticPolarity.SOUTH
                        && be.getRedstoneLevel() == 7,
                "Dipole tuning did not survive NBT round-trip");
        helper.assertTrue(helper.getBlockState(pos).getValue(DirectionalBlock.FACING) == Direction.WEST,
                "Dipole facing changed during BE persistence round-trip");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void multiblockBoundaryAndReformMatrix(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final BlockPos tokamak = new BlockPos(1, 1, 1);
        helper.setBlock(tokamak, MagBlocks.TOKAMAK_CONTROLLER.get());
        buildTokamakRing(helper, tokamak);
        final BlockPos tokamakAbs = helper.absolutePos(tokamak);
        helper.assertTrue(TokamakControllerBlockEntity.isRingFormed(level, tokamakAbs),
                "Tokamak's fixed minimum/maximum 3x3 ring should validate");
        helper.setBlock(tokamak.offset(1, 0, 0), Blocks.AIR);
        helper.assertTrue(!TokamakControllerBlockEntity.isRingFormed(level, tokamakAbs),
                "Incomplete Tokamak ring should fail");
        helper.setBlock(tokamak.offset(1, 0, 0), MagBlocks.TOKAMAK_COIL.get());
        helper.setBlock(tokamak.offset(-1, 0, 0), Blocks.IRON_BLOCK);
        helper.assertTrue(!TokamakControllerBlockEntity.isRingFormed(level, tokamakAbs),
                "Obstructed/wrong-block Tokamak ring should fail");
        helper.setBlock(tokamak.offset(-1, 0, 0), MagBlocks.TOKAMAK_COIL.get());
        helper.assertTrue(TokamakControllerBlockEntity.isRingFormed(level, tokamakAbs),
                "Tokamak should reform after repair");

        final int max = com.stonytark.magnetization.config.MagConfig.fusionThrusterMaxEdge();
        final BlockPos minBase = absoluteSkyBase(helper, 245);
        buildFusionPanel(level, minBase, 3, 3);
        assertPanel(helper, level, minBase.offset(1, 1, 0), true, 1, minBase.offset(1, 1, 0), max,
                "minimum 3x3 Fusion panel");
        clearPanel(level, minBase, 3, 3);

        final BlockPos maxBase = minBase.offset(0, 0, 20);
        buildFusionPanel(level, maxBase, max, max);
        final BlockPos originalMaster = maxBase.offset(1, 1, 0);
        assertPanel(helper, level, originalMaster, true, (max - 2) * (max - 2), originalMaster, max,
                "maximum Fusion panel");

        level.setBlock(maxBase, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        assertPanel(helper, level, originalMaster, false, 0, null, max, "broken Fusion frame");
        level.setBlock(maxBase, MagBlocks.TOKAMAK_COIL.get().defaultBlockState(), Block.UPDATE_ALL);
        assertPanel(helper, level, originalMaster, true, (max - 2) * (max - 2), originalMaster, max,
                "reformed Fusion panel");
        clearPanel(level, maxBase, max, max);

        final BlockPos oversize = minBase.offset(20, 0, 0);
        buildFusionPanel(level, oversize, max + 1, 3);
        assertPanel(helper, level, oversize.offset(1, 1, 0), false, 0, null, max,
                "oversized Fusion panel");
        clearPanel(level, oversize, max + 1, 3);

        final BlockPos obstructed = minBase.offset(40, 0, 0);
        buildFusionPanel(level, obstructed, 5, 3);
        level.setBlock(obstructed.offset(2, 1, 0), Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        assertPanel(helper, level, obstructed.offset(1, 1, 0), false, 0, null, max,
                "obstructed Fusion interior");
        clearPanel(level, obstructed, 5, 3);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void fuelAutomationRejectsAndNeverDuplicates(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final BlockPos tokamakPos = new BlockPos(1, 1, 1);
        helper.setBlock(tokamakPos, MagBlocks.TOKAMAK_CONTROLLER.get());
        final TokamakControllerBlockEntity tokamak =
                (TokamakControllerBlockEntity) helper.getBlockEntity(tokamakPos);
        final MachineFuelItemHandler tokamakItems = new MachineFuelItemHandler(tokamak.fuelContainer());
        final ItemStack wrongCell = new ItemStack(Items.COAL);
        helper.assertTrue(tokamakItems.insertItem(0, wrongCell, false).getCount() == 1
                        && tokamak.fuelContainer().isEmpty(),
                "Tokamak automation must reject non-cell fuel");
        tokamakItems.insertItem(0, new ItemStack(MagItems.TRITIUM_CELL.get(), 2), false);
        helper.assertTrue(tokamakItems.extractItem(0, 1, false).isEmpty()
                        && tokamak.fuelContainer().getItem(0).getCount() == 2,
                "Automation must not extract active Tokamak fuel");

        final BlockPos panelBase = absoluteSkyBase(helper, 250);
        buildFusionPanel(level, panelBase, 3, 3);
        final BlockPos masterPos = panelBase.offset(1, 1, 0);
        final FusionThrusterBlockEntity thruster = (FusionThrusterBlockEntity) level.getBlockEntity(masterPos);
        FusionThrusterBlockEntity.serverTick(level, masterPos, thruster.getBlockState(), thruster);
        final MachineFuelItemHandler buckets = new MachineFuelItemHandler(thruster.bucketContainer());
        helper.assertTrue(buckets.insertItem(0, new ItemStack(Items.WATER_BUCKET), false).getCount() == 1,
                "Fusion Thruster must reject a wrong fluid bucket");
        buckets.insertItem(0, new ItemStack(MagItems.HELIUM_3_BUCKET.get()), false);
        FusionThrusterBlockEntity.serverTick(level, masterPos, thruster.getBlockState(), thruster);
        helper.assertTrue(thruster.bucketContainer().getItem(0).is(Items.BUCKET)
                        && thruster.guiStat1() == 1_000,
                "One fusion bucket must become exactly one empty bucket and 1000 mB");
        helper.assertTrue(buckets.extractItem(0, 64, false).getCount() == 1
                        && buckets.extractItem(0, 64, false).isEmpty(),
                "Empty bucket must be extractable exactly once");
        helper.assertTrue(thruster.fluidHandler().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000),
                        IFluidHandler.FluidAction.EXECUTE) == 0,
                "Fusion pipe must reject a non-fusion fluid");
        final int fuelBeforeDrain = thruster.guiStat1();
        helper.assertTrue(thruster.fluidHandler().drain(1000, IFluidHandler.FluidAction.EXECUTE).isEmpty()
                        && thruster.guiStat1() == fuelBeforeDrain,
                "Pipe extraction must not siphon active fusion fuel");
        clearPanel(level, panelBase, 3, 3);

        final BlockPos electrolyzerPos = new BlockPos(1, 1, 3);
        helper.setBlock(electrolyzerPos, MagBlocks.ELECTROLYZER.get());
        final ElectrolyzerBlockEntity electrolyzer =
                (ElectrolyzerBlockEntity) helper.getBlockEntity(electrolyzerPos);
        final MachineFuelItemHandler waterBuckets = new MachineFuelItemHandler(electrolyzer.bucketContainer());
        helper.assertTrue(waterBuckets.insertItem(0, new ItemStack(MagItems.HYDROGEN_BUCKET.get()), false).getCount() == 1,
                "Electrolyzer must reject a non-water bucket");
        waterBuckets.insertItem(0, new ItemStack(Items.WATER_BUCKET), false);
        ElectrolyzerBlockEntity.serverTick(level, helper.absolutePos(electrolyzerPos),
                electrolyzer.getBlockState(), electrolyzer);
        helper.assertTrue(electrolyzer.bucketContainer().getItem(0).is(Items.BUCKET)
                        && electrolyzer.waterAmount() == 1_000,
                "One water bucket must become exactly one empty bucket and 1000 mB");
        helper.assertTrue(waterBuckets.extractItem(0, 64, false).getCount() == 1
                        && waterBuckets.extractItem(0, 64, false).isEmpty(),
                "Electrolyzer empty bucket must be extractable exactly once");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void fullElectrolyzerOutputStallsWithoutInputLoss(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MagBlocks.ELECTROLYZER.get());
        final ElectrolyzerBlockEntity be = (ElectrolyzerBlockEntity) helper.getBlockEntity(pos);
        be.fluidHandler().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 2_000),
                IFluidHandler.FluidAction.EXECUTE);
        be.energyBuffer().receiveEnergy(20_000, false);
        final CompoundTag seeded = be.saveWithoutMetadata(level.registryAccess());
        final var fullHydrogen = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(
                com.stonytark.magnetization.config.MagConfig.electrolyzerHydrogenTank());
        fullHydrogen.fill(new FluidStack(MagFluids.HYDROGEN.get(), fullHydrogen.getCapacity()),
                IFluidHandler.FluidAction.EXECUTE);
        seeded.put("Hydrogen", fullHydrogen.writeToNBT(level.registryAccess(), new CompoundTag()));
        be.loadCustomOnly(seeded, level.registryAccess());
        final int water = be.waterAmount();
        final int energy = be.energyBuffer().getEnergyStored();
        ElectrolyzerBlockEntity.serverTick(level, helper.absolutePos(pos), be.getBlockState(), be);
        helper.assertTrue(be.waterAmount() == water && be.energyBuffer().getEnergyStored() == energy,
                "Full hydrogen output must stall without consuming water or FE");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void isotopeInventoryAdvancementsRequireRealItems(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerPlayer player = new net.minecraft.server.level.ServerPlayer(
                helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "isotope-advancement"),
                net.minecraft.server.level.ClientInformation.createDefault());
        final var connection = new net.minecraft.network.Connection(
                net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(
                helper.getLevel().getServer(), connection, player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(final net.minecraft.network.protocol.Packet<?> packet) {
                // Headless GameTest player: advancement sync has no client.
            }
        };
        player.getAdvancements().reload(helper.getLevel().getServer().getAdvancements());
        final String[] paths = {"first_electrolyzer_hydrogen", "first_tritium", "first_helium_3"};
        final String[][] criteria = {
                {"hydrogen"},
                {"tritium_cell", "tritium_bucket"},
                {"helium_cell", "helium_bucket", "helium_gas", "crystal"}
        };
        for (int i = 0; i < paths.length; i++) revokeAll(player, paths[i]);

        helper.assertTrue(!criterionDone(player, paths[0], criteria[0][0]),
                "Hydrogen criterion must start incomplete");
        addAndTriggerInventory(player, new ItemStack(MagItems.HYDROGEN_BUCKET.get()));
        helper.assertTrue(criterionDone(player, paths[0], criteria[0][0]),
                "Obtaining a real Hydrogen Bucket should award its criterion");

        addAndTriggerInventory(player, new ItemStack(MagItems.TRITIUM_CELL.get()));
        helper.assertTrue(criterionDone(player, paths[1], criteria[1][0])
                        && !criterionDone(player, paths[1], criteria[1][1]),
                "Tritium cell should satisfy only its alternative criterion");
        player.getInventory().clearContent();
        revokeAll(player, paths[1]);
        addAndTriggerInventory(player, new ItemStack(MagItems.TRITIUM_BUCKET.get()));
        helper.assertTrue(criterionDone(player, paths[1], criteria[1][1]),
                "Tritium bucket should satisfy its alternative criterion");

        final ItemStack[] heliumItems = {
                new ItemStack(MagItems.HELIUM_3_CELL.get()),
                new ItemStack(MagItems.HELIUM_3_BUCKET.get()),
                new ItemStack(MagItems.HELIUM_3_GAS.get()),
                new ItemStack(MagItems.HELIUM_3_CRYSTAL_BLOCK.get())
        };
        for (int i = 0; i < heliumItems.length; i++) {
            player.getInventory().clearContent();
            revokeAll(player, paths[2]);
            addAndTriggerInventory(player, heliumItems[i]);
            helper.assertTrue(criterionDone(player, paths[2], criteria[2][i]),
                    "Obtaining " + heliumItems[i].getItem() + " should award " + criteria[2][i]);
        }
        helper.succeed();
    }

    private static void buildTokamakRing(final GameTestHelper helper, final BlockPos controller) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx != 0 || dz != 0) helper.setBlock(controller.offset(dx, 0, dz), MagBlocks.TOKAMAK_COIL.get());
        }
    }

    private static BlockPos absoluteSkyBase(final GameTestHelper helper, final int y) {
        final BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        return new BlockPos(abs.getX(), y, abs.getZ());
    }

    private static void buildFusionPanel(final ServerLevel level, final BlockPos base,
                                         final int width, final int height) {
        final var interior = MagBlocks.FUSION_THRUSTER.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.NORTH);
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) {
            final boolean inside = x > 0 && x < width - 1 && y > 0 && y < height - 1;
            level.setBlock(base.offset(x, y, 0), inside ? interior : MagBlocks.TOKAMAK_COIL.get().defaultBlockState(),
                    Block.UPDATE_ALL);
        }
    }

    private static void clearPanel(final ServerLevel level, final BlockPos base,
                                   final int width, final int height) {
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) {
            level.setBlock(base.offset(x, y, 0), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void assertPanel(final GameTestHelper helper, final ServerLevel level,
                                    final BlockPos start, final boolean valid, final int count,
                                    final BlockPos master, final int max, final String description) {
        final FusionThrusterPanel.Result result = FusionThrusterPanel.validate(level, start, Direction.NORTH, max);
        helper.assertTrue(result.valid() == valid, description + " validity was " + result.valid());
        if (valid) {
            helper.assertTrue(result.interiorCount() == count,
                    description + " interior count was " + result.interiorCount());
            helper.assertTrue(java.util.Objects.equals(result.master(), master),
                    description + " master was " + result.master() + ", expected " + master);
            for (final BlockPos interior : result.interior()) {
                helper.assertTrue(java.util.Objects.equals(
                                FusionThrusterPanel.findMaster(level, interior, Direction.NORTH, max), master),
                        description + " did not resolve one shared master from " + interior);
            }
        }
    }

    private static void revokeAll(final net.minecraft.server.level.ServerPlayer player, final String path) {
        final var holder = advancement(player, path);
        final var progress = player.getAdvancements().getOrStartProgress(holder);
        final java.util.List<String> completed = new java.util.ArrayList<>();
        progress.getCompletedCriteria().forEach(completed::add);
        for (final String criterion : completed) {
            player.getAdvancements().revoke(holder, criterion);
        }
    }

    private static boolean criterionDone(final net.minecraft.server.level.ServerPlayer player,
                                         final String path, final String criterion) {
        final var progress = player.getAdvancements().getOrStartProgress(advancement(player, path));
        return progress.getCriterion(criterion) != null && progress.getCriterion(criterion).isDone();
    }

    private static net.minecraft.advancements.AdvancementHolder advancement(
            final net.minecraft.server.level.ServerPlayer player, final String path) {
        final var holder = player.server.getAdvancements().get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, path));
        if (holder == null) throw new IllegalStateException("Missing advancement magnetization:" + path);
        return holder;
    }

    private static void addAndTriggerInventory(final net.minecraft.server.level.ServerPlayer player,
                                               final ItemStack stack) {
        final ItemStack changed = stack.copy();
        player.getInventory().add(stack);
        net.minecraft.advancements.CriteriaTriggers.INVENTORY_CHANGED.trigger(
                player, player.getInventory(), changed);
    }
}
