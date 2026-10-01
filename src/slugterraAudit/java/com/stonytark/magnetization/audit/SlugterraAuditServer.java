package com.stonytark.magnetization.audit;

import com.stonytark.magnetization.api.*;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.compat.SlugterraProjectileCompat;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.registry.*;
import falconnex.legendsofslugterra.network.SlugterraModVariables;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;

/** Test-only controller: real dedicated server, normal entity tracking and two real connected clients. */
@EventBusSubscriber(modid="magnetization")
public final class SlugterraAuditServer {
    static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("SlugterraAudit");
    static final String[] TYPES={"slugterra:armashelt","slugterra:rammstone","slugterra_dark:dark_armashelt","slugterra_dark:dark_rammstone"};
    static int age=-1, scenario=-1;
    static Entity shot;
    static Vec3 initial;
    static boolean enabled, ability;
    static ServerPlayer owner;
    static final List<String> failures=new ArrayList<>();
    static boolean configured;
    static int samples, bends, abilityTicks, maxFists;
    static UUID protoUuid;
    static int combat;
    static LivingEntity hudSlug;
    static boolean hudHadEffect;

    @SubscribeEvent public static void tick(ServerTickEvent.Post e) throws Exception {
        if (!"server".equals(System.getProperty("magnetization.slugterraAudit"))) return;
        var server=e.getServer(); var level=server.overworld();
        if (server.getPlayerList().getPlayerCount()<2) return;
        if (!configured) {
            final var cleanup = new ArrayList<Entity>();
            level.getAllEntities().forEach(entity -> {
                if (entity != null && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace().startsWith("slugterra")) cleanup.add(entity);
            });
            cleanup.forEach(Entity::discard);
            owner=server.getPlayerList().getPlayers().stream().filter(p->p.getName().getString().equals("SlugShooter")).findFirst().orElseThrow();
            for(int x=-24;x<=40;x++) for(int z=-12;z<=14;z++) level.setBlock(new BlockPos(x,100,z),Blocks.SMOOTH_QUARTZ.defaultBlockState(),3);
            level.setDayTime(6000);
            int camera=0;
            for(var p:server.getPlayerList().getPlayers()) {server.getPlayerList().op(p.getGameProfile());p.setGameMode(GameType.CREATIVE);p.teleportTo(level,0.5+3*camera++,101,8.5,Set.of(),180,0);}
            MagConfig.SLUGTERRA_DEFLECTION_ENABLED.set(true);
            configured=true;
            LOG.info("AUDIT_READY two real clients connected, deflection enabled for validation");
        }
        String command=Files.exists(Path.of("control.txt"))?Files.readString(Path.of("control.txt")).trim():"";
        if(command.equals("flight") && age<0) {age=0;scenario=0;}
        if(command.startsWith("hud")) {
            Files.writeString(Path.of("control.txt"), "");
            if(command.equals("hud_clear") && hudSlug!=null) hudSlug.removeEffect(MagEffects.MAGNETIZED);
            else if(command.equals("hud_infinite") && hudSlug!=null) {hudSlug.addEffect(new MobEffectInstance(MagEffects.MAGNETIZED,-1,2));hudHadEffect=true;}
            else setupHud(level,command.equals("hud_dark") ? "slugterra_dark:dark_rammstone" : "slugterra:rammstone");
            return;
        }
        if(hudHadEffect && hudSlug!=null && !hudSlug.hasEffect(MagEffects.MAGNETIZED)) {
            hudHadEffect=false;LOG.info("HUD_EXPIRED uuid={} tick={}",hudSlug.getUUID(),level.getGameTime());
        }
        if(age<0) return;
        if(age==0) start(level);
        if(shot!=null && !shot.isRemoved()) {
            Vec3 before=shot.getDeltaMovement();
            if(ability && combat>=15 && age>3) {
                if(shot instanceof Projectile) {
                    if(before.length()>0.01 && before.normalize().dot(owner.getLookAngle().normalize())>0.999) abilityTicks++;
                } else if(shot.getPersistentData().getBoolean("FiringProjectiles")) abilityTicks++;
            }
            if(ability && combat==10 && shot instanceof Projectile && shot.getPersistentData().getBoolean("hasSwerved")) abilityTicks++;
            maxFists=Math.max(maxFists,shot.getPersistentData().getInt("ProjectilesFired"));
            if(enabled) {
                var polarity=(scenario%2==0)?MagneticPolarity.SOUTH:MagneticPolarity.NORTH;
                var field=new MagneticField(new Vec3(0,102.3,-3),new Vec3(0,0,1),polarity,MagneticStrength.EXTREME,MagneticField.Shape.OMNIDIRECTIONAL,24,100000);
                FieldApplicator.applyEntitiesOnly(level,field);
            }
            Vec3 after=shot.getDeltaMovement();
            if (before.distanceTo(after)>1e-7) {
                bends++;
                double turn=Math.acos(Math.clamp(before.normalize().dot(after.normalize()),-1,1));
                if(Math.abs(before.length()-after.length())>1e-7||turn>Math.toRadians(6)+1e-6) fail("speed/turn",shot);
            }
            if(!Double.isFinite(after.length())||after.length()>20)fail("invalid velocity",shot);
            if(age%2==0) {
                LOG.info("SERVER_SAMPLE case={} age={} tick={} uuid={} type={} pos={} velocity={} ability={} firing={}",scenario,age,level.getGameTime(),shot.getUUID(),BuiltInRegistries.ENTITY_TYPE.getKey(shot.getType()),shot.position(),after,ability,shot.getPersistentData().getBoolean("FiringProjectiles"));samples++;
            }
        }
        if(++age>=65) {
            LOG.info("CASE_COMPLETE case={} samples={} bends={} alive={} data={}",scenario,samples,bends,shot!=null&&!shot.isRemoved(),shot==null?"":shot.getPersistentData().getBoolean("FiringProjectiles"));
            if(enabled && !ability && bends==0)fail("no field deflection",shot);
            if(ability && combat>=15 && abilityTicks==0)fail("native ability never executed",shot);
            if(ability && combat==10 && shot instanceof Projectile && abilityTicks==0)fail("swerve never executed",shot);
            if(ability && combat>=15 && !(shot instanceof Projectile) && maxFists!=2)fail("native fists missing",shot);
            var recovered=level.getEntity(protoUuid);
            if(ability && combat>=15 && !(shot instanceof Projectile) && recovered==null)fail("ability recovery missing",shot);
            LOG.info("ABILITY_RESULT case={} activeTicks={} fists={} recovered={}",scenario,abilityTicks,maxFists,recovered!=null);
            if(shot!=null)shot.discard();
            if(recovered!=null)recovered.discard();
            // Four species x levels 1,10,15,20 x both poles, then the same with abilities enabled.
            if(++scenario>=64) {LOG.info("AUDIT_COMPLETE cases=64 failures={}",failures);age=-2;Files.writeString(Path.of("control.txt"),"");}
            else age=0;
        }
    }
    static void start(ServerLevel level) {
        String id=TYPES[(scenario/2)%4];combat=new int[]{1,10,15,20}[(scenario/8)%4];ability=scenario>=32;enabled=true;samples=0;bends=0;abilityTicks=0;maxFists=0;
        owner.getData(SlugterraModVariables.PLAYER_VARIABLES).VelocimorphAirAbilityActivation=ability;
        owner.setYRot(-90);owner.setXRot(0);
        shot=create(level,id,combat,owner,new Vec3(-8,102,0));initial=shot.position();protoUuid=shot.getPersistentData().getCompound("entityData").getUUID("UUID");
        shot.setCustomName(Component.literal("Flight "+scenario+" "+id));shot.setCustomNameVisible(true);
        level.addFreshEntity(shot);
        LOG.info("CASE_START case={} species={} combat={} ability={} polarity={} uuid={}",scenario,id,combat,ability,scenario%2==0?"SOUTH":"NORTH",shot.getUUID());
    }
    static Entity create(ServerLevel level,String id,int combat,ServerPlayer owner,Vec3 pos) {
        var proto=(TamableAnimal)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id)).create(level);
        proto.setOwnerUUID(owner.getUUID());proto.setTame(true,true);proto.setCustomName(Component.literal("Audit slug"));
        var data=new CompoundTag();proto.save(data);proto.discard();data.putUUID("owner",owner.getUUID());data.putInt("CombatLevel",combat);data.putInt("DataSpeed",1);
        var e=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id+"_velocimorph")).create(level);
        if(e instanceof Projectile p)p.setOwner(owner);
        if(e instanceof TamableAnimal t){t.setOwnerUUID(owner.getUUID());t.setTame(true,true);}
        e.getPersistentData().put("entityData",data);e.getPersistentData().putDouble("v_x",1);e.setPos(pos);e.setOldPosAndRot();e.setDeltaMovement(0.65,0,0);e.setNoGravity(true);return e;
    }
    static void fail(String reason,Entity entity){failures.add(scenario+":"+reason);LOG.error("AUDIT_FAILURE {} case={} entity={}",reason,scenario,entity);}
    static void setupHud(ServerLevel level,String id) {
        if(hudSlug!=null)hudSlug.discard();
        var slug=(TamableAnimal)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id)).create(level);
        hudSlug=slug;hudHadEffect=true;
        slug.setOwnerUUID(owner.getUUID());slug.setTame(true,true);slug.setNoAi(true);slug.setPos(0.5,101,3.5);slug.setCustomName(Component.literal("HUD magnetic status"));
        slug.addEffect(new MobEffectInstance(MagEffects.MAGNETIZED,400,2));level.addFreshEntity(slug);
        int camera=0;
        for(var p:level.players())p.teleportTo(level,0.5+2*camera++,101,6.5,Set.of(),180,20);
        LOG.info("HUD_READY uuid={} effect=magnetization:magnetized amplifier=2 duration=400",slug.getUUID());
    }
}
