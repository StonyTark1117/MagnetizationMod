package com.stonytark.magnetization.gametest;

import com.mojang.authlib.GameProfile;
import com.stonytark.magnetization.api.*;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.item.MagneticToolPullHandler;
import com.stonytark.magnetization.menu.EmitterMenu;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.registry.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("magnetization_slugterra")
@PrefixGameTestTemplate(false)
public final class SlugterraEquipmentGameTests {
    private static final List<String> BLASTERS = List.of("slugterra:overpass_shooter_avg_1", "slugterra_dark:doctor_black_blaster");

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void fieldsAndPickupPreserveEveryCapsule(final GameTestHelper h) {
        final var level = h.getLevel();
        final var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "SlugCapsules"));
        player.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 30, 1))));
        final List<Item> items = new ArrayList<>();
        BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, ResourceLocation.parse("slugterra:capsule")))
                .orElseThrow().forEach(item -> items.add(item.value()));
        h.assertTrue(items.size() == 69, "Expected all 46 base and 23 dark capsules");
        for (String id : List.of("slugterra:empty_capsule", "slugterra:slug_energy_core",
                BLASTERS.get(0), BLASTERS.get(1))) items.add(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
        try {
            for (Item item : items) {
                player.getInventory().clearContent();
                final var stack = specimen(item);
                final var before = stack.copy();
                final var dropped = new ItemEntity(level, player.getX() + 2, player.getY() + 0.8, player.getZ(), stack);
                dropped.setNoGravity(true);
                level.addFreshEntity(dropped);
                try {
                    h.assertTrue(FieldApplicator.isMagnetizableTarget(dropped), "Equipment not eligible: " + item);
                    for (var pole : List.of(MagneticPolarity.SOUTH, MagneticPolarity.NORTH)) {
                        dropped.setDeltaMovement(Vec3.ZERO);
                        FieldApplicator.applyEntitiesOnly(level, new MagneticField(dropped.position().add(-2, 0, 0),
                                new Vec3(1, 0, 0), pole, MagneticStrength.WEAK, MagneticField.Shape.OMNIDIRECTIONAL));
                        h.assertTrue(pole == MagneticPolarity.SOUTH ? dropped.getDeltaMovement().x < 0 : dropped.getDeltaMovement().x > 0,
                                "Missing field response for " + item + "/" + pole);
                    }
                    final var decoded = ItemStack.parseOptional(level.registryAccess(),
                            (CompoundTag) dropped.getItem().save(level.registryAccess()));
                    h.assertTrue(ItemStack.matches(before, decoded), "Save/reload changed stored slug data: " + item);
                    dropped.setItem(decoded);
                    dropped.setNoPickUpDelay();
                    dropped.playerTouch(player);
                    h.assertTrue(dropped.isRemoved(), "Pickup failed: " + item);
                    h.assertTrue(ItemStack.matches(before, player.getInventory().getItem(0)), "Pickup changed capsule data: " + item);
                } finally { dropped.discard(); }
            }
            for (var setting : List.of(MagConfig.SLUGTERRA_EQUIPMENT_ENABLED, MagConfig.SLUGTERRA_COMPAT_ENABLED)) {
                boolean old = setting.get();
                try {
                    setting.set(false);
                    for (Item item : items) {
                        var drop = new ItemEntity(level, 0, 0, 0, new ItemStack(item));
                        h.assertTrue(!FieldApplicator.isMagnetizableTarget(drop), "Disabled equipment remains magnetic: " + item);
                    }
                } finally { setting.set(old); }
            }
            final var slug = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("slugterra:tazerling")).create(level);
            h.assertTrue(slug != null && !FieldApplicator.isMagnetizableTarget(slug), "Capsule tags made living slugs magnetic");
            slug.discard();
            h.succeed();
        } finally { player.getInventory().clearContent(); }
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void blastersUseElectromagnetMenuAndHeldItemMagnet(final GameTestHelper h) {
        final var level = h.getLevel();
        final var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "SlugBlaster"));
        final var pos = h.absolutePos(new BlockPos(1, 30, 1));
        player.setPos(Vec3.atCenterOf(pos));
        level.setBlockAndUpdate(pos, MagBlocks.ELECTROMAGNET.get().defaultBlockState());
        final var menu = new EmitterMenu(1, player.getInventory(), ContainerLevelAccess.create(level, pos), pos,
                EmitterMenu.CAP_ARMOR | EmitterMenu.CAP_POLARITY);
        final int oldInterval = MagConfig.TOOL_PULL_TICKS.get();
        try {
            MagConfig.TOOL_PULL_TICKS.set(1);
            for (String id : BLASTERS) {
                var blaster = specimen(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
                var beforeData = blaster.get(DataComponents.CUSTOM_DATA);
                h.assertTrue(menu.getSlot(0).mayPlace(blaster), "Menu rejects " + id);
                menu.getSlot(0).set(blaster);
                h.assertTrue(menu.clickMenuButton(player, EmitterMenu.BUTTON_POLARITY_SOUTH), "Menu click rejected");
                blaster = menu.getSlot(0).getItem();
                h.assertTrue(blaster.get(MagDataComponents.ARMOR_POLARITY.get()) == MagneticPolarity.SOUTH, "Blaster not stamped");
                h.assertTrue(beforeData.equals(blaster.get(DataComponents.CUSTOM_DATA)), "Stamping lost loaded slug data");
                menu.getSlot(0).set(ItemStack.EMPTY);
                for (var hand : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
                    player.setItemSlot(hand, blaster);
                    var metal = new ItemEntity(level, player.getX() + 2, player.getY() + 0.8, player.getZ(), new ItemStack(Items.IRON_INGOT));
                    var stone = new ItemEntity(level, player.getX() + 2, player.getY() + 0.8, player.getZ(), new ItemStack(Items.COBBLESTONE));
                    metal.setDeltaMovement(Vec3.ZERO);
                    stone.setDeltaMovement(Vec3.ZERO);
                    level.addFreshEntity(metal);
                    level.addFreshEntity(stone);
                    try {
                        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
                        h.assertTrue(metal.getDeltaMovement().x < 0, "Held blaster did not attract metal: " + hand);
                        h.assertTrue(stone.getDeltaMovement().equals(Vec3.ZERO), "Blaster attracted nonmetal");
                        metal.setDeltaMovement(Vec3.ZERO);
                        boolean old = MagConfig.SLUGTERRA_EQUIPMENT_ENABLED.get();
                        try {
                            MagConfig.SLUGTERRA_EQUIPMENT_ENABLED.set(false);
                            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
                            h.assertTrue(metal.getDeltaMovement().equals(Vec3.ZERO), "Disabled blaster still attracts");
                        } finally { MagConfig.SLUGTERRA_EQUIPMENT_ENABLED.set(old); }
                    } finally { metal.discard(); stone.discard(); player.setItemSlot(hand, ItemStack.EMPTY); }
                }
            }
            h.succeed();
        } finally {
            MagConfig.TOOL_PULL_TICKS.set(oldInterval);
            player.getInventory().clearContent();
            level.removeBlock(pos, false);
        }
    }

    private static ItemStack specimen(Item item) {
        final var stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Named slug with saved training"));
        final var slug = new CompoundTag();
        slug.putUUID("Owner", UUID.fromString("f719f491-ea33-42fc-87e5-c61cdd43f251"));
        slug.putInt("CombatLevel", 15);
        slug.putFloat("pendingGenericExp", 12.5f);
        slug.putString("Variant", "rare");
        final var data = new CompoundTag();
        data.put("entityData", slug);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }
}
