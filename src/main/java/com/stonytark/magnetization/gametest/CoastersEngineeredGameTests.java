package com.stonytark.magnetization.gametest;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.emp.EmpChargeBlock;
import com.stonytark.magnetization.data.CompatConfigCondition;
import dev.jwaterfall.coastersengineered.CoastersEngineeredConfig;
import dev.jwaterfall.coastersengineered.CoastersEngineeredRegistry;
import dev.jwaterfall.coastersengineered.MotorForce;
import dev.jwaterfall.coastersengineered.TrackEnergy;
import dev.jwaterfall.coastersengineered.TrackKind;
import dev.jwaterfall.coastersengineered.compat.JadePlugin;
import dev.jwaterfall.coastersengineered.controller.LinearMotorControllerBlockEntity;
import dev.jwaterfall.coastersengineered.span.AnchorEnergyBehaviour;
import dev.jwaterfall.coastersengineered.span.RunSummary;
import dev.jwaterfall.coastersengineered.span.SpanSegment;
import dev.jwaterfall.coastersengineered.span.TrackSpan;
import dev.jwaterfall.coastersengineered.span.TrackSpanBehaviour;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.silvergold.simulatedcoasters.SimulatedCoastersBlocks;
import dev.silvergold.simulatedcoasters.track.cart.CoasterCartSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;

/** Recipe and energy-contract checks against Coasters: Engineered 1.0.1. */
@GameTestHolder("magnetization_coasters_engineered")
@PrefixGameTestTemplate(false)
public final class CoastersEngineeredGameTests {
    private CoastersEngineeredGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void linearMotorRecipeConditionAndOverlayContractsRemainAvailable(
            final GameTestHelper helper) {
        final boolean master = MagConfig.COASTERS_ENGINEERED_COMPAT_ENABLED.get();
        final boolean recipes = MagConfig.COASTERS_ENGINEERED_RECIPES_ENABLED.get();
        final BlockPos anchorPos = new BlockPos(2, 2, 2);
        final BlockPos controllerPos = new BlockPos(4, 2, 2);
        try {
            final ResourceLocation recipe = ResourceLocation.fromNamespaceAndPath(
                    "magnetization", "coasters_engineered_linear_motor_from_magnetic_alloy");
            helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(recipe).isPresent(),
                    "Magnetic-alloy Linear Motor recipe did not load");

            MagConfig.COASTERS_ENGINEERED_COMPAT_ENABLED.set(true);
            MagConfig.COASTERS_ENGINEERED_RECIPES_ENABLED.set(false);
            helper.assertTrue(!new CompatConfigCondition(CompatConfigCondition.Feature.COASTERS_ENGINEERED)
                            .test(ICondition.IContext.EMPTY),
                    "Coasters: Engineered recipe condition ignored its recipe switch");
            MagConfig.COASTERS_ENGINEERED_RECIPES_ENABLED.set(true);
            MagConfig.COASTERS_ENGINEERED_COMPAT_ENABLED.set(false);
            helper.assertTrue(!MagConfig.coastersEngineeredRecipesEnabled(),
                    "Coasters: Engineered master did not suppress recipes");

            helper.setBlock(anchorPos, SimulatedCoastersBlocks.COASTER_ANCHORPOINT.get());
            final var anchor = helper.getBlockEntity(anchorPos);
            final AnchorEnergyBehaviour energy = AnchorEnergyBehaviour.of(anchor);
            helper.assertTrue(energy != null && energy.canReceive() && energy.canExtract(),
                    "Engineered anchor energy is no longer standard extractable FE");
            helper.assertTrue(energy.receiveEnergy(5_000, false) == 5_000,
                    "Engineered anchor rejected FE");
            helper.assertTrue(energy.extractEnergy(Integer.MAX_VALUE, false) == 5_000
                            && energy.getEnergyStored() == 0,
                    "Engineered anchor FE could not be drained through its native contract");

            helper.setBlock(controllerPos, CoastersEngineeredRegistry.LINEAR_MOTOR_CONTROLLER.get());
            final var controller = helper.getBlockEntity(controllerPos);
            helper.assertTrue(controller instanceof LinearMotorControllerBlockEntity
                            && controller instanceof IHaveGoggleInformation,
                    "Linear Motor Controller lost its native controller/goggles contract");
            helper.assertTrue(RunSummary.of(anchor) != null,
                    "Engineered run-summary overlay rejected the native anchor");
            helper.assertTrue(JadePlugin.RunProvider.INSTANCE.getUid() != null
                            && JadePlugin.RunProvider.INSTANCE.getUid().getNamespace()
                            .equals("coastersengineered"),
                    "Engineered Jade provider no longer owns its run overlay");
            helper.succeed();
        } finally {
            MagConfig.COASTERS_ENGINEERED_COMPAT_ENABLED.set(master);
            MagConfig.COASTERS_ENGINEERED_RECIPES_ENABLED.set(recipes);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void empMotorConsumptionAndRegenerationUseNativeEnergyContract(
            final GameTestHelper helper) {
        final boolean requireEnergy = CoastersEngineeredConfig.REQUIRE_ENERGY.get();
        final double energyPerJoule = CoastersEngineeredConfig.ENERGY_PER_JOULE.get();
        final double regenEfficiency = CoastersEngineeredConfig.REGEN_EFFICIENCY.get();
        final BlockPos anchorRel = new BlockPos(3, 3, 3);
        final BlockPos empRel = anchorRel.west(2);
        helper.setBlock(anchorRel, SimulatedCoastersBlocks.COASTER_ANCHORPOINT.get());
        final ServerSubLevel cart = CoasterCartSpawner.spawnMinimalContraption(helper.getLevel(),
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(7, 8, 7))), new Quaterniond());
        if (cart == null) {
            remove(helper, cart);
            helper.fail("Could not construct Engineered anchor/run/cart fixture");
            return;
        }
        // Retain only a JDK UUID across the delayed boundary. Capturing an
        // Engineered object here would put the optional class in javac's
        // synthetic lambda signature and break the absent-mod GameTest scanner.
        final java.util.UUID cartId = cart.getUniqueId();
        helper.runAfterDelay(12L, () -> {
            final var anchor = helper.getBlockEntity(anchorRel);
            final AnchorEnergyBehaviour energy = AnchorEnergyBehaviour.of(anchor);
            final TrackSpanBehaviour run = TrackSpanBehaviour.of(anchor, TrackKind.MOTOR);
            final var container = SubLevelContainer.getContainer(helper.getLevel());
            final var resolved = container == null ? null : container.getSubLevel(cartId);
            final ServerSubLevel liveCart = resolved instanceof ServerSubLevel server ? server : null;
            try {
                helper.assertTrue(energy != null && run != null && liveCart != null,
                        "Engineered anchor/run/cart fixture did not survive initialization");
                if (energy == null || run == null || liveCart == null) return;
                final BlockPos edgeFrom = helper.absolutePos(anchorRel.north(2));
                final BlockPos edgeTo = helper.absolutePos(anchorRel.south(2));
                helper.assertTrue(run.setSpan(new TrackSpan(List.of(
                                new SpanSegment(edgeFrom, edgeTo, 0.0d, 1.0d)))),
                        "Engineered motor rejected a non-empty native track span");
                CoastersEngineeredConfig.REQUIRE_ENERGY.set(true);
                final var exposed = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                        helper.absolutePos(anchorRel), null);
                helper.assertTrue(exposed != null && exposed.canExtract() && exposed.canReceive(),
                        "Engineered anchor no longer publishes standard bidirectional FE");
                energy.receiveEnergy(20_000, false);
                helper.setBlock(empRel, com.stonytark.magnetization.registry.MagBlocks.EMP_CHARGE.get());
                EmpChargeBlock.detonate(helper.getLevel(), helper.absolutePos(empRel));
                helper.assertTrue(energy.getEnergyStored() == 0,
                        "A real Magnetization EMP did not drain Engineered's extractable FE");

                CoastersEngineeredConfig.ENERGY_PER_JOULE.set(1.0d);
                CoastersEngineeredConfig.REGEN_EFFICIENCY.set(0.5d);
                CoastersEngineeredConfig.REQUIRE_ENERGY.set(false);
                helper.assertTrue(TrackEnergy.throttle(run, 500.0d) == 1.0d,
                        "Native motor unexpectedly required FE while its setting was disabled");

                CoastersEngineeredConfig.REQUIRE_ENERGY.set(true);
                helper.assertTrue(TrackEnergy.throttle(run, 500.0d) == 0.0d,
                        "Empty native motor did not report energy starvation");
                final var physics = container.physicsSystem();
                helper.assertTrue(MotorForce.mass(liveCart) > 0.0d,
                        "Native Engineered motor lost its Sable mass-based force input");
                helper.assertTrue(MotorForce.accelerate(run, liveCart, physics, 0.05d,
                                new Vector3d(1, 0, 0), 0.0d, 5.0d) == MotorForce.Outcome.STARVED,
                        "Empty native motor did not preserve its STARVED outcome");

                energy.receiveEnergy(20_000, false);
                final int beforeDrive = energy.getEnergyStored();
                helper.assertTrue(MotorForce.accelerate(run, liveCart, physics, 0.05d,
                                new Vector3d(1, 0, 0), 0.0d, 5.0d) == MotorForce.Outcome.APPLIED,
                        "Powered native motor did not apply mass-based acceleration");
                helper.assertTrue(energy.getEnergyStored() < beforeDrive,
                        "Native acceleration did not consume FE");

                energy.extractEnergy(Integer.MAX_VALUE, false);
                helper.assertTrue(MotorForce.decelerate(run, liveCart, physics, 0.05d,
                                new Vector3d(1, 0, 0), 5.0d, 0.0d) == MotorForce.Outcome.APPLIED,
                        "Native motor did not apply mass-based braking");
                helper.assertTrue(energy.getEnergyStored() > 0,
                        "Native braking did not regenerate FE");
                helper.succeed();
            } finally {
                CoastersEngineeredConfig.REQUIRE_ENERGY.set(requireEnergy);
                CoastersEngineeredConfig.ENERGY_PER_JOULE.set(energyPerJoule);
                CoastersEngineeredConfig.REGEN_EFFICIENCY.set(regenEfficiency);
                remove(helper, cartId);
            }
        });
    }

    private static void remove(final GameTestHelper helper, final ServerSubLevel cart) {
        final SubLevelContainer container = SubLevelContainer.getContainer(helper.getLevel());
        if (container != null && cart != null
                && container.getSubLevel(cart.getUniqueId()) != null) {
            container.removeSubLevel(cart, SubLevelRemovalReason.REMOVED);
        }
    }

    private static void remove(final GameTestHelper helper, final java.util.UUID cartId) {
        final SubLevelContainer container = SubLevelContainer.getContainer(helper.getLevel());
        if (container == null) return;
        final var cart = container.getSubLevel(cartId);
        if (cart instanceof ServerSubLevel server) {
            container.removeSubLevel(server, SubLevelRemovalReason.REMOVED);
        }
    }
}
