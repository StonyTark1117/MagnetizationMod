package com.stonytark.magnetization.gametest;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.stonytark.magnetization.api.EmitterPreset;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.compat.EnderFieldRelayCompat;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.EmitterRegistry;
import com.stonytark.magnetization.physics.MagneticFields;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagFluids;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Published-runtime checks for Ender Transmission's actual power transport. */
@GameTestHolder("magnetization_ender_transmission")
@PrefixGameTestTemplate(false)
public final class EnderTransmissionGameTests {
    private EnderTransmissionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void remoteKineticChannelAndChunkLoaderRemainCompatible(
            final GameTestHelper helper) {
        final BlockPos firstPos = new BlockPos(1, 2, 1);
        final BlockPos secondPos = new BlockPos(5, 2, 1);
        final BlockPos loaderPos = new BlockPos(3, 2, 4);
        helper.setBlock(firstPos, block("energy_transmitter"));
        helper.setBlock(secondPos, block("energy_transmitter"));
        helper.setBlock(loaderPos, block("chunk_loader"));

        helper.runAfterDelay(3, () -> {
            final KineticBlockEntity first = (KineticBlockEntity) helper.getBlockEntity(firstPos);
            final KineticBlockEntity second = (KineticBlockEntity) helper.getBlockEntity(secondPos);
            first.getPersistentData().putInt("channel", 7);
            second.getPersistentData().putInt("channel", 7);
            first.getPersistentData().putString("password", "magnetization_gametest");
            second.getPersistentData().putString("password", "magnetization_gametest");
            invoke(first, "reloadSettings");
            invoke(second, "reloadSettings");
            invoke(first, "afterReload");
            invoke(second, "afterReload");

            final Object connected = invoke(first, "getConnectedTransmitters");
            helper.assertTrue(connected instanceof java.util.List<?> list && list.contains(second),
                    "Ender Transmission did not link matching remote kinetic transmitters");

            final KineticBlockEntity loader = (KineticBlockEntity) helper.getBlockEntity(loaderPos);
            loader.setSpeed(256.0F);
            invoke(loader, "tick");
            final ChunkPos center = new ChunkPos(helper.absolutePos(loaderPos));
            helper.assertTrue(helper.getLevel().getForcedChunks().contains(
                            ChunkPos.asLong(center.x + 2, center.z + 2)),
                    "Powered Ender Transmission chunk loader did not force its advertised remote radius");
            loader.setSpeed(0.0F);
            invoke(loader, "tick");
            helper.assertTrue(!helper.getLevel().getForcedChunks().contains(
                            ChunkPos.asLong(center.x + 2, center.z + 2)),
                    "Stopped Ender Transmission chunk loader retained a stale force ticket");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fluidsAndComponentBearingItemsSurviveRemoteTransmission(
            final GameTestHelper helper) {
        final boolean compat = MagConfig.ENDER_TRANSMISSION_COMPAT_ENABLED.get();
        final BlockPos itemA = new BlockPos(1, 2, 1);
        final BlockPos itemB = new BlockPos(5, 2, 1);
        final BlockPos fluidA = new BlockPos(1, 2, 5);
        final BlockPos fluidB = new BlockPos(5, 2, 5);
        MagConfig.ENDER_TRANSMISSION_COMPAT_ENABLED.set(true);
        helper.setBlock(itemA, block("item_transmitter"));
        helper.setBlock(itemB, block("item_transmitter"));
        helper.setBlock(fluidA, block("fluid_transmitter"));
        helper.setBlock(fluidB, block("fluid_transmitter"));
        helper.runAfterDelay(5, () -> {
            try {
                configure(helper.getBlockEntity(itemA), 7, "magnetization_items");
                configure(helper.getBlockEntity(itemB), 7, "magnetization_items");
                configure(helper.getBlockEntity(fluidA), 8, "magnetization_fluids");
                configure(helper.getBlockEntity(fluidB), 8, "magnetization_fluids");

                final IItemHandler itemInput = helper.getLevel().getCapability(
                        Capabilities.ItemHandler.BLOCK, helper.absolutePos(itemA), null);
                final IItemHandler itemOutput = helper.getLevel().getCapability(
                        Capabilities.ItemHandler.BLOCK, helper.absolutePos(itemB), null);
                helper.assertTrue(itemInput != null && itemOutput != null,
                        "Ender item transmitters expose no item capability");
                if (itemInput == null || itemOutput == null) return;
                final ItemStack polarized = new ItemStack(Items.IRON_CHESTPLATE);
                polarized.set(MagDataComponents.ARMOR_POLARITY.get(), MagneticPolarity.SOUTH);
                helper.assertTrue(itemInput.insertItem(0, polarized, false).isEmpty(),
                        "Ender item transmitter rejected a polarized item");
                final ItemStack received = itemOutput.extractItem(0, 1, false);
                helper.assertTrue(received.get(MagDataComponents.ARMOR_POLARITY.get()) == MagneticPolarity.SOUTH,
                        "Ender item transmission stripped Magnetization data components");
                final EmitterPreset preset = new EmitterPreset(
                        MagneticStrength.STRONG, MagneticPolarity.SOUTH, 12,
                        ResourceLocation.fromNamespaceAndPath("magnetization", "electromagnet"));
                final ItemStack imprint = new ItemStack(MagItems.IMPRINT_MODULE.get());
                imprint.set(MagDataComponents.EMITTER_PRESET.get(), preset);
                helper.assertTrue(itemInput.insertItem(0, imprint, false).isEmpty(),
                        "Ender item transmitter rejected an Imprint Module");
                final ItemStack receivedImprint = itemOutput.extractItem(0, 1, false);
                helper.assertTrue(preset.equals(receivedImprint.get(MagDataComponents.EMITTER_PRESET.get())),
                        "Ender item transmission altered Imprint Module emitter data");

                final IFluidHandler fluidInput = helper.getLevel().getCapability(
                        Capabilities.FluidHandler.BLOCK, helper.absolutePos(fluidA), null);
                final IFluidHandler fluidOutput = helper.getLevel().getCapability(
                        Capabilities.FluidHandler.BLOCK, helper.absolutePos(fluidB), null);
                helper.assertTrue(fluidInput != null && fluidOutput != null,
                        "Ender fluid transmitters expose no fluid capability");
                if (fluidInput == null || fluidOutput == null) return;
                for (final Fluid fluid : java.util.List.of(
                        MagFluids.FERROFLUID.get(), MagFluids.HYDROGEN.get(),
                        MagFluids.TRITIUM.get(), MagFluids.HELIUM_3.get(),
                        MagFluids.HELIUM.get(), MagFluids.NEON.get(),
                        MagFluids.ARGON.get(), MagFluids.KRYPTON.get(),
                        MagFluids.XENON.get(), MagFluids.RADON.get())) {
                    final String name = BuiltInRegistries.FLUID.getKey(fluid).toString();
                    helper.assertTrue(fluidInput.fill(new FluidStack(fluid, 1000),
                                    IFluidHandler.FluidAction.EXECUTE) == 1000,
                            "Ender fluid transmitter rejected " + name);
                    final FluidStack receivedFluid = fluidOutput.drain(
                            1000, IFluidHandler.FluidAction.EXECUTE);
                    helper.assertTrue(receivedFluid.is(fluid) && receivedFluid.getAmount() == 1000,
                            "Ender fluid transmission did not preserve " + name);
                }
                helper.succeed();
            } finally {
                MagConfig.ENDER_TRANSMISSION_COMPAT_ENABLED.set(compat);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void experimentalFieldRelayIsOffByDefaultAndWorksOneHop(
            final GameTestHelper helper) {
        final boolean compat = MagConfig.ENDER_TRANSMISSION_COMPAT_ENABLED.get();
        final boolean relay = MagConfig.ENDER_TRANSMISSION_FIELD_RELAY_ENABLED.get();
        final BlockPos sourceTransmitter = new BlockPos(1, 2, 2);
        final BlockPos sourceMagnet = new BlockPos(2, 2, 2);
        final BlockPos destinationTransmitter = new BlockPos(8, 2, 2);
        helper.assertTrue(!MagConfig.enderTransmissionFieldRelayEnabled(),
                "Experimental Ender field relay is not disabled by default");
        helper.setBlock(sourceTransmitter, block("energy_transmitter"));
        helper.setBlock(destinationTransmitter, block("energy_transmitter"));
        helper.setBlock(sourceMagnet, MagBlocks.PERMANENT_MAGNET.get());
        helper.runAfterDelay(5, () -> {
            final BlockPos sourceAbsolute = helper.absolutePos(sourceTransmitter);
            final BlockPos magnetAbsolute = helper.absolutePos(sourceMagnet);
            final BlockPos destinationAbsolute = helper.absolutePos(destinationTransmitter);
            final ItemEntity target = new ItemEntity(helper.getLevel(),
                    destinationAbsolute.getX() + 2.5d,
                    destinationAbsolute.getY() + 0.5d,
                    destinationAbsolute.getZ() + 0.5d,
                    new ItemStack(Items.IRON_INGOT));
            target.setNoGravity(true);
            target.setDeltaMovement(Vec3.ZERO);
            helper.getLevel().addFreshEntity(target);
            try {
                MagConfig.ENDER_TRANSMISSION_COMPAT_ENABLED.set(true);
                configure(helper.getBlockEntity(sourceTransmitter), 9, "magnetization_fields");
                configure(helper.getBlockEntity(destinationTransmitter), 9, "magnetization_fields");
                invoke(helper.getBlockEntity(sourceTransmitter), "afterReload");
                invoke(helper.getBlockEntity(destinationTransmitter), "afterReload");
                EmitterRegistry.register(helper.getLevel(), magnetAbsolute);
                EmitterRegistry.register(helper.getLevel(), sourceAbsolute);
                EmitterRegistry.register(helper.getLevel(), destinationAbsolute);
                helper.assertTrue(MagneticFields.nearestField(helper.getLevel(),
                                Vec3.atCenterOf(sourceAbsolute)) != null,
                        "Source transmitter could not resolve its nearby Permanent Magnet field");
                helper.assertTrue(MagneticFields.nearestField(helper.getLevel(), target.position()) == null,
                        "Relay target is already inside the original local field");

                MagConfig.ENDER_TRANSMISSION_FIELD_RELAY_ENABLED.set(false);
                EnderFieldRelayCompat.apply(helper.getLevel());
                helper.assertTrue(target.getDeltaMovement().lengthSqr() < 1.0e-12,
                        "Disabled Ender field relay moved a remote item");
                MagConfig.ENDER_TRANSMISSION_FIELD_RELAY_ENABLED.set(true);
                EnderFieldRelayCompat.apply(helper.getLevel());
                helper.assertTrue(target.getDeltaMovement().lengthSqr() > 1.0e-8,
                        "Enabled Ender field relay did not project the source field one hop");
                helper.succeed();
            } finally {
                MagConfig.ENDER_TRANSMISSION_COMPAT_ENABLED.set(compat);
                MagConfig.ENDER_TRANSMISSION_FIELD_RELAY_ENABLED.set(relay);
                EmitterRegistry.unregister(helper.getLevel(), sourceAbsolute);
                EmitterRegistry.unregister(helper.getLevel(), destinationAbsolute);
                target.discard();
            }
        });
    }

    private static void configure(final net.minecraft.world.level.block.entity.BlockEntity transmitter,
                                  final int channel, final String password) {
        transmitter.getPersistentData().putInt("channel", channel);
        transmitter.getPersistentData().putString("password", password);
        invoke(transmitter, "reloadSettings");
    }

    private static Object invoke(final Object target, final String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Ender Transmission runtime contract changed: " + method,
                    exception);
        }
    }

    private static Block block(final String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(
                "createendertransmission", path));
    }
}
