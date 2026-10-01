package com.stonytark.magnetization.audit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.*;

@EventBusSubscriber(modid="magnetization",value=Dist.CLIENT)
public final class SlugterraAuditClient {
    static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("SlugterraAuditClient");
    static Entity tracked;
    static int age;
    static long frame;
    static final Set<UUID> captured=new HashSet<>();
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(System.getProperty("magnetization.slugterraAudit")==null)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        tracked=null;
        for(var entity:mc.level.entitiesForRendering()) if(entity.hasCustomName()&&entity.getCustomName().getString().startsWith("Flight ")) {tracked=entity;break;}
        if(tracked==null) for(var entity:mc.level.entitiesForRendering())
            if(entity.hasCustomName()&&entity.getCustomName().getString().startsWith("HUD magnetic"))
                mc.player.lookAt(EntityAnchorArgument.Anchor.EYES,entity.getBoundingBox().getCenter());
        if(tracked!=null){
            if(mc.screen!=null)mc.setScreen(null);
            mc.player.lookAt(EntityAnchorArgument.Anchor.EYES,tracked.getBoundingBox().getCenter());
            if(age++%2==0)LOG.info("CLIENT_SAMPLE role={} tick={} uuid={} type={} pos={} velocity={} renderer={}",System.getProperty("magnetization.slugterraAudit"),mc.level.getGameTime(),tracked.getUUID(),BuiltInRegistries.ENTITY_TYPE.getKey(tracked.getType()),tracked.position(),tracked.getDeltaMovement(),mc.getEntityRenderDispatcher().getRenderer(tracked).getClass().getName());
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES||tracked==null)return;
        var mc=Minecraft.getInstance();frame++;
        if(frame%10==0)LOG.info("RENDER_FRAME uuid={} visible={} pos={}",tracked.getUUID(),event.getFrustum().isVisible(tracked.getBoundingBox()),tracked.position());
        if(tracked.tickCount>=8&&captured.add(tracked.getUUID()))Screenshot.grab(mc.gameDirectory,"flight-"+tracked.getCustomName().getString().split(" ")[1]+".png",mc.getMainRenderTarget(),c->LOG.info("CAPTURE {}",c.getString()));
    }
}
