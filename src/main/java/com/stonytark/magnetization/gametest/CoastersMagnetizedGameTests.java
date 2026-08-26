package com.stonytark.magnetization.gametest;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.permanent.PermanentMagnetBlockEntity;
import com.stonytark.magnetization.physics.EmitterRegistry;
import com.stonytark.magnetization.registry.MagBlocks;
import dev.silvergold.simulatedcoasters.track.graph.CoasterPathEdge;
import dev.silvergold.simulatedcoasters.SimulatedCoastersBlocks;
import net.antopfr.coastersmagnetized.magnet.AnchorMode;
import net.antopfr.coastersmagnetized.magnet.MagnetBoost;
import net.antopfr.coastersmagnetized.magnet.MagnetizedAnchors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real-runtime checks for Coasters: Magnetized's redstone-or-field contract. */
@GameTestHolder("magnetization_coasters_magnetized")
@PrefixGameTestTemplate(false)
public final class CoastersMagnetizedGameTests {
    private CoastersMagnetizedGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "coastersMagnetizedPower")
    public static void fieldPowerIsAnAlternativeToRedstoneWithoutDoubleForce(final GameTestHelper helper) {
        final boolean master = MagConfig.COASTERS_MAGNETIZED_COMPAT_ENABLED.get();
        final boolean fieldPower = MagConfig.COASTERS_MAGNETIZED_FIELD_POWER_ENABLED.get();
        final BlockPos fromRel = new BlockPos(2, 2, 2);
        final BlockPos toRel = fromRel.east();
        final BlockPos magnetRel = fromRel.north();
        final BlockPos farFromRel = fromRel.south(18);
        final BlockPos farToRel = farFromRel.east();
        final BlockPos from = helper.absolutePos(fromRel);
        final BlockPos to = helper.absolutePos(toRel);
        final BlockPos farFrom = helper.absolutePos(farFromRel);
        final BlockPos farTo = helper.absolutePos(farToRel);
        final var level = helper.getLevel();
        try {
            helper.setBlock(fromRel, SimulatedCoastersBlocks.COASTER_ANCHORPOINT.get());
            helper.setBlock(toRel, SimulatedCoastersBlocks.COASTER_ANCHORPOINT.get());
            helper.setBlock(farFromRel, SimulatedCoastersBlocks.COASTER_ANCHORPOINT.get());
            helper.setBlock(farToRel, SimulatedCoastersBlocks.COASTER_ANCHORPOINT.get());
            helper.setBlock(magnetRel, MagBlocks.PERMANENT_MAGNET.get());
            final PermanentMagnetBlockEntity magnet = (PermanentMagnetBlockEntity)
                    level.getBlockEntity(helper.absolutePos(magnetRel));
            EmitterRegistry.register(level, helper.absolutePos(magnetRel));
            PermanentMagnetBlockEntity.serverTick(level, helper.absolutePos(magnetRel),
                    magnet.getBlockState(), magnet);
            MagnetizedAnchors.setMode(level, from, AnchorMode.MAGNET);
            MagnetizedAnchors.setMode(level, to, AnchorMode.MAGNET);
            final ScrollValueBehaviour fromValue = ((SmartBlockEntity) level.getBlockEntity(from))
                    .getBehaviour(ScrollValueBehaviour.TYPE);
            final ScrollValueBehaviour toValue = ((SmartBlockEntity) level.getBlockEntity(to))
                    .getBehaviour(ScrollValueBehaviour.TYPE);
            helper.assertTrue(fromValue != null && toValue != null,
                    "Coasters: Magnetized scroll-value behavior was not attached to anchors");
            fromValue.setValue(65);
            toValue.setValue(65);
            final CoasterPathEdge edge = CoasterPathEdge.straight(from, to,
                    Vec3.atCenterOf(from), Vec3.atCenterOf(to));
            final double configuredAcceleration = MagnetBoost.maxAcceleration();
            final double configuredSpeedCap = MagnetBoost.maxSpeed();

            MagConfig.COASTERS_MAGNETIZED_COMPAT_ENABLED.set(true);
            MagConfig.COASTERS_MAGNETIZED_FIELD_POWER_ENABLED.set(true);
            final double fieldOnly = MagnetBoost.accelerationFor(level, edge);
            helper.assertTrue(fieldOnly > 0.0d
                            && Math.abs(fieldOnly - configuredAcceleration * 0.65d) < 1.0e-9d,
                    "A live Magnetization field did not power the magnetic anchor");

            MagConfig.COASTERS_MAGNETIZED_FIELD_POWER_ENABLED.set(false);
            helper.assertTrue(MagnetBoost.accelerationFor(level, edge) == 0.0d,
                    "Disabled field-power compatibility still powered the anchor");

            helper.setBlock(fromRel.below(), Blocks.REDSTONE_BLOCK);
            helper.setBlock(toRel.below(), Blocks.REDSTONE_BLOCK);
            final double redstoneOnly = MagnetBoost.accelerationFor(level, edge);
            helper.assertTrue(redstoneOnly > 0.0d,
                    "Native redstone power stopped working when field power was disabled");
            MagConfig.COASTERS_MAGNETIZED_FIELD_POWER_ENABLED.set(true);
            final double combined = MagnetBoost.accelerationFor(level, edge);
            helper.assertTrue(Math.abs(combined - redstoneOnly) < 1.0e-9d,
                    "Redstone plus field power applied the addon's acceleration twice");

            helper.setBlock(fromRel.below(), Blocks.AIR);
            helper.setBlock(toRel.below(), Blocks.AIR);
            MagnetizedAnchors.setMode(level, from, AnchorMode.BRAKE);
            MagnetizedAnchors.setMode(level, to, AnchorMode.BRAKE);
            fromValue.setValue(40);
            toValue.setValue(40);
            final double brake = MagnetBoost.accelerationFor(level, edge);
            helper.assertTrue(brake < 0.0d
                            && Math.abs(brake + configuredAcceleration * 0.40d) < 1.0e-9d,
                    "Field power did not preserve the addon's selected brake mode/force");
            helper.assertTrue(MagnetizedAnchors.modeOf(level, from) == AnchorMode.BRAKE
                            && MagnetizedAnchors.modeOf(level, to) == AnchorMode.BRAKE,
                    "Field evaluation changed the addon's selected anchor mode");

            MagnetizedAnchors.setMode(level, farFrom, AnchorMode.MAGNET);
            MagnetizedAnchors.setMode(level, farTo, AnchorMode.MAGNET);
            final CoasterPathEdge farEdge = CoasterPathEdge.straight(farFrom, farTo,
                    Vec3.atCenterOf(farFrom), Vec3.atCenterOf(farTo));
            helper.assertTrue(MagnetBoost.accelerationFor(level, farEdge) == 0.0d,
                    "An out-of-range Magnetization field powered distant anchors");

            final CompoundTag saved = MagnetizedAnchors.get(level).save(
                    new CompoundTag(), level.registryAccess());
            helper.assertTrue(saved.getList("Anchors", Tag.TAG_COMPOUND).stream()
                            .map(tag -> ((CompoundTag) tag).getString("Mode"))
                            .anyMatch(AnchorMode.BRAKE.name()::equals),
                    "The addon's saved data did not persist the selected brake mode");
            helper.assertTrue(MagnetBoost.maxAcceleration() == configuredAcceleration
                            && MagnetBoost.maxSpeed() == configuredSpeedCap,
                    "Field power changed the addon's configured force or speed cap");

            MagConfig.COASTERS_MAGNETIZED_COMPAT_ENABLED.set(false);
            helper.assertTrue(!MagConfig.coastersMagnetizedFieldPowerEnabled(),
                    "Coasters: Magnetized master did not suppress field power");
            helper.succeed();
        } finally {
            MagConfig.COASTERS_MAGNETIZED_COMPAT_ENABLED.set(master);
            MagConfig.COASTERS_MAGNETIZED_FIELD_POWER_ENABLED.set(fieldPower);
            MagnetizedAnchors.setMode(level, from, AnchorMode.NONE);
            MagnetizedAnchors.setMode(level, to, AnchorMode.NONE);
            MagnetizedAnchors.setMode(level, farFrom, AnchorMode.NONE);
            MagnetizedAnchors.setMode(level, farTo, AnchorMode.NONE);
            EmitterRegistry.unregister(level, helper.absolutePos(magnetRel));
            level.removeBlock(farFrom, false);
            level.removeBlock(farTo, false);
        }
    }
}
