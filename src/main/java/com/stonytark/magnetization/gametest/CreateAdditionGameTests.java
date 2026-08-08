package com.stonytark.magnetization.gametest;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Contract tests against the published Create Crafts & Additions runtime. */
@GameTestHolder("magnetization_createaddition")
@PrefixGameTestTemplate(false)
public final class CreateAdditionGameTests {
    private CreateAdditionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void kineticElectricMachinesAndIronComponentsHaveMagneticRoles(
            final GameTestHelper helper) {
        assertBlockRole(helper, "electric_motor", true, true);
        assertBlockRole(helper, "alternator", true, true);
        assertBlockRole(helper, "rolling_mill", true, true);
        assertBlockRole(helper, "tesla_coil", true, true);
        helper.assertTrue(item("iron_wire").getDefaultInstance().is(MagTags.FERROMAGNETIC_ITEMS),
                "CreateAddition iron wire is not ferromagnetic");
        helper.assertTrue(item("iron_rod").getDefaultInstance().is(MagTags.FERROMAGNETIC_ITEMS),
                "CreateAddition iron rod is not ferromagnetic");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = "createAdditionEnergy")
    public static void publishedFeSourcePowersEmitterAndMachinesKeepGoggleParity(
            final GameTestHelper helper) {
        final BlockPos sourceRel = new BlockPos(1, 1, 1);
        final BlockPos emitterRel = new BlockPos(2, 1, 1);
        final BlockPos alternatorRel = new BlockPos(1, 1, 3);
        final BlockPos motorRel = new BlockPos(2, 1, 3);
        helper.setBlock(sourceRel, block("creative_energy"));
        helper.setBlock(emitterRel, MagBlocks.ELECTROMAGNET.get());
        helper.setBlock(alternatorRel, block("alternator"));
        helper.setBlock(motorRel, block("electric_motor"));

        helper.runAfterDelay(3, () -> {
            final IEnergyStorage source = energyCapability(helper, sourceRel);
            final IEnergyStorage target = energyCapability(helper, emitterRel);
            helper.assertTrue(source != null && source.canExtract(),
                    "CreateAddition Creative Generator exposes no extractable NeoForge FE capability");
            helper.assertTrue(target != null && target.canReceive(),
                    "Magnetization emitter exposes no receiving NeoForge FE capability");
            if (source == null || target == null) return;

            final int extracted = source.extractEnergy(200, false);
            final int received = target.receiveEnergy(extracted, false);
            helper.assertTrue(received > 0,
                    "CreateAddition FE could not enter the Magnetization emitter");

            final AbstractEmitterBlockEntity emitter =
                    (AbstractEmitterBlockEntity) helper.getBlockEntity(emitterRel);
            emitter.setPowered(false);
            AbstractEmitterBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(emitterRel),
                    emitter.getBlockState(), emitter);
            helper.assertTrue(emitter.isPowered(),
                    "CreateAddition-supplied FE did not activate the emitter without redstone");

            assertGoggleInformation(helper, helper.getBlockEntity(alternatorRel), "Alternator");
            assertGoggleInformation(helper, helper.getBlockEntity(motorRel), "Electric Motor");
            helper.succeed();
        });
    }

    private static void assertBlockRole(final GameTestHelper helper, final String path,
                                        final boolean ferromagnetic, final boolean conductive) {
        final Block block = block(path);
        helper.assertTrue(!ferromagnetic || block.defaultBlockState().is(MagTags.FERROMAGNETIC_BLOCKS),
                "CreateAddition " + path + " is not ferromagnetic");
        helper.assertTrue(!conductive || block.defaultBlockState().is(MagTags.EDDY_CONDUCTORS),
                "CreateAddition " + path + " is not an eddy-current conductor");
    }

    private static void assertGoggleInformation(final GameTestHelper helper,
                                                final BlockEntity blockEntity,
                                                final String name) {
        helper.assertTrue(blockEntity instanceof IHaveGoggleInformation,
                "CreateAddition " + name + " lost Create-goggle support");
        // Do not execute addToGoggleTooltip here: CreateAddition's implementation
        // legitimately reads Minecraft.getInstance() and is therefore client-only.
        // The dedicated-server contract we can safely enforce is that both machine
        // BEs continue to implement Create's public goggles interface.
    }

    private static IEnergyStorage energyCapability(final GameTestHelper helper, final BlockPos rel) {
        final BlockPos absolute = helper.absolutePos(rel);
        IEnergyStorage storage = helper.getLevel().getCapability(
                Capabilities.EnergyStorage.BLOCK, absolute, null);
        if (storage != null) return storage;
        for (Direction direction : Direction.values()) {
            storage = helper.getLevel().getCapability(
                    Capabilities.EnergyStorage.BLOCK, absolute, direction);
            if (storage != null) return storage;
        }
        return null;
    }

    private static Block block(final String path) {
        return BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("createaddition", path));
    }

    private static net.minecraft.world.item.Item item(final String path) {
        return BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("createaddition", path));
    }
}
