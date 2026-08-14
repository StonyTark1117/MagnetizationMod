package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.golem.GalliumGolem;
import com.stonytark.magnetization.content.golem.MagnetiteGolem;
import com.stonytark.magnetization.registry.MagEntities;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.List;

/** Acceptance tests for every solid custom-golem repair material. */
@GameTestHolder(Magnetization.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CustomGolemRepairGameTests {
    private record RepairCase(String name, EntityType<? extends IronGolem> type, Item material) {}

    private CustomGolemRepairGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "customGolemRepairs")
    public static void everySolidGolemRejectsIronAndUsesItsOwnMaterial(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        // NeoForge's cached fake player avoids an artificial login connection;
        // disconnecting that connection intermittently blocked the full release
        // matrix even though the repair assertions had already completed.
        final ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        final boolean previousGallium = MagConfig.GALLIUM_GOLEM_ENABLED.get();
        final boolean previousMagnetite = MagConfig.MAGNETITE_GOLEM_ENABLED.get();
        final List<RepairCase> cases = List.of(
                new RepairCase("gallium", MagEntities.GALLIUM_GOLEM.get(), MagItems.GALLIUM_INGOT.get()),
                new RepairCase("magnetite", MagEntities.MAGNETITE_GOLEM.get(), MagItems.MAGNETITE_INGOT.get()),
                new RepairCase("pyrrhotite", MagEntities.PYRRHOTITE_GOLEM.get(), MagItems.PYRRHOTITE_INGOT.get()),
                new RepairCase("hematite", MagEntities.HEMATITE_GOLEM.get(), MagItems.HEMATITE_INGOT.get()),
                new RepairCase("titanomagnetite", MagEntities.TITANOMAGNETITE_GOLEM.get(),
                        MagItems.TITANOMAGNETITE_INGOT.get()));
        try {
            for (int index = 0; index < cases.size(); index++) {
                final RepairCase repair = cases.get(index);
                final IronGolem golem = repair.type().create(level);
                helper.assertTrue(golem != null, "Could not create " + repair.name());
                golem.setPos(helper.absolutePos(new BlockPos(2 + index, 2, 2)).getCenter());
                golem.setNoAi(true);
                level.addFreshEntity(golem);
                golem.setHealth(20.0f);

                final ItemStack iron = new ItemStack(Items.IRON_INGOT);
                player.setItemInHand(InteractionHand.MAIN_HAND, iron);
                player.interactOn(golem, InteractionHand.MAIN_HAND);
                helper.assertTrue(golem.getHealth() == 20.0f && iron.getCount() == 1,
                        repair.name() + " inherited vanilla iron-ingot repair");

                final ItemStack material = new ItemStack(repair.material());
                player.setItemInHand(InteractionHand.MAIN_HAND, material);
                player.interactOn(golem, InteractionHand.MAIN_HAND);
                helper.assertTrue(golem.getHealth() == 45.0f,
                        repair.name() + " material did not repair 25 health; health=" + golem.getHealth());
                helper.assertTrue(material.isEmpty(), repair.name() + " repair material was not consumed");
                golem.discard();
            }

            final MagnetiteGolem oxidized = MagEntities.MAGNETITE_GOLEM.get().create(level);
            helper.assertTrue(oxidized != null, "Could not create oxidized Magnetite Golem");
            oxidized.setPos(helper.absolutePos(new BlockPos(8, 2, 2)).getCenter());
            level.addFreshEntity(oxidized);
            final CompoundTag tag = new CompoundTag();
            oxidized.addAdditionalSaveData(tag);
            tag.putBoolean("Oxidized", true);
            oxidized.readAdditionalSaveData(tag);
            oxidized.setHealth(20.0f);

            final ItemStack freshIngot = new ItemStack(MagItems.MAGNETITE_INGOT.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, freshIngot);
            player.interactOn(oxidized, InteractionHand.MAIN_HAND);
            helper.assertTrue(oxidized.getHealth() == 20.0f && freshIngot.getCount() == 1,
                    "Oxidized Magnetite Golem accepted fresh Magnetite");

            final ItemStack oxidizedIngot = new ItemStack(MagItems.MAGHEMITE_INGOT.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, oxidizedIngot);
            player.interactOn(oxidized, InteractionHand.MAIN_HAND);
            helper.assertTrue(oxidized.getHealth() == 45.0f && oxidizedIngot.isEmpty(),
                    "Oxidized Magnetite Golem did not use Maghemite");
            oxidized.discard();

            // A disabled type keeps its save data but loses custom material
            // behavior, so existing bodies must fall back to vanilla iron repair
            // rather than becoming permanently unrepairable.
            MagConfig.GALLIUM_GOLEM_ENABLED.set(false);
            final GalliumGolem disabledGallium = MagEntities.GALLIUM_GOLEM.get().create(level);
            helper.assertTrue(disabledGallium != null, "Could not create disabled Gallium Golem fixture");
            disabledGallium.setPos(helper.absolutePos(new BlockPos(9, 2, 2)).getCenter());
            level.addFreshEntity(disabledGallium);
            disabledGallium.setHealth(20.0f);
            final ItemStack galliumIron = new ItemStack(Items.IRON_INGOT);
            player.setItemInHand(InteractionHand.MAIN_HAND, galliumIron);
            player.interactOn(disabledGallium, InteractionHand.MAIN_HAND);
            helper.assertTrue(disabledGallium.getHealth() == 45.0f && galliumIron.isEmpty(),
                    "Disabled Gallium Golem did not restore vanilla iron repair");
            disabledGallium.discard();

            MagConfig.MAGNETITE_GOLEM_ENABLED.set(false);
            final MagnetiteGolem disabledMagnetite = MagEntities.MAGNETITE_GOLEM.get().create(level);
            helper.assertTrue(disabledMagnetite != null, "Could not create disabled Magnetite Golem fixture");
            disabledMagnetite.setPos(helper.absolutePos(new BlockPos(10, 2, 2)).getCenter());
            level.addFreshEntity(disabledMagnetite);
            disabledMagnetite.setHealth(20.0f);
            final ItemStack magnetiteIron = new ItemStack(Items.IRON_INGOT);
            player.setItemInHand(InteractionHand.MAIN_HAND, magnetiteIron);
            player.interactOn(disabledMagnetite, InteractionHand.MAIN_HAND);
            helper.assertTrue(disabledMagnetite.getHealth() == 45.0f && magnetiteIron.isEmpty(),
                    "Disabled oxide Golem did not restore vanilla iron repair");
            disabledMagnetite.discard();
            helper.succeed();
        } finally {
            MagConfig.GALLIUM_GOLEM_ENABLED.set(previousGallium);
            MagConfig.MAGNETITE_GOLEM_ENABLED.set(previousMagnetite);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }
}
