package com.stonytark.magnetization.content.jet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThrusterPlumeTest {

    @Test
    void plumeAlwaysStartsBeyondAndMovesOutOfNozzle() {
        final BlockPos origin = new BlockPos(4, 7, -2);
        for (final Direction exhaust : Direction.values()) {
            final Vec3 normal = Vec3.atLowerCornerOf(exhaust.getNormal());
            final Vec3 centre = Vec3.atCenterOf(origin);
            final Vec3 point = ThrusterPlume.plumePoint(origin, exhaust, 0.75d, 0.11d, -0.07d);
            final Vec3 velocity = ThrusterPlume.plumeVelocity(exhaust, 0.24d, 0.01d, -0.02d);

            assertEquals(1.31d, point.subtract(centre).dot(normal), 1.0e-9,
                    () -> exhaust + " plume point must extend from the facing side");
            assertEquals(0.24d, velocity.dot(normal), 1.0e-9,
                    () -> exhaust + " plume velocity must point along the exhaust facing");
            assertTrue(point.subtract(centre).subtract(normal.scale(1.31d)).length() > 0.0d,
                    () -> exhaust + " plume should retain transverse spread");
        }
    }

    @Test
    void packedColourConvertsToNormalisedRgb() {
        final var rgb = ThrusterPlume.colourVector(0x804020);
        assertEquals(128.0f / 255.0f, rgb.x(), 1.0e-6f);
        assertEquals(64.0f / 255.0f, rgb.y(), 1.0e-6f);
        assertEquals(32.0f / 255.0f, rgb.z(), 1.0e-6f);
    }

    @Test
    void fusionSamplingScalesWithPanelWithoutUnboundedParticleLoad() {
        assertEquals(3, ThrusterPlume.samplingDivisor(ThrusterPlume.Style.FUSION, 1));
        assertEquals(3, ThrusterPlume.samplingDivisor(ThrusterPlume.Style.FUSION, 36));
        assertEquals(84, ThrusterPlume.samplingDivisor(ThrusterPlume.Style.FUSION, 1_000));
        assertEquals(1, ThrusterPlume.samplingDivisor(ThrusterPlume.Style.ION, Integer.MAX_VALUE));
    }

    @Test
    void cooledFusionLayerHasAnIndependentPanelWideCap() {
        assertEquals(4, ThrusterPlume.cooledSamplingDivisor(1));
        assertEquals(4, ThrusterPlume.cooledSamplingDivisor(36));
        assertEquals(84, ThrusterPlume.cooledSamplingDivisor(1_000));
        assertEquals(178_956_971, ThrusterPlume.cooledSamplingDivisor(Integer.MAX_VALUE));
    }

    @Test
    void activeThrustersUseFiringStatusAndFusionUsesVisibleState() {
        for (final var kind : java.util.List.of(
                com.stonytark.magnetization.menu.MachineMenu.Kind.JET,
                com.stonytark.magnetization.menu.MachineMenu.Kind.THRUSTER,
                com.stonytark.magnetization.menu.MachineMenu.Kind.ION_THRUSTER,
                com.stonytark.magnetization.menu.MachineMenu.Kind.FUSION_THRUSTER)) {
            final var line = com.stonytark.magnetization.menu.MachineGuiData.statusLine(kind,
                    com.stonytark.magnetization.menu.MachineDisplayData.Status.ACTIVE);
            assertTrue(line.getContents() instanceof TranslatableContents contents
                            && contents.getKey().equals("tooltip.magnetization.machine_firing"),
                    () -> kind + " should label its active state as firing");
        }

        assertEquals(com.stonytark.magnetization.menu.MachineDisplayData.Status.INVALID,
                FusionThrusterBlockEntity.displayStatus(false, true));
        assertEquals(com.stonytark.magnetization.menu.MachineDisplayData.Status.FORMED,
                FusionThrusterBlockEntity.displayStatus(true, false));
        assertEquals(com.stonytark.magnetization.menu.MachineDisplayData.Status.ACTIVE,
                FusionThrusterBlockEntity.displayStatus(true, true));
    }
}
