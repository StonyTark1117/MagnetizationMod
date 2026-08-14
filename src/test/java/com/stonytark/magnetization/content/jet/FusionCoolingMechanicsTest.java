package com.stonytark.magnetization.content.jet;

import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.fluid.CoolantFluids;
import com.stonytark.magnetization.content.tokamak.TokamakControllerBlockEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FusionCoolingMechanicsTest {
    @Test
    void fusionThrusterKeepsBaselineDryAndImprovesEveryCooledMetric() {
        final int cells = 3;
        final int baselineFe = MagConfig.fusionThrusterFeCostBase()
                + MagConfig.fusionThrusterFeCostPerInterior() * cells;
        final int baselineFluid = MagConfig.fusionThrusterFluidPerTickBase()
                + MagConfig.fusionThrusterFluidPerTickPerInterior() * cells;
        final var dry = FusionThrusterBlockEntity.operatingProfile(cells, false);
        final var cooled = FusionThrusterBlockEntity.operatingProfile(cells, true);

        assertEquals(baselineFe, dry.feCost());
        assertEquals(baselineFluid, dry.nominalFluidCost());
        assertEquals(1.0d, dry.powerMultiplier());
        assertEquals(1.0d, dry.speedMultiplier());
        assertTrue(cooled.feCost() < dry.feCost());
        assertTrue(cooled.nominalFluidCost() < dry.nominalFluidCost());
        assertTrue(cooled.powerMultiplier() > dry.powerMultiplier());
        assertTrue(cooled.speedMultiplier() > dry.speedMultiplier());
    }

    @Test
    void tokamakKeepsBaselineDryAndBoostsPowerAndFuelLifeWhenCooled() {
        final var dry = TokamakControllerBlockEntity.coolingProfile(false);
        final var cooled = TokamakControllerBlockEntity.coolingProfile(true);

        assertEquals(1.0d, dry.powerMultiplier());
        assertEquals(1.0d, dry.fuelEfficiencyMultiplier());
        assertTrue(cooled.powerMultiplier() > dry.powerMultiplier());
        assertTrue(cooled.fuelEfficiencyMultiplier() > dry.fuelEfficiencyMultiplier());
    }

    @Test
    void higherQualityCoolantMovesBothMachinesFurtherAlongTheEfficiencyCurve() {
        final var waterThruster = FusionThrusterBlockEntity.operatingProfile(3, true, 1.0d);
        final var galliumThruster = FusionThrusterBlockEntity.operatingProfile(3, true, 1.75d);
        assertTrue(galliumThruster.feCost() < waterThruster.feCost());
        assertTrue(galliumThruster.nominalFluidCost() < waterThruster.nominalFluidCost());
        assertTrue(galliumThruster.powerMultiplier() > waterThruster.powerMultiplier());
        assertTrue(galliumThruster.speedMultiplier() > waterThruster.speedMultiplier());

        final var waterTokamak = TokamakControllerBlockEntity.coolingProfile(true, 1.0d);
        final var galliumTokamak = TokamakControllerBlockEntity.coolingProfile(true, 1.75d);
        assertTrue(galliumTokamak.powerMultiplier() > waterTokamak.powerMultiplier());
        assertTrue(galliumTokamak.fuelEfficiencyMultiplier() > waterTokamak.fuelEfficiencyMultiplier());
        assertTrue(CoolantFluids.consumptionForQuality(100, 1.75d)
                < CoolantFluids.consumptionForQuality(100, 1.0d));
    }
}
