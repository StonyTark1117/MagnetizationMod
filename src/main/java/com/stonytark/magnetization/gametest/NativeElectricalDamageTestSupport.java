package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.registry.MagDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import static com.stonytark.magnetization.gametest.NativeCompatTestSupport.*;

final class NativeElectricalDamageTestSupport {
    private NativeElectricalDamageTestSupport() {}

    static Mob victim(GameTestHelper h, BlockPos relative) {
        Mob target = EntityType.IRON_GOLEM.create(h.getLevel());
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setPos(Vec3.atBottomCenterOf(h.absolutePos(relative)));
        target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        target.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        h.getLevel().addFreshEntity(target);
        return target;
    }

    static void assertHit(GameTestHelper h, Mob target, String id) {
        var damage = target.getLastDamageSource();
        h.assertTrue(target.getHealth() < target.getMaxHealth(), id + " native producer did not hurt target");
        h.assertTrue(damage != null && damage.typeHolder().unwrapKey().orElseThrow().location()
                .equals(ResourceLocation.parse(id)), "Wrong native damage: " + damage);
        h.assertTrue(damage.is(MagTags.LIGHTNING_SOURCES), id + " is not tagged for LIRM");
        long stamps = java.util.stream.Stream.of(EquipmentSlot.CHEST, EquipmentSlot.MAINHAND)
                .filter(slot -> target.getItemBySlot(slot).has(MagDataComponents.ARMOR_POLARITY.get())).count();
        h.assertTrue(stamps == 1, id + " should stamp exactly one equipment piece, got " + stamps);
    }

    static void tesla(GameTestHelper h, String namespace) {
        BlockPos pos = new BlockPos(2, 40, 2);
        var coil = place(h, pos, namespace + ":tesla_coil");
        Mob target = victim(h, pos.east(2));
        h.setBlock(pos.west(), Blocks.REDSTONE_BLOCK);
        IEnergyStorage energy = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                h.absolutePos(pos), null);
        h.assertTrue(energy != null, "Native Tesla coil has no FE input");
        // FE input is rate limited; fill through repeated real capability transfers.
        while (energy.getEnergyStored() < energy.getMaxEnergyStored()) {
            h.assertTrue(energy.receiveEnergy(energy.getMaxEnergyStored(), false) > 0,
                    "Native Tesla coil stopped accepting FE");
        }
        int charged = energy.getEnergyStored();
        h.succeedWhen(() -> {
            assertHit(h, target, namespace + (namespace.equals("immersiveengineering") ? ":tesla" : ":tesla_coil"));
            h.assertTrue(energy.getEnergyStored() < charged, "Native Tesla attack consumed no FE");
            h.setBlock(pos, Blocks.AIR);
            target.discard();
        });
    }

    static void razor(GameTestHelper h, boolean powered) {
        BlockPos pos = new BlockPos(2, 40, 2);
        h.setBlock(pos.below(), Blocks.STONE);
        var wire = place(h, pos, "immersiveengineering:razor_wire");
        Mob target = victim(h, pos);
        if (powered) call(wire, "insertEnergy", new Class<?>[]{int.class}, 64);
        else call(wire, "onEntityCollision", new Class<?>[]{net.minecraft.world.level.Level.class,
                net.minecraft.world.entity.Entity.class}, h.getLevel(), target);
        try { assertHit(h, target, "immersiveengineering:" + (powered ? "razor_shock" : "razor_wire")); }
        finally { target.discard(); h.setBlock(pos, Blocks.AIR); }
        h.succeed();
    }
}
