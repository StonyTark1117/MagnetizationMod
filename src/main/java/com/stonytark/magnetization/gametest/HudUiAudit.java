package com.stonytark.magnetization.gametest;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Real assembled blocks, moving and rotating in a connected server's world. */
@EventBusSubscriber(modid="magnetization")
public final class HudUiAudit {
    public static final List<String> BLOCKS=List.of("permanent_magnet","kinetic_electromagnet","gas_exciter","gas_vent","air_separator","magnetostrictive_sensor","barkhausen_generator","gyrostabilizer","induction_pad");
    private static ServerSubLevel ship; private static int ticks; private static boolean failed;
    private static Boolean appliedMaster;
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        String mode=System.getProperty("magnetization.audit.uiMode","");
        if(!Boolean.getBoolean("magnetization.audit.ui") || !List.of("jade","wthit","top","goggles").contains(mode) || failed)return;
        var p=event.getServer().getPlayerList().getPlayerByName("AuditUi");if(p==null)return;
        try {
            var control=java.nio.file.Path.of(System.getProperty("magnetization.audit.uiControl"));
            if(java.nio.file.Files.exists(control)) {
                boolean enabled=Boolean.parseBoolean(java.nio.file.Files.readString(control).trim());
                if(appliedMaster==null || appliedMaster!=enabled) {
                    setMaster(mode,enabled);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,new com.stonytark.magnetization.network.CommonConfigSyncPayload(com.stonytark.magnetization.config.MagConfig.commonSnapshot()));
                    appliedMaster=enabled;
                    org.slf4j.LoggerFactory.getLogger("magnetization/ui-audit").info("UI_HUD_MASTER_SYNC viewer={} enabled={}",mode,enabled);
                }
            }
            var level=p.serverLevel();var c=SubLevelContainer.getContainer(level);
            if(ship==null) {
                p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(0.5,102,-3);
                var blocks=new ArrayList<BlockPos>();
                for(int x=0;x<BLOCKS.size()*2;x++){var floor=new BlockPos(x,100,0);level.setBlockAndUpdate(floor,Blocks.IRON_BLOCK.defaultBlockState());blocks.add(floor);}
                for(int i=0;i<BLOCKS.size();i++) {
                    var id=ResourceLocation.parse("magnetization:"+BLOCKS.get(i));
                    if(!BuiltInRegistries.BLOCK.containsKey(id))throw new IllegalStateException("Missing HUD fixture "+id);
                    var pos=new BlockPos(i*2,101,0);level.setBlockAndUpdate(pos,BuiltInRegistries.BLOCK.get(id).defaultBlockState());blocks.add(pos);
                }
                ship=SubLevelAssemblyHelper.assembleBlocks(level,new BlockPos(0,101,0),blocks,new BoundingBox3i(0,100,0,BLOCKS.size()*2,102,1));
                c.addForceLoadTicket(ship,dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED,net.minecraft.util.Unit.INSTANCE);
                if(mode.equals("top"))p.getInventory().setItem(8,new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("theoneprobe:probe"))));
                if(mode.equals("goggles"))p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,com.simibubi.create.AllItems.GOGGLES.asStack());
                p.inventoryMenu.broadcastChanges();
                org.slf4j.LoggerFactory.getLogger("magnetization/ui-audit").info("UI_HUD_SHIP_CREATED uuid={} blocks={} pose={}",ship.getUniqueId(),BLOCKS,ship.logicalPose());
            }
            var handle=c.physicsSystem().getPhysicsHandle(ship);
            handle.addLinearAndAngularVelocity(handle.getLinearVelocity(new org.joml.Vector3d()).negate(),handle.getAngularVelocity(new org.joml.Vector3d()).negate());
            if(++ticks%10==0) {
                // Controlled transforms keep every native overlay sample in reach;
                // real Sable sublevel blocks and targeting are never substituted.
                c.physicsSystem().getPipeline().teleport(ship,new org.joml.Vector3d(8+Math.sin(ticks/60.0),102,0.5),new org.joml.Quaterniond().rotateY(Math.sin(ticks/80.0)*0.12));
                if(ticks%100==0)org.slf4j.LoggerFactory.getLogger("magnetization/ui-audit").info("UI_HUD_SHIP_MOTION tick={} pose={}",ticks,ship.logicalPose());
            }
        }catch(Exception|AssertionError e){failed=true;org.slf4j.LoggerFactory.getLogger("magnetization/ui-audit").error("UI_AUDIT_FAILED hud_server",e);}
    }
    public static void setMaster(String mode,boolean enabled) {
        switch(mode) {
            case "jade" -> com.stonytark.magnetization.config.MagConfig.JADE_COMPAT_ENABLED.set(enabled);
            case "wthit" -> com.stonytark.magnetization.config.MagConfig.WTHIT_COMPAT_ENABLED.set(enabled);
            case "top" -> com.stonytark.magnetization.config.MagConfig.THE_ONE_PROBE_COMPAT_ENABLED.set(enabled);
        }
    }
}
