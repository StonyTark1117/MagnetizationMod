package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.api.Lirm;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("magnetization_slugterra")
@PrefixGameTestTemplate(false)
public final class SlugterraElectricGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeTazerlingHitsStampOnceAndExpireWithoutPetrification(GameTestHelper h) throws Exception {
        final var level = h.getLevel();
        final var pos = h.absolutePos(new BlockPos(1, 70, 1));
        final var shock = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse("slugterra:electric_shock")).orElseThrow();
        level.setBlockAndUpdate(pos.east(), Blocks.OAK_LOG.defaultBlockState());
        try {
            for (String id : List.of("slugterra:tazerling_bolt", "slugterra:tazerling_velocimorph", "slugterra_dark:dark_tazerling_velocimorph")) {
                var target = EntityType.ZOMBIE.create(level);
                target.setPos(Vec3.atCenterOf(pos));
                target.setNoAi(true);
                target.setNoGravity(true);
                target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                target.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                level.addFreshEntity(target);
                var owner = EntityType.ZOMBIE.create(level);
                var projectile = (Projectile) BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id)).create(level);
                try {
                    projectile.setOwner(owner);
                    projectile.setPos(target.position());
                    projectile.setDeltaMovement(new Vec3(1, 0, 0));
                    var data = projectile.getPersistentData().getCompound("entityData");
                    data.putInt("CombatLevel", 1);
                    projectile.getPersistentData().put("entityData", data);
                    projectile.getClass().getMethod("onHitEntity", EntityHitResult.class).invoke(projectile, new EntityHitResult(target));
                    h.assertTrue(target.hasEffect(shock), "Native impact did not shock: " + id);
                    h.assertTrue(stamped(target) == 1, "Native discharge must stamp exactly one piece: " + id);
                    target.addEffect(new MobEffectInstance(shock, 600, 3));
                    h.assertTrue(stamped(target) == 1, "Effect refresh/amplification stamped another piece");
                    target.removeEffect(shock);
                    target.addEffect(new MobEffectInstance(shock, 100, 0));
                    h.assertTrue(stamped(target) == 1, "Cooldown allowed immediate second discharge");
                    h.assertTrue(level.getBlockState(pos.east()).is(Blocks.OAK_LOG), "Electric shock petrified nearby logs");
                    for (var slot : List.of(EquipmentSlot.CHEST, EquipmentSlot.HEAD)) {
                        var stack = target.getItemBySlot(slot);
                        if (stack.has(MagDataComponents.LIRM_CREATED_AT.get())) {
                            h.assertTrue(Lirm.isTemporary(stack, level.getGameTime()), "Stamp was permanent");
                            stack.set(MagDataComponents.LIRM_CREATED_AT.get(), level.getGameTime() - Lirm.DURATION_TICKS);
                        }
                    }
                    target.tickCount = 100;
                    NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(target));
                    h.assertTrue(stamped(target) == 0, "Expired mob equipment polarity remains");
                } finally { projectile.discard(); target.discard(); owner.discard(); }
            }
            h.succeed();
        } finally { level.removeBlock(pos.east(), false); }
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void electricControlsAndNonElectricDamageRemainIndependent(GameTestHelper h) {
        final var level = h.getLevel();
        final var shock = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse("slugterra:electric_shock")).orElseThrow();
        for (var setting : List.of(MagConfig.SLUGTERRA_ELECTRIC_ENABLED, MagConfig.SLUGTERRA_COMPAT_ENABLED, MagConfig.LIRM_ENABLED)) {
            boolean old = setting.get();
            var target = EntityType.ZOMBIE.create(level);
            target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            try {
                setting.set(false);
                target.addEffect(new MobEffectInstance(shock, 100, 0));
                h.assertTrue(stamped(target) == 0, "Disabled electric integration stamped armor");
            } finally { setting.set(old); target.discard(); }
        }
        var target = EntityType.ZOMBIE.create(level);
        target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        try {
            target.hurt(level.damageSources().generic(), 1);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100));
            h.assertTrue(stamped(target) == 0, "Unrelated damage/effect stamped gear");
            target.addEffect(new MobEffectInstance(shock, 100));
            h.assertTrue(stamped(target) == 1, "Fresh shock failed after negative controls");
            h.succeed();
        } finally { target.discard(); }
    }

    private static int stamped(LivingEntity target) {
        int count = 0;
        for (var stack : target.getArmorSlots()) if (stack.has(MagDataComponents.ARMOR_POLARITY.get())) count++;
        return count;
    }
}
