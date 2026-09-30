package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.registry.MagDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Calls pinned upstream attack/spell producers; damage is delivered by their actual world ticks. */
@GameTestHolder("magnetization_native_lightning")
@PrefixGameTestTemplate(false)
public final class NativeLightningGameTests {
    @GameTest(template="empty", timeoutTicks=240, batch="native_ironsLightningBolt")
    public static void ironsLightningBolt(GameTestHelper h) throws Exception { spell(h, "LightningBoltSpell"); }
    @GameTest(template="empty", timeoutTicks=240, batch="native_ironsChainLightning")
    public static void ironsChainLightning(GameTestHelper h) throws Exception { spell(h, "ChainLightningSpell"); }
    @GameTest(template="empty", timeoutTicks=240, batch="native_ironsLightningLance")
    public static void ironsLightningLance(GameTestHelper h) throws Exception { spell(h, "LightningLanceSpell"); }
    @GameTest(template="empty", timeoutTicks=240, batch="native_ironsBallLightning")
    public static void ironsBallLightning(GameTestHelper h) throws Exception { spell(h, "BallLightningSpell"); }
    @GameTest(template="empty", timeoutTicks=240, batch="native_ironsElectrocute")
    public static void ironsElectrocute(GameTestHelper h) throws Exception { spell(h, "ElectrocuteSpell"); }
    @GameTest(template="empty", timeoutTicks=240, batch="native_ironsAscension")
    public static void ironsAscension(GameTestHelper h) throws Exception { spell(h, "AscensionSpell"); }
    @GameTest(template="empty", timeoutTicks=240, batch="native_ironsShockwave")
    public static void ironsShockwave(GameTestHelper h) throws Exception { spell(h, "ShockwaveSpell"); }
    @GameTest(template="empty", timeoutTicks=240, batch="native_ironsThunderstorm")
    public static void ironsThunderstorm(GameTestHelper h) throws Exception { spell(h, "ThunderstormSpell"); }
    @GameTest(template="empty", timeoutTicks=240, batch="native_ironsVoltStrike")
    public static void ironsVoltStrike(GameTestHelper h) throws Exception { spell(h, "VoltStrikeSpell"); }

    private static void spell(GameTestHelper h, String name) throws Exception {
        final var level = h.getLevel();
        // This upstream version initializes spell schools on the player datapack-sync event.
        // GameTest servers have no joining player; dispatch the normal lifecycle event first.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new net.neoforged.neoforge.event.OnDatapackSyncEvent(level.getServer().getPlayerList(), null));
        final var base = arena(h, 40);
        for (var p : BlockPos.betweenClosed(base.offset(-2,-1,-2),base.offset(2,-1,8))) level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
        final var caster = (Mob) BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("irons_spellbooks:electromancer")).create(level);
        caster.setNoAi(true); caster.setNoGravity(true); caster.setPos(Vec3.atBottomCenterOf(base));
        caster.setYRot(0); caster.setYHeadRot(0); caster.setXRot(0);
        level.addFreshEntity(caster);
        final var victim = victim(h, base.south(name.equals("VoltStrikeSpell") ? 1 : 3), name.equals("ThunderstormSpell"));
        caster.setTarget(victim);
        if (name.equals("ThunderstormSpell")) {
            // Native area targeting respects teams; mark a hostile target explicitly.
            final var board = level.getScoreboard();
            final var team = board.getPlayerTeam("audit_thunder_caster") == null ? board.addPlayerTeam("audit_thunder_caster") : board.getPlayerTeam("audit_thunder_caster");
            board.addPlayerToTeam(caster.getStringUUID(), team);
        }
        final var spellClass = Class.forName("io.redspace.ironsspellbooks.spells.lightning."+name);
        final String field = name.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(java.util.Locale.ROOT);
        final var spell = ((java.util.function.Supplier<?>)Class.forName("io.redspace.ironsspellbooks.api.registry.SpellRegistry").getField(field).get(null)).get();
        final var magicClass = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
        final var magic = magicClass.getMethod("getPlayerMagicData",LivingEntity.class).invoke(null,caster);
        final var sourceClass = Class.forName("io.redspace.ironsspellbooks.api.spells.CastSource");
        final var source = sourceClass.getField("MOB").get(null);
        final boolean ready = (boolean)spellClass.getMethod("checkPreCastConditions",Level.class,int.class,LivingEntity.class,magicClass)
                .invoke(spell,level,1,caster,magic);
        h.assertTrue(ready,name+" native pre-cast did not find target");
        spellClass.getMethod("onCast",Level.class,int.class,LivingEntity.class,sourceClass,magicClass)
                .invoke(spell,level,1,caster,source,magic);
        verifyHit(h, victim, caster, "irons_spellbooks:lightning_magic", name);
    }

    @GameTest(template="empty", timeoutTicks=80, batch="native_scyllaNativeStorm")
    public static void scyllaNativeStorm(GameTestHelper h) throws Exception {
        final var level=h.getLevel(); final var base=arena(h, 40);
        final var victim=victim(h,base.south(3));
        level.setBlockAndUpdate(base.south(3).below(),Blocks.STONE.defaultBlockState());
        final var caster=(Mob)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("cataclysm:scylla")).create(level);
        caster.setNoAi(true); caster.setNoGravity(true); caster.setPos(Vec3.atBottomCenterOf(base)); level.addFreshEntity(caster);
        final var producer=caster.getClass().getDeclaredMethod("spawnLightning",double.class,double.class,double.class,double.class,float.class,int.class,float.class);
        producer.setAccessible(true);
        producer.invoke(caster,victim.getX(),victim.getZ(),victim.getY()-2,victim.getY()+3,0f,0,3.5f);
        verifyHit(h,victim,caster,"cataclysm:lightning","Scylla spawnLightning");
    }

    @GameTest(template="empty", timeoutTicks=100, batch="native_scyllaNativeSpearGoal")
    public static void scyllaNativeSpearGoal(GameTestHelper h) throws Exception {
        final var level=h.getLevel(); final var base=arena(h, 120);
        final var victim=victim(h,base.south(7));
        final var caster=(Mob)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("cataclysm:scylla")).create(level);
        caster.setNoAi(true); caster.setNoGravity(true); caster.setPos(Vec3.atBottomCenterOf(base)); caster.setTarget(victim); level.addFreshEntity(caster);
        caster.getClass().getMethod("setAttackState",int.class).invoke(caster,10);
        caster.getClass().getField("attackTicks").setInt(caster,27);
        final var goalClass=Class.forName(caster.getClass().getName()+"$SpearThrowGoal");
        final var ctor=goalClass.getDeclaredConstructor(caster.getClass(),int.class,float.class,float.class,int.class,float.class);
        ctor.setAccessible(true);final var goal=ctor.newInstance(caster,0,0f,32f,30,100f);
        final var tick=goalClass.getDeclaredMethod("tick");tick.setAccessible(true);tick.invoke(goal);
        verifyHit(h,victim,caster,"cataclysm:lightning","Scylla native spear goal");
    }

    @GameTest(template="empty", timeoutTicks=80, batch="native_scyllaNativeElectricWhip")
    public static void scyllaNativeElectricWhip(GameTestHelper h) throws Exception {
        final var level=h.getLevel();final var base=arena(h, 200);
        final var victim=victim(h,base.south(3));
        final var caster=(Mob)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("cataclysm:scylla")).create(level);
        caster.setNoAi(true);caster.setNoGravity(true);caster.setPos(Vec3.atBottomCenterOf(base));level.addFreshEntity(caster);
        final var whip=caster.getClass().getDeclaredMethod("Whip",double.class,double.class,double.class,double.class,int.class,float.class,float.class,float.class);
        whip.setAccessible(true);whip.invoke(caster,0d,5d,1d,3d,1,0f,1f,0f);
        verifyHit(h,victim,caster,"cataclysm:lightning","Scylla native electric whip");
    }

    private static BlockPos arena(GameTestHelper h, int height) {
        final var base = h.absolutePos(new BlockPos(2, height, 2));
        // Fixtures extend beyond the tiny template. Keep every projectile/target
        // chunk entity-ticking, including when the randomized origin meets a border.
        for (int x = (base.getX() - 24) >> 4; x <= (base.getX() + 24) >> 4; x++) {
            for (int z = (base.getZ() - 24) >> 4; z <= (base.getZ() + 24) >> 4; z++) {
                h.getLevel().setChunkForced(x, z, true);
                h.getLevel().getChunk(x, z);
            }
        }
        return base;
    }

    private static Mob victim(GameTestHelper h, BlockPos pos) {
        return victim(h, pos, false);
    }
    private static Mob victim(GameTestHelper h, BlockPos pos, boolean hostile) {
        final Mob victim=hostile ? EntityType.HUSK.create(h.getLevel()) : EntityType.IRON_GOLEM.create(h.getLevel());
        victim.setNoAi(true); victim.setNoGravity(true); victim.setPos(Vec3.atBottomCenterOf(pos));
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024); victim.setHealth(1024);
        victim.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));
        h.getLevel().addFreshEntity(victim); return victim;
    }
    private static void verifyHit(GameTestHelper h, Mob victim, LivingEntity caster, String damageId, String producer) {
        final float[] lastHealth = {1024};
        h.onEachTick(() -> {
            if (victim.getHealth() != lastHealth[0]) {
                org.slf4j.LoggerFactory.getLogger("magnetization/native-lightning-audit").info("NATIVE_HIT producer={} health={} source={} armor={}", producer, victim.getHealth(), victim.getLastDamageSource(), victim.getItemBySlot(EquipmentSlot.CHEST));
                lastHealth[0] = victim.getHealth();
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(victim.getHealth()<1024, producer+" did not damage target; caster ticks="+caster.tickCount+" victim ticks="+victim.tickCount);
            final var damage=victim.getLastDamageSource();
            h.assertTrue(damage!=null && damage.typeHolder().unwrapKey().orElseThrow().location().equals(ResourceLocation.parse(damageId)), producer+" did not deliver expected damage type: "+(damage == null ? "none" : damage.typeHolder().unwrapKey()));
            h.assertTrue(damage.getEntity() == caster, producer+" was damaged by another fixture's producer");
            h.assertTrue(victim.getItemBySlot(EquipmentSlot.CHEST).has(MagDataComponents.ARMOR_POLARITY.get()),producer+" damage did not trigger LIRM");
            victim.discard(); caster.discard();
        });
    }
}
