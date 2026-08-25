package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.permanent.PermanentMagnetBlockEntity;
import com.stonytark.magnetization.physics.EmitterRegistry;
import com.stonytark.magnetization.registry.MagBlocks;
import dev.silvergold.simulatedcoasters.track.graph.CoasterPathEdge;
import net.antopfr.coastersmagnetized.magnet.AnchorMode;
import net.antopfr.coastersmagnetized.magnet.MagnetBoost;
import net.antopfr.coastersmagnetized.magnet.MagnetizedAnchors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
        final BlockPos from = helper.absolutePos(fromRel);
        final BlockPos to = helper.absolutePos(toRel);
        final var level = helper.getLevel();
        try {
            helper.setBlock(magnetRel, MagBlocks.PERMANENT_MAGNET.get());
            final PermanentMagnetBlockEntity magnet = (PermanentMagnetBlockEntity)
                    level.getBlockEntity(helper.absolutePos(magnetRel));
            EmitterRegistry.register(level, helper.absolutePos(magnetRel));
            PermanentMagnetBlockEntity.serverTick(level, helper.absolutePos(magnetRel),
                    magnet.getBlockState(), magnet);
            MagnetizedAnchors.setMode(level, from, AnchorMode.MAGNET);
            MagnetizedAnchors.setMode(level, to, AnchorMode.MAGNET);
            final CoasterPathEdge edge = CoasterPathEdge.straight(from, to,
                    Vec3.atCenterOf(from), Vec3.atCenterOf(to));

            MagConfig.COASTERS_MAGNETIZED_COMPAT_ENABLED.set(true);
            MagConfig.COASTERS_MAGNETIZED_FIELD_POWER_ENABLED.set(true);
            final double fieldOnly = MagnetBoost.accelerationFor(level, edge);
            helper.assertTrue(fieldOnly > 0.0d,
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

            MagConfig.COASTERS_MAGNETIZED_COMPAT_ENABLED.set(false);
            helper.assertTrue(!MagConfig.coastersMagnetizedFieldPowerEnabled(),
                    "Coasters: Magnetized master did not suppress field power");
            helper.succeed();
        } finally {
            MagConfig.COASTERS_MAGNETIZED_COMPAT_ENABLED.set(master);
            MagConfig.COASTERS_MAGNETIZED_FIELD_POWER_ENABLED.set(fieldPower);
            MagnetizedAnchors.setMode(level, from, AnchorMode.NONE);
            MagnetizedAnchors.setMode(level, to, AnchorMode.NONE);
            EmitterRegistry.unregister(level, helper.absolutePos(magnetRel));
        }
    }
}
