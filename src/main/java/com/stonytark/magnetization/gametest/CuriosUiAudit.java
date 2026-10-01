package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.registry.*;
import com.stonytark.magnetization.content.item.GrappleTickHandler;
import com.stonytark.magnetization.content.permanent.PermanentMagnetBlock;
import com.stonytark.magnetization.api.MagneticPolarity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server assertions driven solely by the connected client's real equipped tools. */
@EventBusSubscriber(modid="magnetization")
public final class CuriosUiAudit {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("magnetization/ui-audit");
    private static ItemEntity target;
    private static boolean setup, gunSeen, pullSeen, passed, failed;
    private static double startX;
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(!Boolean.getBoolean("magnetization.audit.ui") || !System.getProperty("magnetization.audit.uiMode","").equals("curios") || failed || passed) return;
        var p=event.getServer().getPlayerList().getPlayerByName("AuditUi"); if(p==null)return;
        try {
            var level=p.serverLevel();
            if(!setup) {
                for(int x=-4;x<12;x++)for(int z=-4;z<12;z++)level.setBlockAndUpdate(new BlockPos(x,80,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(8,81,0),MagBlocks.PERMANENT_MAGNET.get().defaultBlockState().setValue(PermanentMagnetBlock.POLARITY,MagneticPolarity.SOUTH));
                p.teleportTo(0.5,81,0.5);p.setYRot(0);p.setXRot(0);startX=p.getX();
                p.getInventory().setItem(2,new ItemStack(MagItems.MAGNETIC_GRAPPLE.get()));
                p.getInventory().setItem(3,new ItemStack(MagItems.REPULSOR_GUN.get()));
                p.inventoryMenu.broadcastChanges();
                target=new ItemEntity(level,0.5,82,4.5,new ItemStack(Items.STONE));target.setNoGravity(true);target.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);level.addFreshEntity(target);
                setup=true; LOG.info("UI_CURIOS_FIXTURE emitter=8,81,0 loose_target=0.5,82,4.5");return;
            }
            var handler=p.getCapability(top.theillusivec4.curios.api.CuriosCapability.INVENTORY);
            ItemStack gun=null,grapple=null;
            for(var slots:handler.getCurios().values())for(int i=0;i<slots.getStacks().getSlots();i++) {
                var stack=slots.getStacks().getStackInSlot(i);
                if(stack.is(MagItems.REPULSOR_GUN.get()))gun=stack;
                if(stack.is(MagItems.MAGNETIC_GRAPPLE.get()))grapple=stack;
            }
            if(gun!=null && gun.has(MagDataComponents.FIRED_AT.get()) && !gunSeen) {
                RecipeCheck.check(p.getMainHandItem().isEmpty() && p.getOffhandItem().isEmpty(),"Activation requires empty hands control");
                RecipeCheck.check(target.getDeltaMovement().z>0.01 || target.getZ()>4.6,"Repulsor packet did not push loose target");
                gunSeen=true;LOG.info("UI_CURIOS_REPULSOR_SERVER_PASS stamp={} targetZ={} velocity={}",gun.get(MagDataComponents.FIRED_AT.get()),target.getZ(),target.getDeltaMovement());
            }
            if(grapple!=null && grapple.has(MagDataComponents.FIRED_AT.get()) && GrappleTickHandler.isPulling(p)) pullSeen=true;
            if(gunSeen && pullSeen && p.getX()>startX+2) {
                passed=true;LOG.info("UI_CURIOS_SERVER_PASS network_activation=true equipped_stamp=true repulsor_target_motion=true grapple_player_motion={} ",p.position());
            }
        } catch(Exception|AssertionError e) {failed=true;LOG.error("UI_AUDIT_FAILED curios_server",e);}
    }
    private static class RecipeCheck { static void check(boolean v,String m){if(!v)throw new IllegalStateException(m);} }
}
