package com.stonytark.magnetization.gametest;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.compat.ExternalFieldCompat;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
        assertBlockRole(helper, "modular_accumulator", false, true);
        assertBlockRole(helper, "connector", false, true);
        assertBlockRole(helper, "large_connector", false, true);
        helper.assertTrue(item("iron_wire").getDefaultInstance().is(MagTags.FERROMAGNETIC_ITEMS),
                "CreateAddition iron wire is not ferromagnetic");
        helper.assertTrue(item("iron_rod").getDefaultInstance().is(MagTags.FERROMAGNETIC_ITEMS),
                "CreateAddition iron rod is not ferromagnetic");
        final ResourceKey<DamageType> teslaDamage = ResourceKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath("createaddition", "tesla_coil"));
        helper.assertTrue(helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolder(teslaDamage)
                        .map(holder -> holder.is(MagTags.LIGHTNING_SOURCES)).orElse(false),
                "CreateAddition Tesla Coil damage is not a LIRM lightning source");
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

    @GameTest(template = "empty", timeoutTicks = 100, batch = "createAdditionTesla")
    public static void chargedTeslaCoilEmitsFieldAndRecipesLoad(final GameTestHelper helper) {
        final boolean compat = MagConfig.CREATE_ADDITION_COMPAT_ENABLED.get();
        final boolean fields = MagConfig.CREATE_ADDITION_FIELDS_ENABLED.get();
        final BlockPos coil = new BlockPos(2, 2, 2);
        MagConfig.CREATE_ADDITION_COMPAT_ENABLED.set(true);
        MagConfig.CREATE_ADDITION_FIELDS_ENABLED.set(true);
        helper.setBlock(coil, block("tesla_coil"));
        helper.setBlock(coil.west(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(3, () -> {
            final IEnergyStorage storage = energyCapability(helper, coil);
            helper.assertTrue(storage != null && storage.canReceive(),
                    "CreateAddition Tesla Coil exposes no receiving FE capability");
            if (storage != null) storage.receiveEnergy(storage.getMaxEnergyStored(), false);
        });
        helper.runAfterDelay(8, () -> {
            try {
                final var field = ExternalFieldCompat.currentField(
                        helper.getLevel(), helper.absolutePos(coil));
                helper.assertTrue(field != null && field.force() > 0.0d,
                        "Powered CreateAddition Tesla Coil did not emit a charge-scaled field");
                MagConfig.CREATE_ADDITION_COMPAT_ENABLED.set(false);
                helper.assertTrue(ExternalFieldCompat.currentField(
                                helper.getLevel(), helper.absolutePos(coil)) == null,
                        "CreateAddition master compatibility switch did not suppress its field");
                for (final String path : new String[]{"createaddition_electric_motor_from_permanent_magnet",
                        "createaddition_alternator_from_permanent_magnet",
                        "ferrofluid_from_plant_oil"}) {
                    final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("magnetization", path);
                    helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(id).isPresent(),
                            "Missing CreateAddition compatibility recipe " + id);
                }
            } finally {
                MagConfig.CREATE_ADDITION_COMPAT_ENABLED.set(compat);
                MagConfig.CREATE_ADDITION_FIELDS_ENABLED.set(fields);
            }
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
