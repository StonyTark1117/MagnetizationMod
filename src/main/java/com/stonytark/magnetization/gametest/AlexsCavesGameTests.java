package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.compat.ExternalFieldCompat;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.MagneticMaterials;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Published-runtime checks for Alex's Caves magnetic-caves compatibility. */
@GameTestHolder("magnetization_alexscaves")
@PrefixGameTestTemplate(false)
public final class AlexsCavesGameTests {
    private AlexsCavesGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 80, batch = "alexNativeLightning")
    public static void nativeTeslaDischargeStampsOnceAndHonorsSwitches(final GameTestHelper helper) throws Exception {
        final boolean compat = MagConfig.ALEXSCAVES_COMPAT_ENABLED.get();
        final boolean lirm = MagConfig.LIRM_ENABLED.get();
        final double petrify = MagConfig.LIRM_LOG_PETRIFY_CHANCE.get();
        try {
            MagConfig.LIRM_LOG_PETRIFY_CHANCE.set(1.0);
            for (int mode = 0; mode < 6; mode++) {
                MagConfig.ALEXSCAVES_COMPAT_ENABLED.set(mode % 3 != 1);
                MagConfig.LIRM_ENABLED.set(mode % 3 != 2);
                final var pos = helper.absolutePos(new BlockPos(1, 3, 1));
                final var state = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("alexscaves:tesla_bulb")).defaultBlockState();
                helper.getLevel().setBlockAndUpdate(pos, state);
                final var bulb = helper.getLevel().getBlockEntity(pos);
                helper.assertTrue(bulb != null, "Native Tesla bulb block entity missing");
                final var victim = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
                victim.setNoAi(true); victim.setNoGravity(true);
                victim.setPos(net.minecraft.world.phys.Vec3.atCenterOf(pos.east(2)));
                final var chest = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE);
                final var helm = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET);
                victim.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, chest);
                victim.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, helm);
                helper.getLevel().addFreshEntity(victim);
                final var logPos = victim.blockPosition().below();
                helper.getLevel().setBlockAndUpdate(logPos, Blocks.OAK_LOG.defaultBlockState());
                try {
                    final float health = victim.getHealth();
                    if (mode < 3) bulb.getClass().getMethod("explode").invoke(bulb);
                    else {
                        final var countdown = bulb.getClass().getDeclaredField("strikeTime");
                        final var destination = bulb.getClass().getDeclaredField("lightningPos");
                        countdown.setAccessible(true); destination.setAccessible(true);
                        countdown.setInt(bulb, -3);
                        destination.set(bulb, victim.position());
                    }
                    final var tick = bulb.getClass().getMethod("tick", net.minecraft.world.level.Level.class,
                            BlockPos.class, net.minecraft.world.level.block.state.BlockState.class, bulb.getClass());
                    tick.invoke(null, helper.getLevel(), pos, state, bulb);
                    tick.invoke(null, helper.getLevel(), pos, state, bulb);
                    helper.assertTrue(victim.getHealth() < health, "Native Tesla discharge did not hit the fixture");
                    final int stamps = (chest.has(com.stonytark.magnetization.registry.MagDataComponents.ARMOR_POLARITY.get()) ? 1 : 0)
                            + (helm.has(com.stonytark.magnetization.registry.MagDataComponents.ARMOR_POLARITY.get()) ? 1 : 0);
                    helper.assertTrue(stamps == (mode % 3 == 0 ? 1 : 0), "Native Tesla discharge LIRM stamp count " + stamps + " mode " + mode);
                    helper.assertTrue(helper.getLevel().getBlockState(logPos).is(mode % 3 == 0 ? MagBlocks.PETRIFIED_WOOD.get() : Blocks.OAK_LOG),
                            "Native Tesla discharge log conversion ignored switches, mode " + mode);
                } finally {
                    victim.discard(); helper.getLevel().removeBlock(pos, false); helper.getLevel().removeBlock(logPos, false);
                }
            }
        } finally {
            MagConfig.ALEXSCAVES_COMPAT_ENABLED.set(compat); MagConfig.LIRM_ENABLED.set(lirm);
            MagConfig.LIRM_LOG_PETRIFY_CHANCE.set(petrify);
        }
        helper.succeed();
    }


    /** Invoke the actual private melee producer; never classify generic mob_attack as lightning. */
    @GameTest(template = "empty", timeoutTicks = 80, batch = "alexNativeMelee")
    public static void nativeMagnetronPhysicalDefaultAndExperimentalLirm(final GameTestHelper helper) throws Exception {
        final boolean compat = MagConfig.ALEXSCAVES_COMPAT_ENABLED.get();
        final boolean lirm = MagConfig.LIRM_ENABLED.get();
        final boolean experimental = MagConfig.ALEXSCAVES_MAGNETRON_LIRM_ENABLED.get();
        final double petrify = MagConfig.LIRM_LOG_PETRIFY_CHANCE.get();
        helper.assertTrue(!MagConfig.ALEXSCAVES_MAGNETRON_LIRM_ENABLED.getDefault(), "Experimental policy must default off");
        final var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("alexscaves:magnetron"));
        final var source = (net.minecraft.world.entity.Mob) type.create(helper.getLevel());
        final var base = helper.absolutePos(new BlockPos(2, 3, 2));
        helper.getLevel().setBlockAndUpdate(base.below(), Blocks.STONE.defaultBlockState());
        source.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(base));
        source.setNoAi(true); source.setNoGravity(true);
        helper.getLevel().addFreshEntity(source);
        try {
            MagConfig.LIRM_LOG_PETRIFY_CHANCE.set(1.0);
            final var goalClass = Class.forName(source.getClass().getName() + "$MeleeGoal");
            final var poseClass = Class.forName(source.getClass().getName() + "$AttackPose");
            final var ctor = goalClass.getDeclaredConstructor(source.getClass());
            ctor.setAccessible(true);
            final var goal = ctor.newInstance(source);
            final var attack = goalClass.getDeclaredMethod("dealDamage", net.minecraft.world.entity.LivingEntity.class, poseClass);
            attack.setAccessible(true);
            for (int mode = 0; mode < 6; mode++) {
                MagConfig.ALEXSCAVES_MAGNETRON_LIRM_ENABLED.set(mode != 0 && mode != 4);
                MagConfig.ALEXSCAVES_COMPAT_ENABLED.set(mode != 2);
                MagConfig.LIRM_ENABLED.set(mode != 3);
                for (final Object pose : poseClass.getEnumConstants()) {
                    if (!java.util.Set.of("LEFT_PUNCH", "RIGHT_PUNCH", "SLAM").contains(pose.toString())) continue;
                    final var victim = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
                    victim.setNoAi(true); victim.setNoGravity(true); victim.setInvulnerable(mode == 5);
                    victim.setPos(source.position().add(0, 0, 2));
                    final var armor = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE);
                    final var helm = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET);
                    victim.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, armor);
                    victim.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, helm);
                    helper.getLevel().addFreshEntity(victim);
                    final var logPos = victim.blockPosition().below();
                    helper.getLevel().setBlockAndUpdate(logPos, Blocks.OAK_LOG.defaultBlockState());
                    try {
                        final float before = victim.getHealth();
                        attack.invoke(goal, victim, pose);
                        helper.assertTrue((victim.getHealth() < before) == (mode != 5), "Native Magnetron hit/control failed " + pose + " mode " + mode);
                        final var component = com.stonytark.magnetization.registry.MagDataComponents.ARMOR_POLARITY.get();
                        final int stamps = (armor.has(component) ? 1 : 0) + (helm.has(component) ? 1 : 0);
                        helper.assertTrue(stamps == (mode == 1 ? 1 : 0), "Magnetron LIRM count " + stamps + " for " + pose + " mode " + mode);
                        helper.assertTrue(helper.getLevel().getBlockState(logPos).is(mode == 1 ? MagBlocks.PETRIFIED_WOOD.get() : Blocks.OAK_LOG),
                                "Magnetron log policy failed " + pose + " mode " + mode);
                    } finally { victim.discard(); helper.getLevel().removeBlock(logPos, false); }
                }
            }
            // Opting Magnetron in must not opt all mob_attack damage into LIRM.
            MagConfig.ALEXSCAVES_MAGNETRON_LIRM_ENABLED.set(true);
            MagConfig.ALEXSCAVES_COMPAT_ENABLED.set(true); MagConfig.LIRM_ENABLED.set(true);
            final var other = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
            final var victim = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
            final var armor = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE);
            victim.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, armor);
            victim.hurt(helper.getLevel().damageSources().mobAttack(other), 3);
            helper.assertTrue(!armor.has(com.stonytark.magnetization.registry.MagDataComponents.ARMOR_POLARITY.get()), "Other mob gained experimental LIRM");
        } finally {
            source.discard(); helper.getLevel().removeBlock(base.below(), false);
            MagConfig.ALEXSCAVES_COMPAT_ENABLED.set(compat); MagConfig.LIRM_ENABLED.set(lirm);
            MagConfig.ALEXSCAVES_MAGNETRON_LIRM_ENABLED.set(experimental); MagConfig.LIRM_LOG_PETRIFY_CHANCE.set(petrify);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void activeAzureAndScarletMagnetsExposeOppositeShipFields(final GameTestHelper helper) {
        final boolean compat = MagConfig.ALEXSCAVES_COMPAT_ENABLED.get();
        final boolean fields = MagConfig.ALEXSCAVES_FIELDS_ENABLED.get();
        final BlockPos azure = new BlockPos(2, 2, 2);
        final BlockPos scarlet = new BlockPos(6, 2, 2);
        MagConfig.ALEXSCAVES_COMPAT_ENABLED.set(true);
        MagConfig.ALEXSCAVES_FIELDS_ENABLED.set(true);
        helper.setBlock(azure, block("azure_magnet"));
        helper.setBlock(scarlet, block("scarlet_magnet"));
        helper.setBlock(azure.below(), Blocks.REDSTONE_BLOCK);
        helper.setBlock(scarlet.below(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(8, () -> {
            try {
                final var azureField = ExternalFieldCompat.currentField(
                        helper.getLevel(), helper.absolutePos(azure));
                final var scarletField = ExternalFieldCompat.currentField(
                        helper.getLevel(), helper.absolutePos(scarlet));
                helper.assertTrue(azureField != null && azureField.polarity() == MagneticPolarity.NORTH,
                        "Active Azure Magnet did not expose a NORTH field");
                helper.assertTrue(scarletField != null && scarletField.polarity() == MagneticPolarity.SOUTH,
                        "Active Scarlet Magnet did not expose a SOUTH field");
                helper.assertTrue(ExternalFieldCompat.shipsOnly(
                                helper.getLevel().getBlockState(helper.absolutePos(azure))),
                        "Alex's Caves field is not marked ship-only for duplicate-force suppression");
                MagConfig.ALEXSCAVES_COMPAT_ENABLED.set(false);
                helper.assertTrue(ExternalFieldCompat.currentField(
                                helper.getLevel(), helper.absolutePos(azure)) == null,
                        "Alex's Caves master switch did not disable projected fields");
            } finally {
                MagConfig.ALEXSCAVES_COMPAT_ENABLED.set(compat);
                MagConfig.ALEXSCAVES_FIELDS_ENABLED.set(fields);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void neodymiumMachineMagnetsAndSharedRecipesLoad(final GameTestHelper helper) {
        int ingotPotency = -1;
        int magnetPotency = -1;
        for (final String color : new String[]{"azure", "scarlet"}) {
            final var ingot = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(
                    "alexscaves", color + "_neodymium_ingot")).getDefaultInstance();
            final var magnet = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(
                    "alexscaves", color + "_magnet")).getDefaultInstance();
            helper.assertTrue(ingot.is(MagTags.MACHINE_MAGNETS), color + " Neodymium is not a machine magnet");
            helper.assertTrue(magnet.is(MagTags.MACHINE_MAGNETS), color + " Magnet is not a machine magnet");
            final int thisIngot = MagneticMaterials.potency(ingot);
            final int thisMagnet = MagneticMaterials.potency(magnet);
            helper.assertTrue(thisMagnet > thisIngot,
                    color + " Magnet is not stronger than its Neodymium ingredient");
            if (ingotPotency >= 0) helper.assertTrue(thisIngot == ingotPotency,
                    "Azure and Scarlet Neodymium have inconsistent potency");
            if (magnetPotency >= 0) helper.assertTrue(thisMagnet == magnetPotency,
                    "Azure and Scarlet Magnets have inconsistent potency");
            ingotPotency = thisIngot;
            magnetPotency = thisMagnet;
        }
        for (final String path : new String[]{"alexscaves_permanent_magnet_from_neodymium",
                "alexscaves_azure_magnet_from_permanent_magnet",
                "alexscaves_scarlet_magnet_from_permanent_magnet",
                "alexscaves_levitation_rail_from_permanent_magnets",
                "alexscaves_ferrofluid_from_ferrouslime"}) {
            final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("magnetization", path);
            helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(id).isPresent(),
                    "Missing Alex's Caves compatibility recipe " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void railsQuarryAndMovableMagneticBlocksShareNativeRoles(final GameTestHelper helper) {
        for (final String path : new String[]{"magnetic_levitation_rail", "quarry",
                "magnetic_activator", "magnetic_light"}) {
            final var state = block(path).defaultBlockState();
            helper.assertTrue(state.is(MagTags.FERROMAGNETIC_BLOCKS),
                    "Alex's Caves magnetic machine is not ferromagnetic: " + path);
            helper.assertTrue(state.is(MagTags.EDDY_CONDUCTORS),
                    "Alex's Caves magnetic machine is not an eddy conductor: " + path);
        }
        for (final String path : new String[]{"block_of_azure_neodymium",
                "block_of_scarlet_neodymium", "azure_neodymium_node",
                "scarlet_neodymium_node", "azure_neodymium_pillar",
                "scarlet_neodymium_pillar", "galena_iron_ore"}) {
            helper.assertTrue(block(path).defaultBlockState().is(MagTags.FERROMAGNETIC_BLOCKS),
                    "Alex's Caves Neodymium/Galena block has no magnetic role: " + path);
        }
        final TagKey<Block> alexMovable = TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath("alexscaves", "ferromagnetic_blocks"));
        helper.assertTrue(MagBlocks.MAGNETITE_BLOCK.get().defaultBlockState().is(alexMovable),
                "Magnetization blocks were not added to Alex's Caves native movable-block tag");
        helper.assertTrue(MagBlocks.PERMANENT_MAGNET.get().defaultBlockState().is(alexMovable),
                "Permanent Magnet cannot be moved by Alex's Caves magnet machinery");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "alexPotionModes")
    public static void potionModesReplaceRatherThanDuplicate(final GameTestHelper helper) {
        final var original = MagConfig.ALEXSCAVES_POTION_MODE.get();
        final var entity = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
        final var theirs = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse("alexscaves:magnetizing")).orElseThrow();
        final var ours = com.stonytark.magnetization.registry.MagEffects.MAGNETIZED;
        try {
            for (var mode : MagConfig.AlexsCavesPotionMode.values()) {
                entity.removeAllEffects();
                MagConfig.ALEXSCAVES_POTION_MODE.set(mode);
                entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(theirs, 200, 1));
                entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(ours, 200, 1));
                helper.assertTrue(entity.hasEffect(ours) == (mode != MagConfig.AlexsCavesPotionMode.THEIRS_ONLY),
                        "Wrong own effect state for " + mode);
                helper.assertTrue(entity.hasEffect(theirs) == (mode != MagConfig.AlexsCavesPotionMode.OURS_ONLY),
                        "Wrong upstream effect state for " + mode);
            }
            helper.succeed();
        } finally { entity.discard(); MagConfig.ALEXSCAVES_POTION_MODE.set(original); }
    }

    private static Block block(final String path) {
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("alexscaves", path);
        if (!BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalStateException("Missing Alex's Caves block " + id);
        return BuiltInRegistries.BLOCK.get(id);
    }
}
