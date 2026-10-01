package com.stonytark.magnetization.client;

import com.stonytark.magnetization.registry.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ClickType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import static com.stonytark.magnetization.client.RecipeViewerUiAudit.*;

@EventBusSubscriber(modid="magnetization", value=Dist.CLIENT)
public final class CuriosUiAuditClient {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("magnetization/ui-audit");
    private static int ticks; private static boolean finished;private static float firstAngle;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("magnetization.audit.ui") || !System.getProperty("magnetization.audit.uiMode","").equals("curios") || finished)return;
        var mc=Minecraft.getInstance();if(mc.player==null || mc.level==null || mc.gameMode==null)return;
        try {
            mc.getToasts().clear();
            switch(++ticks) {
                case 80 -> {
                    var payload=(net.minecraft.network.protocol.common.custom.CustomPacketPayload)Class.forName("top.theillusivec4.curios.common.network.client.CPacketOpenCurios").getConstructor(ItemStack.class).newInstance(ItemStack.EMPTY);
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
                }
                case 120 -> { equip(mc,MagItems.FIELD_COMPASS.get(),"charm");equip(mc,MagItems.MAGNETIC_GRAPPLE.get(),"back");equip(mc,MagItems.REPULSOR_GUN.get(),"hands"); }
                case 160 -> {
                    require(find(mc,MagItems.FIELD_COMPASS.get())!=null && find(mc,MagItems.MAGNETIC_GRAPPLE.get())!=null && find(mc,MagItems.REPULSOR_GUN.get())!=null,"Native Curios equip did not synchronize");
                    capture(mc,"ui-curios-three-equipped.png");mc.player.closeContainer();mc.player.getInventory().selected=8;
                }
                case 190 -> {
                    require(mc.player.getMainHandItem().isEmpty() && mc.player.getOffhandItem().isEmpty(),"Hands must be empty");
                    require(UiAuditText.lines().stream().anyMatch(s -> s.startsWith("E  (90°)")),"Compass HUD bearing must point east: "+UiAuditText.lines());
                    require(UiAuditText.lines().stream().anyMatch(s -> s.contains("SOUTH")),"Equipped compass HUD missing emitter reading: "+UiAuditText.lines());
                    firstAngle=angle(mc);capture(mc,"ui-curios-compass-hud.png");mc.player.setYRot(90);
                }
                case 200 -> {
                    float next=angle(mc);require(Math.abs(next-firstAngle)>0.2,"Equipped compass needle did not follow yaw");
                    LOG.info("UI_CURIOS_NEEDLE_PASS first={} rotated={} rendered={}",firstAngle,next,UiAuditText.lines());
                    var payload=(net.minecraft.network.protocol.common.custom.CustomPacketPayload)Class.forName("top.theillusivec4.curios.common.network.client.CPacketOpenCurios").getConstructor(ItemStack.class).newInstance(ItemStack.EMPTY);
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
                }
                case 220 -> {capture(mc,"ui-curios-compass-slot-rotated.png");mc.player.closeContainer();mc.options.hideGui=true;}
                case 240 -> {require(UiAuditText.lines().stream().noneMatch(s -> s.contains("SOUTH")),"F1 failed to suppress compass HUD");capture(mc,"ui-curios-compass-hidden.png");mc.options.hideGui=false;mc.player.setYRot(0);}
                case 250 -> {bindAndClick(MagKeyBindings.USE_REPULSOR_GUN,org.lwjgl.glfw.GLFW.GLFW_KEY_F9);LOG.info("UI_CURIOS_KEY repulsor=F9");}
                case 270 -> {require(find(mc,MagItems.REPULSOR_GUN.get()).has(MagDataComponents.FIRED_AT.get()),"Repulsor key packet failed to stamp equipped stack");capture(mc,"ui-curios-repulsor-activated.png");}
                case 300 -> {bindAndClick(MagKeyBindings.USE_GRAPPLE,org.lwjgl.glfw.GLFW.GLFW_KEY_F10);LOG.info("UI_CURIOS_KEY grapple=F10");}
                case 310 -> {
                    require(find(mc,MagItems.MAGNETIC_GRAPPLE.get()).has(MagDataComponents.FIRED_AT.get()),"Grapple key packet failed to stamp equipped stack: "+UiAuditText.lines()+" player="+mc.player.position()+" screen="+mc.screen);
                    require(mc.player.getX()>2.5,"Grapple packet did not move client player");capture(mc,"ui-curios-grapple-activated.png");
                    LOG.info("UI_AUDIT_PASS curios=native_three_slot_equip_keymapping_packet_server_effect compass=equipped_needle_hud_f1");finished=true;
                }
            }
        } catch(Exception|AssertionError e){capture(mc,"ui-curios-failure.png");finished=true;LOG.error("UI_AUDIT_FAILED curios_client tick="+ticks,e);}
    }
    private static void bindAndClick(KeyMapping mapping,int code) {
        var key=com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(code);
        var mc=Minecraft.getInstance();
        require(java.util.Arrays.stream(mc.options.keyMappings).noneMatch(other -> other!=mapping && other.getKey().equals(key)),"Audit key conflicts with a native binding");
        mapping.setKey(key);KeyMapping.resetMapping();
        mc.keyboardHandler.keyPress(mc.getWindow().getWindow(),code,0,org.lwjgl.glfw.GLFW.GLFW_PRESS,0);
        mc.keyboardHandler.keyPress(mc.getWindow().getWindow(),code,0,org.lwjgl.glfw.GLFW.GLFW_RELEASE,0);
    }
    private static ItemStack find(Minecraft mc,net.minecraft.world.item.Item item) {
        var h=mc.player.getCapability(top.theillusivec4.curios.api.CuriosCapability.INVENTORY);
        for(var slots:h.getCurios().values())for(int i=0;i<slots.getStacks().getSlots();i++){var s=slots.getStacks().getStackInSlot(i);if(s.is(item))return s;}return null;
    }
    private static float angle(Minecraft mc) {
        var stack=find(mc,MagItems.FIELD_COMPASS.get());var fn=net.minecraft.client.renderer.item.ItemProperties.getProperty(stack,net.minecraft.resources.ResourceLocation.parse("magnetization:angle"));require(fn!=null,"Compass needle property missing");return fn.call(stack,mc.level,mc.player,0);
    }
    private static void equip(Minecraft mc,net.minecraft.world.item.Item item,String id) throws Exception {
        var menu=mc.player.containerMenu;int from=-1,to=-1;
        for(var slot:menu.slots){if(slot.getItem().is(item))from=slot.index;if(slot.getClass().getSimpleName().equals("CurioSlot") && slot.getClass().getMethod("getIdentifier").invoke(slot).equals(id))to=slot.index;}
        require(from>=0 && to>=0,"Curios source or slot missing "+id);
        mc.gameMode.handleInventoryMouseClick(menu.containerId,from,0,ClickType.PICKUP,mc.player);mc.gameMode.handleInventoryMouseClick(menu.containerId,to,0,ClickType.PICKUP,mc.player);
    }
}
