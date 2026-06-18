package com.stonytark.magnetization.content.mrtools;

import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagTiers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Magnetorheological-fluid tools. Iron-equivalent in the hand, but the fluid
 * snaps rigid the instant the tool is actually used — every swing or block
 * broken stamps {@link MagDataComponents#HARDENED_UNTIL}, which the
 * {@code magnetization:hardened} item property reads to swap the flowing-fluid
 * icon for the rigid plate one. Held idle, they ripple; used, they harden.
 *
 * <p>The marker interface lets {@code MagClientRegistration} register the
 * hardened property for the whole set in one loop.
 */
public final class MrFluidTools {

    /** How long after a use the tool reads as hardened (texture-swap window). */
    private static long hardenTicks() {
        try { return com.stonytark.magnetization.config.MagConfig.MR_TOOL_HARDEN_TICKS.get(); }
        catch (Throwable t) { return 14L; }
    }

    /** Marker for every MR-fluid tool — used to register the hardened icon swap. */
    public interface Marker {}

    private MrFluidTools() {}

    static void harden(final ItemStack stack, final LivingEntity user) {
        if (user != null && user.level() != null) {
            stack.set(MagDataComponents.HARDENED_UNTIL.get(), user.level().getGameTime() + hardenTicks());
        }
    }

    /** Only re-play the first-person equip animation on a real swap, not when our
     *  {@code HARDENED_UNTIL} stamp (or durability) ticks over — otherwise the
     *  held tool visibly "bounces" every time it hardens. */
    static boolean reequip(final ItemStack oldStack, final ItemStack newStack, final boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    public static final class Sword extends SwordItem implements Marker {
        public Sword(final Properties props) { super(MagTiers.MR_FLUID, props); }
        @Override public boolean shouldCauseReequipAnimation(final ItemStack o, final ItemStack n, final boolean slotChanged) { return reequip(o, n, slotChanged); }
        @Override public boolean hurtEnemy(final ItemStack stack, final LivingEntity target, final LivingEntity attacker) {
            harden(stack, attacker);
            return super.hurtEnemy(stack, target, attacker);
        }
        @Override public boolean mineBlock(final ItemStack stack, final Level level, final BlockState state, final BlockPos pos, final LivingEntity miner) {
            harden(stack, miner);
            return super.mineBlock(stack, level, state, pos, miner);
        }
    }

    public static final class Pickaxe extends PickaxeItem implements Marker {
        public Pickaxe(final Properties props) { super(MagTiers.MR_FLUID, props); }
        @Override public boolean shouldCauseReequipAnimation(final ItemStack o, final ItemStack n, final boolean slotChanged) { return reequip(o, n, slotChanged); }
        @Override public boolean hurtEnemy(final ItemStack stack, final LivingEntity target, final LivingEntity attacker) {
            harden(stack, attacker);
            return super.hurtEnemy(stack, target, attacker);
        }
        @Override public boolean mineBlock(final ItemStack stack, final Level level, final BlockState state, final BlockPos pos, final LivingEntity miner) {
            harden(stack, miner);
            return super.mineBlock(stack, level, state, pos, miner);
        }
    }

    public static final class Axe extends AxeItem implements Marker {
        public Axe(final Properties props) { super(MagTiers.MR_FLUID, props); }
        @Override public boolean shouldCauseReequipAnimation(final ItemStack o, final ItemStack n, final boolean slotChanged) { return reequip(o, n, slotChanged); }
        @Override public boolean hurtEnemy(final ItemStack stack, final LivingEntity target, final LivingEntity attacker) {
            harden(stack, attacker);
            return super.hurtEnemy(stack, target, attacker);
        }
        @Override public boolean mineBlock(final ItemStack stack, final Level level, final BlockState state, final BlockPos pos, final LivingEntity miner) {
            harden(stack, miner);
            return super.mineBlock(stack, level, state, pos, miner);
        }
    }

    public static final class Shovel extends ShovelItem implements Marker {
        public Shovel(final Properties props) { super(MagTiers.MR_FLUID, props); }
        @Override public boolean shouldCauseReequipAnimation(final ItemStack o, final ItemStack n, final boolean slotChanged) { return reequip(o, n, slotChanged); }
        @Override public boolean hurtEnemy(final ItemStack stack, final LivingEntity target, final LivingEntity attacker) {
            harden(stack, attacker);
            return super.hurtEnemy(stack, target, attacker);
        }
        @Override public boolean mineBlock(final ItemStack stack, final Level level, final BlockState state, final BlockPos pos, final LivingEntity miner) {
            harden(stack, miner);
            return super.mineBlock(stack, level, state, pos, miner);
        }
    }

    public static final class Hoe extends HoeItem implements Marker {
        public Hoe(final Properties props) { super(MagTiers.MR_FLUID, props); }
        @Override public boolean shouldCauseReequipAnimation(final ItemStack o, final ItemStack n, final boolean slotChanged) { return reequip(o, n, slotChanged); }
        @Override public boolean hurtEnemy(final ItemStack stack, final LivingEntity target, final LivingEntity attacker) {
            harden(stack, attacker);
            return super.hurtEnemy(stack, target, attacker);
        }
        @Override public boolean mineBlock(final ItemStack stack, final Level level, final BlockState state, final BlockPos pos, final LivingEntity miner) {
            harden(stack, miner);
            return super.mineBlock(stack, level, state, pos, miner);
        }
    }
}
