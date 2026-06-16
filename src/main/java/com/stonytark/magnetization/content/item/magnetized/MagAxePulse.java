package com.stonytark.magnetization.content.item.magnetized;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Axe signature ability: chopping a log with a magnetized axe sends a single radial pull
 * pulse across nearby ferromagnetic items and magnetizable entities. The pulse decays
 * with distance and clamps to a sane max delta so it can't yeet anything across the
 * world. Theme: each chop "rings" the axe like a tuning fork.
 *
 * <p>Why on log break and not all axe breaks? Logs are the dominant axe interaction
 * and the only one where the player swings repeatedly. Other axe targets (pumpkins,
 * mushroom blocks, etc.) skip the pulse so the effect isn't always-on.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MagAxePulse {

    /** Magnetized-axe pulse radius. Notably larger than the pickaxe rip radius —
     *  the axe is the long-arm magnet of the toolset. */
    private static double pulseRadius() {
        try { return MagConfig.TOOL_AXE_PULSE_RADIUS.get(); } catch (Throwable t) { return 10.0d; }
    }
    private static double pulseStrength() {
        try { return MagConfig.TOOL_AXE_PULSE_STRENGTH.get(); } catch (Throwable t) { return 0.35d; }
    }
    private static double maxDelta() {
        try { return MagConfig.TOOL_AXE_PULSE_MAX_DELTA.get(); } catch (Throwable t) { return 0.5d; }
    }
    /** Multiplier on the pulse strength when the target item is petrified wood — the
     *  axe's signature target. ~3× the base pull, so petrified drops snap to the
     *  player even when other ferromagnetic clutter is closer. */
    private static double petrifiedPullMultiplier() {
        try { return MagConfig.TOOL_AXE_PETRIFIED_PULL_MULTIPLIER.get(); } catch (Throwable t) { return 3.0d; }
    }

    /** Per-chop chance to convert ONE of the log's drops into a Petrified Wood item.
     *  Only fires on magnetized axes — regular axes never produce petrified wood. */
    private static double petrifiedChanceMagnetized() {
        try { return MagConfig.TOOL_AXE_PETRIFIED_CHANCE.get(); } catch (Throwable t) { return 0.05d; }
    }

    private MagAxePulse() {}

    @SubscribeEvent
    public static void onBlockBreak(final BlockEvent.BreakEvent event) {
        if (!event.getState().is(BlockTags.LOGS)) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        final ItemStack axe = player.getMainHandItem();
        if (!axe.is(ItemTags.AXES)) return;

        // Petrified-wood drop only fires on magnetized axes — regular axes get nothing.
        final boolean magnetized = isMagnetized(axe);
        if (magnetized && level.random.nextDouble() < petrifiedChanceMagnetized()) {
            dropPetrifiedWood(level, event.getPos());
        }

        // Pulse only fires on a magnetized axe.
        if (!enabled() || !magnetized) return;

        final Vec3 origin = Vec3.atCenterOf(event.getPos());
        final AABB box = AABB.ofSize(origin, 2 * pulseRadius(), 2 * pulseRadius(), 2 * pulseRadius());

        for (final ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box,
                e -> !e.getItem().isEmpty() && e.getItem().is(MagTags.FERROMAGNETIC_ITEMS))) {
            final boolean petrified = item.getItem().is(MagItems.PETRIFIED_WOOD.get());
            pull(item, player.position(), petrified ? petrifiedPullMultiplier() : 1.0d);
        }
        for (final Entity entity : level.getEntities(player, box,
                e -> e.getType().is(MagTags.MAGNETIZABLE_ENTITIES))) {
            pull(entity, player.position(), 1.0d);
        }
    }

    private static boolean isMagnetized(final ItemStack axe) {
        if (!axe.is(MagTags.METAL_TOOLS)) return false;
        final MagneticPolarity pol = axe.get(MagDataComponents.ARMOR_POLARITY.get());
        return pol != null && pol != MagneticPolarity.NONE;
    }

    private static void dropPetrifiedWood(final ServerLevel level, final BlockPos pos) {
        final ItemStack drop = new ItemStack(MagItems.PETRIFIED_WOOD.get(), 1);
        final ItemEntity item = new ItemEntity(level,
                pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d, drop);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    private static void pull(final Entity target, final Vec3 toward, final double multiplier) {
        final Vec3 vec = toward.subtract(target.position());
        final double dist = vec.length();
        if (dist < 0.5 || dist > pulseRadius()) return;
        final double falloff = Math.max(0.0d, 1.0d - dist / pulseRadius());
        final Vec3 nudge = vec.normalize().scale(Math.min(maxDelta() * multiplier, pulseStrength() * falloff * multiplier));
        target.setDeltaMovement(target.getDeltaMovement().add(nudge));
        target.hurtMarked = true;
        target.hasImpulse = true;
    }

    private static boolean enabled() {
        try { return MagConfig.TOOL_AXE_PULSE_ENABLED.get(); } catch (Throwable t) { return true; }
    }
}
