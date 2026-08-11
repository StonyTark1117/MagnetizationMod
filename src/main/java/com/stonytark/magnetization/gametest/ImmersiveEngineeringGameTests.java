package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.compat.ExternalFieldCompat;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Published-runtime checks for the optional Immersive Engineering bridge. */
@GameTestHolder("magnetization_immersiveengineering")
@PrefixGameTestTemplate(false)
public final class ImmersiveEngineeringGameTests {
    private ImmersiveEngineeringGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void poweredElectromagnetEmitsConfigurableField(final GameTestHelper helper) {
        final boolean compat = MagConfig.IMMERSIVE_ENGINEERING_COMPAT_ENABLED.get();
        final boolean fields = MagConfig.IMMERSIVE_ENGINEERING_FIELDS_ENABLED.get();
        final BlockPos magnet = new BlockPos(2, 2, 2);
        MagConfig.IMMERSIVE_ENGINEERING_COMPAT_ENABLED.set(true);
        MagConfig.IMMERSIVE_ENGINEERING_FIELDS_ENABLED.set(true);
        helper.setBlock(magnet, block("electromagnet"));
        helper.setBlock(magnet.west(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(4, () -> {
            try {
                final IEnergyStorage energy = energy(helper, magnet);
                helper.assertTrue(energy != null && energy.canReceive(),
                        "IE Electromagnet exposes no receiving FE capability");
                if (energy == null) return;
                energy.receiveEnergy(Math.min(energy.getMaxEnergyStored(), 100_000), false);
                final var field = ExternalFieldCompat.currentField(
                        helper.getLevel(), helper.absolutePos(magnet));
                helper.assertTrue(field != null && field.force() > 0.0d,
                        "Powered IE Electromagnet did not emit a Magnetization field");
                MagConfig.IMMERSIVE_ENGINEERING_COMPAT_ENABLED.set(false);
                helper.assertTrue(ExternalFieldCompat.currentField(
                                helper.getLevel(), helper.absolutePos(magnet)) == null,
                        "IE master compatibility switch did not suppress its field");
            } finally {
                MagConfig.IMMERSIVE_ENGINEERING_COMPAT_ENABLED.set(compat);
                MagConfig.IMMERSIVE_ENGINEERING_FIELDS_ENABLED.set(fields);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void chargedTeslaCoilEmitsPulsedField(final GameTestHelper helper) {
        final boolean compat = MagConfig.IMMERSIVE_ENGINEERING_COMPAT_ENABLED.get();
        final boolean fields = MagConfig.IMMERSIVE_ENGINEERING_FIELDS_ENABLED.get();
        final BlockPos coil = new BlockPos(2, 2, 2);
        final boolean[] observed = {false, false};
        MagConfig.IMMERSIVE_ENGINEERING_COMPAT_ENABLED.set(true);
        MagConfig.IMMERSIVE_ENGINEERING_FIELDS_ENABLED.set(true);
        helper.setBlock(coil, block("tesla_coil"));
        helper.setBlock(coil.west(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(3, () -> {
            final IEnergyStorage energy = energy(helper, coil);
            helper.assertTrue(energy != null && energy.canReceive(),
                    "IE Tesla Coil exposes no receiving FE capability");
            if (energy != null) energy.receiveEnergy(energy.getMaxEnergyStored(), false);
        });
        for (int delay = 5; delay < 25; delay++) {
            helper.runAfterDelay(delay, () -> {
                final var field = ExternalFieldCompat.currentField(
                        helper.getLevel(), helper.absolutePos(coil));
                observed[field == null ? 1 : 0] = true;
            });
        }
        helper.runAfterDelay(26, () -> {
            try {
                helper.assertTrue(observed[0],
                        "Charged IE Tesla Coil never emitted a field pulse");
                helper.assertTrue(observed[1],
                        "IE Tesla Coil field was continuous instead of pulsed");
            } finally {
                MagConfig.IMMERSIVE_ENGINEERING_COMPAT_ENABLED.set(compat);
                MagConfig.IMMERSIVE_ENGINEERING_FIELDS_ENABLED.set(fields);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void railgunShotReactionHonorsRuntimeControls(final GameTestHelper helper) {
        final boolean compat = MagConfig.IMMERSIVE_ENGINEERING_COMPAT_ENABLED.get();
        final boolean reaction = MagConfig.IMMERSIVE_ENGINEERING_RAILGUN_REACTION.get();
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                "immersiveengineering", "railgun_shot");
        final EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        helper.assertTrue(type != null, "Missing IE Railgun Shot entity type");
        if (type == null) return;
        final Entity shot = type.create(helper.getLevel());
        helper.assertTrue(shot != null, "Could not create IE Railgun Shot entity");
        if (shot == null) return;
        final Vec3 position = Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 5, 5)));
        shot.moveTo(position.x, position.y, position.z, 0.0f, 0.0f);
        shot.setNoGravity(true);
        shot.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(shot);
        final MagneticField field = new MagneticField(
                position.add(4.0d, 0.0d, 0.0d), new Vec3(1.0d, 0.0d, 0.0d),
                MagneticPolarity.SOUTH, MagneticStrength.EXTREME,
                MagneticField.Shape.OMNIDIRECTIONAL, 16.0d);
        try {
            MagConfig.IMMERSIVE_ENGINEERING_COMPAT_ENABLED.set(true);
            MagConfig.IMMERSIVE_ENGINEERING_RAILGUN_REACTION.set(false);
            FieldApplicator.applyEntitiesOnly(helper.getLevel(), field);
            helper.assertTrue(shot.getDeltaMovement().lengthSqr() < 1.0e-12,
                    "Disabled IE Railgun reaction still changed shot velocity");
            MagConfig.IMMERSIVE_ENGINEERING_RAILGUN_REACTION.set(true);
            FieldApplicator.applyEntitiesOnly(helper.getLevel(), field);
            helper.assertTrue(shot.getDeltaMovement().lengthSqr() > 1.0e-8,
                    "Enabled IE Railgun reaction did not move the shot");
            MagConfig.IMMERSIVE_ENGINEERING_COMPAT_ENABLED.set(false);
            helper.assertTrue(!MagConfig.immersiveEngineeringRailgunReaction(),
                    "IE master switch did not disable Railgun Shot reaction");
            helper.succeed();
        } finally {
            MagConfig.IMMERSIVE_ENGINEERING_COMPAT_ENABLED.set(compat);
            MagConfig.IMMERSIVE_ENGINEERING_RAILGUN_REACTION.set(reaction);
            shot.discard();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void recipesRailgunAndElectricalMachinesHaveRoles(final GameTestHelper helper) {
        helper.assertTrue(block("electromagnet").defaultBlockState().is(MagTags.FERROMAGNETIC_BLOCKS),
                "IE Electromagnet is not ferromagnetic");
        for (final String path : new String[]{"electromagnet", "tesla_coil", "capacitor_lv",
                "capacitor_mv", "capacitor_hv", "capacitor_creative", "transformer",
                "transformer_hv", "generator", "coil_lv", "coil_mv", "coil_hv",
                "connector_lv", "connector_lv_relay", "connector_mv", "connector_mv_relay",
                "connector_hv", "connector_hv_relay", "connector_redstone", "connector_probe",
                "connector_bundled", "connector_structural", "post_transformer"}) {
            helper.assertTrue(block(path).defaultBlockState().is(MagTags.EDDY_CONDUCTORS),
                    "IE electrical block is not an eddy conductor: " + path);
        }
        final var railgunShot = BuiltInRegistries.ENTITY_TYPE.get(
                ResourceLocation.fromNamespaceAndPath("immersiveengineering", "railgun_shot"));
        helper.assertTrue(railgunShot.builtInRegistryHolder().is(MagTags.MAGNETIZABLE_ENTITIES),
                "IE Railgun Shot is missing from the magnetizable entity tag");
        helper.assertTrue(isLightningSource(helper, "tesla"),
                "IE Tesla Coil secondary damage is not a LIRM lightning source");
        helper.assertTrue(isLightningSource(helper, "tesla_primary"),
                "IE Tesla Coil primary damage is not a LIRM lightning source");
        final TagKey<net.minecraft.world.item.Item> minerals = TagKey.create(
                Registries.ITEM, ResourceLocation.fromNamespaceAndPath("magnetization", "ferrofluid_minerals"));
        helper.assertTrue(MagItems.RAW_MAGNETITE.get().getDefaultInstance().is(minerals),
                "Raw Magnetite is missing from the Ferrofluid mineral ingredient tag");
        helper.assertTrue(MagItems.RAW_HEMATITE.get().getDefaultInstance().is(minerals),
                "Raw Hematite is missing from the Ferrofluid mineral ingredient tag");
        for (final String path : new String[]{"immersiveengineering_mixer_ferrofluid",
                "immersiveengineering_metal_press_magnetic_plate",
                "air_filter_from_immersiveengineering_plastic"}) {
            final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("magnetization", path);
            helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(id).isPresent(),
                    "Missing Immersive Engineering compatibility recipe " + id);
        }
        helper.succeed();
    }

    private static IEnergyStorage energy(final GameTestHelper helper, final BlockPos relative) {
        final BlockPos absolute = helper.absolutePos(relative);
        IEnergyStorage storage = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, absolute, null);
        if (storage != null) return storage;
        for (final Direction direction : Direction.values()) {
            storage = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, absolute, direction);
            if (storage != null) return storage;
        }
        return null;
    }

    private static boolean isLightningSource(final GameTestHelper helper, final String path) {
        final ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath("immersiveengineering", path));
        return helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolder(key).map(holder -> holder.is(MagTags.LIGHTNING_SOURCES)).orElse(false);
    }

    private static Block block(final String path) {
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("immersiveengineering", path);
        if (!BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalStateException("Missing IE block " + id);
        return BuiltInRegistries.BLOCK.get(id);
    }
}
