package com.stonytark.magnetization.client;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.shaft.MagneticShaftBlockEntity;
import com.stonytark.magnetization.network.FieldInspectionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Inert outside the opt-in engineering audit run. Captures the actual rendered UI. */
@EventBusSubscriber(modid=Magnetization.MOD_ID,value=Dist.CLIENT)
public final class MagneticEngineeringAuditClient {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("magnetization/engineering-audit");
    private static int phase=-1, phaseTicks;
    private static final boolean[] captured=new boolean[4];
    private static boolean done;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("magnetization.audit.engineering") || done) return;
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null) return;
        int next=mc.player.getX()>10?0:mc.player.getX()<1?2:mc.player.getX()<4?1:3;
        if(next!=phase) { phase=next; phaseTicks=0; }
        phaseTicks++;
        mc.options.pauseOnLostFocus=false;
        mc.options.framerateLimit().set(60);
        mc.getToasts().clear();
        mc.player.setYRot(180); mc.player.setXRot(phase==2?5:17);
        mc.options.keyShift.setDown(phase==2);
        if(phaseTicks==1) { mc.options.guiScale().set(2); mc.resizeDisplay(); mc.setScreen(null); }
        if(phaseTicks<25 || captured[phase]) return;
        try {
            if(phase==0) {
                var shaft=(MagneticShaftBlockEntity)mc.level.getBlockEntity(new BlockPos(11,65,0));
                require(shaft != null && shaft.status()==MagneticShaftBlockEntity.Status.RECEIVING && shaft.getSpeed()==32,"Shaft status/RPM did not synchronize");
                capture(mc,"engineering-shaft.png");
            }
            if(phase==1) capture(mc,"engineering-dock.png");
            if(phase==2) {
                var p=FieldInspectionPayload.latest();
                if(p==null || p.ship()==null || !FieldInspectionPayload.fresh() || p.snapshot().sources().size()<2) {
                    require(phaseTicks<150,"No real server inspection response with field contributions"); return;
                }
                require(p.snapshot().limited()>0 && p.snapshot().force().lengthSqr()>0,"Applied/capped field contributions missing");
                capture(mc,"engineering-fields.png");
                LOG.info("ENGINEERING_UI_PASS ship={} sources={} force={} torque={}",p.ship(),p.snapshot().sources().size(),p.snapshot().force(),p.snapshot().torque());
            }
            if(phase==3) {
                require(FieldInspectionPayload.latest()==null,"Inspection remained visible after releasing sneak");
                require(captured[0] && captured[1] && captured[2],"Audit missed an earlier server camera phase");
                capture(mc,"engineering-materials.png");
                LOG.info("ENGINEERING_INSPECTION_RELEASE_PASS"); LOG.info("ENGINEERING_MATERIAL_RENDER_PASS"); done=true;
            }
            captured[phase]=true;
        } catch(Exception|AssertionError e) { done=true; LOG.error("ENGINEERING_UI_FAILED phase="+phase,e); }
    }
    private static void require(boolean condition,String message) { if(!condition) throw new IllegalStateException(message); }
    private static void capture(Minecraft mc,String filename) {
        Screenshot.grab(mc.gameDirectory,filename,mc.getMainRenderTarget(),message->LOG.info("ENGINEERING_CAPTURE {} {}",filename,message.getString()));
    }
}
