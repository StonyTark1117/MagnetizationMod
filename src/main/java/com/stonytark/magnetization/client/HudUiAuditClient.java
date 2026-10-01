package com.stonytark.magnetization.client;

import com.stonytark.magnetization.gametest.HudUiAudit;
import com.stonytark.magnetization.api.MagneticFieldSource;
import com.stonytark.magnetization.menu.MachineHudData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;
import static com.stonytark.magnetization.client.RecipeViewerUiAudit.*;

/** Assertions on native overlay rendering while aiming at actual moving sublevel blocks. */
@EventBusSubscriber(modid="magnetization", value=Dist.CLIENT)
public final class HudUiAuditClient {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("magnetization/ui-audit");
    private static int ticks,index,wait,stage;private static boolean finished;private static net.minecraft.world.phys.Vec3 start;private static double motion;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        String mode=System.getProperty("magnetization.audit.uiMode","");
        if(!Boolean.getBoolean("magnetization.audit.ui") || !List.of("jade","wthit","top","goggles").contains(mode) || finished)return;
        var mc=Minecraft.getInstance();if(mc.player==null || mc.level==null)return;
        try {
            mc.getToasts().clear();
            if(mode.equals("wthit")) Wthit.release(mc);
            if(++ticks<120)return;
            var ships=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(mc.level).getAllSubLevels();
            require(!ships.isEmpty(),"HUD ship did not synchronize");
            var ship=ships.getFirst();
            if(index==HudUiAudit.BLOCKS.size()){LOG.info("UI_AUDIT_PASS viewer={} moving_ship_readouts={} native_rendering=true no_duplicate_lines=true switches=native_and_master_off_on",mode,index);finished=true;return;}
            var id=ResourceLocation.parse("magnetization:"+HudUiAudit.BLOCKS.get(index));
            BlockEntity be=null;
            for(var holder:ship.getPlot().getLoadedChunks())for(var tile:holder.getChunk().getBlockEntities().values())if(BuiltInRegistries.BLOCK.getKey(tile.getBlockState().getBlock()).equals(id))be=tile;
            require(be!=null,"Ship block missing "+id);
            var pos=dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(mc.level,be.getBlockPos().getCenter());
            if(start==null)start=pos;
            motion=Math.max(motion,start.distanceTo(pos));
            mc.player.getInventory().selected=8;mc.player.getAbilities().flying=true;
            mc.player.setPos(pos.x,pos.y-0.8,pos.z-3);mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            var delta=pos.subtract(mc.player.getEyePosition());
            mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
            mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z))));
            List<String> expected=new ArrayList<>();
            if(be instanceof MagneticFieldSource f){f.fieldTooltipLines(true).forEach(l->expected.add(l.getString()));f.extraTooltipLines(true).forEach(l->expected.add(l.getString()));}
            if(be instanceof MachineHudData m)m.hudLines().forEach(l->expected.add(l.getString()));
            require(!expected.isEmpty(),"Fixture has no HUD contract "+id);
            if(++wait<40)return;
            var lines=UiAuditText.lines();
            boolean present=expected.stream().allMatch(e->lines.stream().anyMatch(l->normalize(l).contains(normalize(e))));
            if(stage==0 || stage==2 || stage==4) {
                if(!present){require(wait<160,"Missing native "+mode+" readout "+id+" expected="+expected+" rendered="+lines+" hit="+mc.hitResult);return;}
                for(String e:expected)require(lines.stream().filter(l->normalize(l).contains(normalize(e))).count()==1,"Duplicate native "+mode+" line "+e+": "+lines);
                if(stage==0) {
                    capture(mc,"ui-"+mode+"-"+id.getPath()+".png");LOG.info("UI_HUD_READOUT viewer={} block={} plot={} world={} rendered={}",mode,id,be.getBlockPos(),pos,lines);
                    toggle(mc,mode,false,be instanceof MagneticFieldSource);stage=1;wait=0;
                } else if(stage==2 && !mode.equals("goggles")){
                    LOG.info("UI_HUD_RESTORED viewer={} block={} switch=native rendered={}",mode,id,lines);master(mode,false);stage=3;wait=0;
                }
                else {LOG.info("UI_HUD_RESTORED viewer={} block={} switch={} rendered={}",mode,id,stage==4?"master":"native",lines);require(motion>0.25,"HUD ship did not move during "+id);LOG.info("UI_HUD_CASE_MOTION viewer={} block={} distance={}",mode,id,motion);index++;stage=0;wait=0;start=null;motion=0;}
            } else {
                require(!present,"Native "+mode+" off switch left Magnetization lines visible: "+lines);
                for(String e:expected)require(lines.stream().noneMatch(l->normalize(l).contains(normalize(e))),"Off switch left line visible: "+e+": "+lines);
                if(stage==1){capture(mc,"ui-"+mode+"-"+id.getPath()+"-native-off.png");toggle(mc,mode,true,be instanceof MagneticFieldSource);stage=2;}
                else {capture(mc,"ui-"+mode+"-"+id.getPath()+"-master-off.png");master(mode,true);stage=4;}
                LOG.info("UI_HUD_SWITCH viewer={} block={} off_removed_lines=true stage={}",mode,id,stage);wait=0;
            }
        } catch(Exception|AssertionError e){capture(mc,"ui-"+mode+"-failure.png");finished=true;LOG.error("UI_AUDIT_FAILED hud_client viewer="+mode+" tick="+ticks,e);}
    }
    private static void master(String mode,boolean enabled)throws Exception {
        HudUiAudit.setMaster(mode,enabled);
        java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("magnetization.audit.uiControl")),Boolean.toString(enabled));
    }
    private static void toggle(Minecraft mc,String mode,boolean enabled,boolean emitter)throws Exception {
        switch(mode) {
            case "jade" -> {
                var config=Class.forName("snownee.jade.impl.config.PluginConfig").getField("INSTANCE").get(null);
                config.getClass().getMethod("set",ResourceLocation.class,Object.class).invoke(config,ResourceLocation.parse("magnetization:"+(emitter?"field_info":"machine_info")),enabled);
            }
            case "goggles" -> mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId,5,0,net.minecraft.world.inventory.ClickType.PICKUP,mc.player);
            case "top" -> {
                var mapping=Arrays.stream(mc.options.keyMappings).filter(k->k.getName().equals("key.toggleVisible")).findFirst().orElseThrow();
                var key=com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_F8);
                mapping.setKey(key);net.minecraft.client.KeyMapping.resetMapping();
                mc.keyboardHandler.keyPress(mc.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_KEY_F8,0,org.lwjgl.glfw.GLFW.GLFW_PRESS,0);
                mc.keyboardHandler.keyPress(mc.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_KEY_F8,0,org.lwjgl.glfw.GLFW.GLFW_RELEASE,0);
            }
            case "wthit" -> Wthit.toggle(enabled);
        }
    }
    private static class Wthit {
        private static int releaseAt;
        static void release(Minecraft mc) {
            if(releaseAt>0 && ticks>=releaseAt) {
                mc.keyboardHandler.keyPress(mc.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_KEY_F8,0,org.lwjgl.glfw.GLFW.GLFW_RELEASE,0);
                releaseAt=0;
            }
        }
        static void toggle(boolean enabled) {
            var mc=Minecraft.getInstance();
            var key=com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_F8);
            mcp.mobius.waila.WailaClient.CONFIG.get().getKeyBinds().setShowOverlay(mcp.mobius.waila.config.input.KeyBind.of(key));
            mc.keyboardHandler.keyPress(mc.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_KEY_F8,0,org.lwjgl.glfw.GLFW.GLFW_PRESS,0);
            releaseAt=ticks+3;
        }
    }
}
