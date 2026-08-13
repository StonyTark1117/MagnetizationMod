package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagneticFieldSource;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.compat.RecipeViewerInfo;
import com.stonytark.magnetization.content.MagneticMaterials;
import com.stonytark.magnetization.content.effect.RareEarthEquipmentEffects;
import com.stonytark.magnetization.content.item.MagneticToolPullHandler;
import com.stonytark.magnetization.content.jet.MhdJetBlockEntity;
import com.stonytark.magnetization.content.motor.HomopolarMotorBlockEntity;
import com.stonytark.magnetization.content.permanent.PermanentMagnetBlockEntity;
import com.stonytark.magnetization.registry.MagBlockEntities;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.stream.Collectors;

/** Runtime contracts for the two engineered rare-earth progression tiers. */
@GameTestHolder(Magnetization.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RareEarthGameTests {
    private RareEarthGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void rareEarthMagnetBlocksEmitTheirMaterialTiers(final GameTestHelper helper) {
        assertMagnet(helper, new BlockPos(0, 1, 0), MagBlocks.PERMANENT_MAGNET.get(), MagneticStrength.WEAK);
        // Keep each source at least two blocks away from the others so this
        // assertion measures its native tier, not the intentional Halbach boost.
        assertMagnet(helper, new BlockPos(2, 1, 0), MagBlocks.SAMARIUM_COBALT_MAGNET.get(), MagneticStrength.MEDIUM);
        assertMagnet(helper, new BlockPos(0, 1, 2), MagBlocks.NEODYMIUM_MAGNET.get(), MagneticStrength.STRONG);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void potencyAndEquipmentBonusesKeepTheirProgressionOrder(final GameTestHelper helper) {
        final int titan = MagneticMaterials.potency(new ItemStack(MagItems.TITANOMAGNETITE_INGOT.get()));
        final int smcoAlloy = MagneticMaterials.potency(new ItemStack(MagItems.SAMARIUM_COBALT_ALLOY.get()));
        final int smcoMagnet = MagneticMaterials.potency(new ItemStack(MagItems.SAMARIUM_COBALT_MAGNET.get()));
        final int neodymiumAlloy = MagneticMaterials.potency(new ItemStack(MagItems.NEODYMIUM_ALLOY.get()));
        final int neodymiumMagnet = MagneticMaterials.potency(new ItemStack(MagItems.NEODYMIUM_MAGNET.get()));
        helper.assertTrue(titan < smcoAlloy && smcoAlloy < smcoMagnet
                        && smcoMagnet < neodymiumAlloy && neodymiumAlloy < neodymiumMagnet,
                "Rare-earth machine potency ladder is out of order");

        helper.assertTrue(MagneticToolPullHandler.magneticHarvestWeight(
                        new ItemStack(MagItems.NEODYMIUM_PICKAXE.get())) == 2.0d,
                "Neodymium tools must contribute double magnetic harvesting pull");
        helper.assertTrue(MagneticToolPullHandler.magneticHarvestWeight(
                        new ItemStack(MagItems.SAMARIUM_COBALT_PICKAXE.get())) == 1.0d,
                "Samarium-cobalt tools must retain ordinary magnetic harvesting pull");
        helper.assertTrue(Math.abs(RareEarthEquipmentEffects.fireDamageMultiplier(1) - 0.8f) < 0.0001f
                        && Math.abs(RareEarthEquipmentEffects.fireDamageMultiplier(4) - 0.2f) < 0.0001f,
                "Samarium-cobalt armor must retain the 20%-per-piece fire ladder");

        final ArmorStand wearer = EntityType.ARMOR_STAND.create(helper.getLevel());
        helper.assertTrue(wearer != null, "Could not create armor stand for rare-earth armor test");
        if (wearer == null) return;
        wearer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(MagItems.NEODYMIUM_HELMET.get()));
        wearer.setItemSlot(EquipmentSlot.CHEST, new ItemStack(MagItems.NEODYMIUM_CHESTPLATE.get()));
        wearer.setItemSlot(EquipmentSlot.LEGS, new ItemStack(MagItems.NEODYMIUM_LEGGINGS.get()));
        wearer.setItemSlot(EquipmentSlot.FEET, new ItemStack(MagItems.NEODYMIUM_BOOTS.get()));
        helper.assertTrue(RareEarthEquipmentEffects.hasFullNeodymiumSet(wearer),
                "Complete neodymium armor must activate Magnetic Anchoring");
        helper.assertTrue(RareEarthEquipmentEffects.fieldSusceptibilityMultiplier(wearer) == 0.25d,
                "Magnetic Anchoring must leave exactly 25% field susceptibility");
        wearer.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        helper.assertTrue(RareEarthEquipmentEffects.fieldSusceptibilityMultiplier(wearer) == 1.0d,
                "A partial neodymium set must not activate Magnetic Anchoring");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void samariumCobaltWorksInBothMagnetSlotMachines(final GameTestHelper helper) {
        final BlockPos motorPos = new BlockPos(0, 1, 0);
        helper.setBlock(motorPos, MagBlocks.HOMOPOLAR_MOTOR.get());
        final var motorEntity = helper.getBlockEntity(motorPos);
        helper.assertTrue(motorEntity instanceof HomopolarMotorBlockEntity,
                "Homopolar Motor block entity was not created");
        if (!(motorEntity instanceof HomopolarMotorBlockEntity motor)) return;

        final ItemStack motorMagnet = new ItemStack(MagItems.SAMARIUM_COBALT_MAGNET.get());
        helper.assertTrue(motor.magnetContainer().canPlaceItem(0, motorMagnet),
                "Samarium-cobalt magnet must be accepted by the Homopolar Motor");
        motor.magnetContainer().setItem(0, motorMagnet);
        helper.assertTrue(motor.getMagnet().is(MagItems.SAMARIUM_COBALT_MAGNET.get())
                        && HomopolarMotorBlockEntity.speedFor(motor.getMagnet()) > 0,
                "Samarium-cobalt magnet must drive the Homopolar Motor");

        final BlockPos jetPos = new BlockPos(2, 1, 0);
        helper.setBlock(jetPos, MagBlocks.MHD_JET.get());
        final var jetEntity = helper.getBlockEntity(jetPos);
        helper.assertTrue(jetEntity instanceof MhdJetBlockEntity,
                "MHD Jet block entity was not created");
        if (!(jetEntity instanceof MhdJetBlockEntity jet)) return;

        final ItemStack jetMagnet = new ItemStack(MagItems.SAMARIUM_COBALT_MAGNET.get());
        helper.assertTrue(jet.magnetContainer().canPlaceItem(0, jetMagnet),
                "Samarium-cobalt magnet must be accepted by the MHD Jet");
        jet.magnetContainer().setItem(0, jetMagnet);
        helper.assertTrue(jet.getMagnet().is(MagItems.SAMARIUM_COBALT_MAGNET.get())
                        && MhdJetBlockEntity.isMagnet(jet.getMagnet()),
                "Samarium-cobalt magnet must activate the MHD Jet magnet tier");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void recipeViewersExposeTheCompleteRareEarthProgression(final GameTestHelper helper) {
        final var topic = RecipeViewerInfo.topics().stream()
                .filter(candidate -> candidate.id().getPath().equals("info/rare_earth_progression"))
                .findFirst().orElseThrow(() -> new AssertionError("Missing rare-earth recipe-viewer topic"));
        final Set<String> actual = topic.resolveStacks().stream()
                .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                .collect(Collectors.toUnmodifiableSet());
        final Set<String> required = Set.of(
                "magnetization:bastnasite_ore", "magnetization:deepslate_bastnasite_ore",
                "magnetization:monazite_ore", "magnetization:deepslate_monazite_ore",
                "magnetization:cobaltite_ore", "magnetization:deepslate_cobaltite_ore",
                "magnetization:borax_ore", "magnetization:deepslate_borax_ore",
                "magnetization:bastnasite_concentrate", "magnetization:monazite_concentrate",
                "magnetization:cobaltite_concentrate", "magnetization:boron_dust",
                "magnetization:samarium_cobalt_alloy", "magnetization:neodymium_alloy",
                "magnetization:samarium_cobalt_magnet_blank", "magnetization:neodymium_magnet_blank",
                "magnetization:sintered_samarium_cobalt", "magnetization:sintered_neodymium",
                "magnetization:samarium_cobalt_magnet", "magnetization:neodymium_magnet");
        helper.assertTrue(actual.containsAll(required),
                "Rare-earth recipe-viewer topic is missing progression stacks: "
                        + required.stream().filter(id -> !actual.contains(id)).sorted().toList());
        helper.succeed();
    }

    private static void assertMagnet(final GameTestHelper helper, final BlockPos relative,
                                     final net.minecraft.world.level.block.Block block,
                                     final MagneticStrength expected) {
        helper.setBlock(relative, block);
        final BlockPos absolute = helper.absolutePos(relative);
        final var state = helper.getLevel().getBlockState(absolute);
        helper.assertTrue(MagBlockEntities.PERMANENT_MAGNET.get().isValid(state),
                "Permanent-magnet block entity rejects " + block);
        final var blockEntity = helper.getLevel().getBlockEntity(absolute);
        helper.assertTrue(blockEntity instanceof PermanentMagnetBlockEntity,
                "Missing permanent-magnet block entity for " + block);
        if (!(blockEntity instanceof PermanentMagnetBlockEntity magnet)) return;
        PermanentMagnetBlockEntity.serverTick(helper.getLevel(), absolute, state, magnet);
        final var field = ((MagneticFieldSource) magnet).currentField();
        helper.assertTrue(field != null && field.strength() == expected,
                block + " should emit " + expected + ", got " + (field == null ? "no field" : field.strength()));
    }
}
