package com.stonytark.magnetization.gametest;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagEffects;
import com.stonytark.magnetization.registry.MagFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Published-runtime checks for Create: Diesel Generators. */
@GameTestHolder("magnetization_diesel_generators")
@PrefixGameTestTemplate(false)
public final class DieselGeneratorsGameTests {
    private DieselGeneratorsGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "dieselGeneratorCompat")
    public static void enginesUseKineticsAndFluidMachineryIsExplicitlyExcluded(
            final GameTestHelper helper) {
        final ResourceLocation commonRecipe = ResourceLocation.fromNamespaceAndPath(
                "magnetization", "ferrofluid_from_plant_oil");
        helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(commonRecipe).isPresent(),
                "Diesel Generators did not reuse the common c:plantoil Ferrofluid recipe");
        final BlockPos enginePos = new BlockPos(2, 2, 2);
        final BlockPos modularPos = new BlockPos(4, 2, 2);
        helper.setBlock(enginePos, block("diesel_engine"));
        helper.setBlock(modularPos, block("large_diesel_engine"));
        final var engine = helper.getBlockEntity(enginePos);
        final var modular = helper.getBlockEntity(modularPos);

        helper.assertTrue(engine instanceof GeneratingKineticBlockEntity,
                "Diesel engine no longer exposes Create's kinetic generator contract");
        helper.assertTrue(engine instanceof IHaveGoggleInformation,
                "Diesel engine lost its native Create goggles information");
        helper.assertTrue(modular instanceof GeneratingKineticBlockEntity,
                "Industrial modular diesel engine is not a Create kinetic generator");
        helper.assertTrue(block("diesel_engine").defaultBlockState().is(MagTags.FERROMAGNETIC_BLOCKS),
                "Diesel engine casing does not contribute ship susceptibility");
        helper.assertTrue(block("large_diesel_engine").defaultBlockState().is(MagTags.FERROMAGNETIC_BLOCKS),
                "Industrial diesel multiblock does not contribute ship susceptibility");
        helper.assertTrue(block("powered_engine_shaft").defaultBlockState().is(MagTags.FERROMAGNETIC_BLOCKS),
                "Huge-engine powered shaft is missing magnetic material parity");
        for (final String path : new String[]{"diesel_engine", "large_diesel_engine",
                "huge_diesel_engine", "powered_engine_shaft", "chemical_turret"}) {
            helper.assertTrue(block(path).defaultBlockState().is(MagTags.EDDY_CONDUCTORS),
                    "Diesel Generators metal machine is not an eddy conductor: " + path);
        }

        assertExcluded(helper, "distillation_tank");
        assertExcluded(helper, "bulk_fermenter");
        assertExcluded(helper, "oil_barrel");
        assertExcluded(helper, "pumpjack_bearing");
        assertExcluded(helper, "pumpjack_bearing_b");
        assertExcluded(helper, "pumpjack_head");
        assertExcluded(helper, "pumpjack_crank");
        assertExcluded(helper, "canister");
        assertExcluded(helper, "gasoline");
        assertExcluded(helper, "diesel");
        assertExcluded(helper, "crude_oil");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void ferrofluidChemicalProjectileAppliesMagnetized(final GameTestHelper helper) {
        final boolean compat = MagConfig.DIESEL_GENERATORS_COMPAT_ENABLED.get();
        final boolean spray = MagConfig.DIESEL_GENERATORS_FERROFLUID_SPRAY_ENABLED.get();
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                "createdieselgenerators", "chemical_sprayer_projectile");
        final EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        final Entity projectile = type.create(helper.getLevel());
        helper.assertTrue(projectile != null, "Could not create Diesel Generators chemical projectile");
        if (projectile == null) return;
        final ArmorStand target = new ArmorStand(helper.getLevel(),
                helper.absolutePos(new BlockPos(3, 2, 3)).getX(),
                helper.absolutePos(new BlockPos(3, 2, 3)).getY(),
                helper.absolutePos(new BlockPos(3, 2, 3)).getZ());
        try {
            MagConfig.DIESEL_GENERATORS_COMPAT_ENABLED.set(true);
            MagConfig.DIESEL_GENERATORS_FERROFLUID_SPRAY_ENABLED.set(true);
            final var fluidField = projectile.getClass().getField("stack");
            fluidField.set(projectile, new FluidStack(MagFluids.FERROFLUID.get(), 1000));
            final var hit = projectile.getClass().getDeclaredMethod("onHitEntity", EntityHitResult.class);
            hit.setAccessible(true);
            hit.invoke(projectile, new EntityHitResult(target));
            helper.assertTrue(target.hasEffect(MagEffects.MAGNETIZED),
                    "Ferrofluid chemical spray did not apply Magnetized");
            target.removeEffect(MagEffects.MAGNETIZED);
            MagConfig.DIESEL_GENERATORS_COMPAT_ENABLED.set(false);
            hit.invoke(projectile, new EntityHitResult(target));
            helper.assertTrue(!target.hasEffect(MagEffects.MAGNETIZED),
                    "Diesel Generators master switch did not disable Ferrofluid spray behavior");
            helper.succeed();
        } catch (final ReflectiveOperationException exception) {
            helper.fail("Diesel Generators projectile contract changed: " + exception);
        } finally {
            MagConfig.DIESEL_GENERATORS_COMPAT_ENABLED.set(compat);
            MagConfig.DIESEL_GENERATORS_FERROFLUID_SPRAY_ENABLED.set(spray);
            projectile.discard();
            target.discard();
        }
    }

    private static void assertExcluded(final GameTestHelper helper, final String id) {
        final var state = block(id).defaultBlockState();
        helper.assertTrue(state.is(MagTags.MAGNETIC_SUSCEPTIBILITY_EXCLUDED),
                "Diesel fuel/fluid machinery is not explicitly susceptibility-excluded: " + id);
        helper.assertTrue(!state.is(MagTags.FERROMAGNETIC_BLOCKS),
                "Diesel fuel/fluid machinery was incorrectly tagged ferromagnetic: " + id);
    }

    private static Block block(final String path) {
        return BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("createdieselgenerators", path));
    }
}
