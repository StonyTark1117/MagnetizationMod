package com.stonytark.magnetization.gametest;

import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.compat.FerromagneticCompat;
import com.stonytark.magnetization.compat.steamrails.MagSteamRailsCompat;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Isolated checks against the published Steam 'n' Rails NeoForge port. */
@GameTestHolder("magnetization_steam_rails")
@PrefixGameTestTemplate(false)
public final class SteamRailsGameTests {
    private SteamRailsGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "steamRailsTrainCompat")
    public static void coupledTrainReceivesOneRailProjectedImpulse(final GameTestHelper helper) {
        final boolean originalEnabled = MagConfig.STEAM_N_RAILS_FIELD_REACTION.get();
        final boolean originalMaster = MagConfig.STEAM_N_RAILS_COMPAT_ENABLED.get();
        final double originalSusceptibility = MagConfig.STEAM_N_RAILS_TRAIN_SUSCEPTIBILITY.get();
        try {
            helper.assertTrue(classPresent("com.railwayteam.railways.content.coupling.TrainUtils"),
                    "Published Steam 'n' Rails coupling runtime is absent");
            helper.assertTrue(classPresent("com.railwayteam.railways.ponder.CRPonderPlugin"),
                    "Published Steam 'n' Rails Ponder plugin is absent");
            final var coupler = BuiltInRegistries.BLOCK.get(
                    ResourceLocation.fromNamespaceAndPath("railways", "track_coupler"));
            final var locometal = BuiltInRegistries.BLOCK.get(
                    ResourceLocation.fromNamespaceAndPath("railways", "riveted_locometal"));
            helper.assertTrue(coupler.defaultBlockState().is(MagTags.FERROMAGNETIC_BLOCKS),
                    "Steam 'n' Rails track coupler is missing magnetic material parity");
            helper.assertTrue(locometal.defaultBlockState().is(MagTags.FERROMAGNETIC_BLOCKS),
                    "Steam 'n' Rails locometal tag is not included in magnetic materials");

            final Train train = new Train(UUID.randomUUID(), UUID.randomUUID(), null,
                    List.of(), List.of(), false, 0);
            final HashSet<UUID> affected = new HashSet<>();
            MagConfig.STEAM_N_RAILS_FIELD_REACTION.set(true);
            MagConfig.STEAM_N_RAILS_TRAIN_SUSCEPTIBILITY.set(1.0d);
            helper.assertTrue(MagSteamRailsCompat.applyProjectedForce(
                            train, new Vec3(1, 0, 0), new Vec3(10, 4, 0), affected),
                    "Rail-aligned magnetic force did not accelerate the train");
            final double afterFirstCar = train.speed;
            helper.assertTrue(afterFirstCar > 0.0d,
                    "Train speed did not increase along the rail tangent");
            helper.assertTrue(!MagSteamRailsCompat.applyProjectedForce(
                            train, new Vec3(1, 0, 0), new Vec3(10, 0, 0), affected),
                    "A second coupled carriage applied the same field twice");
            helper.assertTrue(train.speed == afterFirstCar,
                    "Coupled-car deduplication changed shared train speed twice");

            final CarriageContraptionEntity carriage = new CarriageContraptionEntity(
                    AllEntityTypes.CARRIAGE_CONTRAPTION.get(), helper.getLevel());
            helper.assertTrue(!MagSteamRailsCompat.structuralInducerCanAdopt(carriage),
                    "Structural Inducer accepted an assembled rail-bound carriage entity");

            MagConfig.STEAM_N_RAILS_COMPAT_ENABLED.set(false);
            helper.assertTrue(!MagConfig.steamRailsFieldReaction()
                            && MagSteamRailsCompat.structuralInducerCanAdopt(carriage)
                            && !FerromagneticCompat.isFerromagnetic(coupler.defaultBlockState())
                            && !FerromagneticCompat.isFerromagnetic(locometal.defaultBlockState()),
                    "Steam 'n' Rails master did not suppress force, inducer, and material integration");
            MagConfig.STEAM_N_RAILS_COMPAT_ENABLED.set(true);

            MagConfig.STEAM_N_RAILS_FIELD_REACTION.set(false);
            helper.assertTrue(!MagSteamRailsCompat.applyProjectedForce(
                            train, new Vec3(1, 0, 0), new Vec3(10, 0, 0), new HashSet<>()),
                    "Disabled Steam 'n' Rails field compatibility still changed the train");
            helper.succeed();
        } finally {
            MagConfig.STEAM_N_RAILS_FIELD_REACTION.set(originalEnabled);
            MagConfig.STEAM_N_RAILS_COMPAT_ENABLED.set(originalMaster);
            MagConfig.STEAM_N_RAILS_TRAIN_SUSCEPTIBILITY.set(originalSusceptibility);
        }
    }

    private static boolean classPresent(final String name) {
        try {
            Class.forName(name, false, SteamRailsGameTests.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
