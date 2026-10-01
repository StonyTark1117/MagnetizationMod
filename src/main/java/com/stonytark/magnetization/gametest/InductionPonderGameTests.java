package com.stonytark.magnetization.gametest;

import com.mojang.authlib.GameProfile;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.induction.InductionPadBlockEntity;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

/** Real IE capabilities verify the tutorial's generic equipment illustration. */
@GameTestHolder("magnetization_immersiveengineering")
@PrefixGameTestTemplate(false)
public final class InductionPonderGameTests {
    private InductionPonderGameTests() {}
    @GameTest(template = "empty", batch = "ponder_induction", timeoutTicks = 60)
    public static void realEquipmentChargesOnlyWhenEnabledPoweredAndInRange(GameTestHelper h) {
        boolean enabled = MagConfig.INDUCTION_PAD_ENABLED.get(); int interval = MagConfig.INDUCTION_PAD_INTERVAL.get();
        double range = MagConfig.INDUCTION_PAD_RANGE.get();
        var level = h.getLevel(); var p = h.absolutePos(new BlockPos(2,80,2));
        var player = FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"PadEquipment"));
        player.setPos(Vec3.atCenterOf(p).add(1,0,0)); player.setNoGravity(true); level.addNewPlayer(player);
        try {
            MagConfig.INDUCTION_PAD_ENABLED.set(true); MagConfig.INDUCTION_PAD_INTERVAL.set(1); MagConfig.INDUCTION_PAD_RANGE.set(4d);
            level.setBlock(p, MagBlocks.INDUCTION_PAD.get().defaultBlockState(),3);
            var pad = (InductionPadBlockEntity) level.getBlockEntity(p);
            var input = level.getCapability(Capabilities.EnergyStorage.BLOCK,p,null);
            h.assertTrue(input != null && input.canReceive(),"Pad exposes no FE input");
            for (int slot = 0; slot < 3; slot++) {
                var id = ResourceLocation.fromNamespaceAndPath("immersiveengineering",slot == 2 ? "powerpack" : "railgun");
                h.assertTrue(BuiltInRegistries.ITEM.containsKey(id),"Pinned IE equipment missing: "+id);
                var stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
                if (slot == 2) {
                    h.assertTrue(stack.getCapability(Capabilities.EnergyStorage.ITEM) == null, "Empty powerpack unexpectedly has a battery");
                    var inventory = stack.getCapability(Capabilities.ItemHandler.ITEM);
                    var capacitor = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("immersiveengineering:capacitor_lv")));
                    h.assertTrue(inventory != null && inventory.insertItem(0, capacitor, false).isEmpty(), "Native powerpack rejected LV capacitor");
                }
                var cap = stack.getCapability(Capabilities.EnergyStorage.ITEM);
                h.assertTrue(cap != null && cap.canReceive() && cap.getEnergyStored()==0,"Equipment must begin empty and accept FE: "+id);
                player.getInventory().clearContent();
                if (slot == 0) player.getInventory().setItem(9,stack);
                else player.setItemSlot(slot == 1 ? EquipmentSlot.OFFHAND : EquipmentSlot.CHEST,stack);
                pad.clearEnergyForEmp(); InductionPadBlockEntity.serverTick(level,p,pad.getBlockState(),pad);
                h.assertTrue(cap.getEnergyStored()==0,"Empty pad charged equipment");
                h.assertTrue(input.receiveEnergy(4000,false)==4000,"Pad rejected default input");
                MagConfig.INDUCTION_PAD_ENABLED.set(false); InductionPadBlockEntity.serverTick(level,p,pad.getBlockState(),pad);
                h.assertTrue(cap.getEnergyStored()==0 && pad.energyBuffer().getEnergyStored()==4000,"Disabled pad transferred energy");
                MagConfig.INDUCTION_PAD_ENABLED.set(true); player.setPos(Vec3.atCenterOf(p).add(6,0,0));
                InductionPadBlockEntity.serverTick(level,p,pad.getBlockState(),pad); h.assertTrue(cap.getEnergyStored()==0,"Out-of-range player charged");
                player.setPos(Vec3.atCenterOf(p).add(1,0,0)); InductionPadBlockEntity.serverTick(level,p,pad.getBlockState(),pad);
                int received=stack.getCapability(Capabilities.EnergyStorage.ITEM).getEnergyStored();
                h.assertTrue(received>0 && received+pad.energyBuffer().getEnergyStored()==4000,"Charging or FE conservation failed in slot "+slot+" received="+received+" remaining="+pad.energyBuffer().getEnergyStored());
            }
            player.getInventory().clearContent(); player.getInventory().setItem(0,new ItemStack(Items.IRON_PICKAXE));
            pad.clearEnergyForEmp(); input.receiveEnergy(4000,false); InductionPadBlockEntity.serverTick(level,p,pad.getBlockState(),pad);
            h.assertTrue(pad.energyBuffer().getEnergyStored()==4000,"Ordinary tool consumed charging energy");
            var saved=pad.saveWithoutMetadata(level.registryAccess()); pad.clearEnergyForEmp();pad.loadWithComponents(saved,level.registryAccess());
            h.assertTrue(pad.energyBuffer().getEnergyStored()==4000,"Pad buffer lost on serialization");h.succeed();
        } finally {
            player.discard();level.setBlock(p,Blocks.AIR.defaultBlockState(),3);
            MagConfig.INDUCTION_PAD_ENABLED.set(enabled);MagConfig.INDUCTION_PAD_INTERVAL.set(interval);MagConfig.INDUCTION_PAD_RANGE.set(range);
        }
    }
}
