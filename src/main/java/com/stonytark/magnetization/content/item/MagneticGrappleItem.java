package com.stonytark.magnetization.content.item;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticFieldSource;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.physics.EmitterRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Right-click to scan for the nearest active <em>attractive</em> emitter within
 * 24 blocks and yank the player toward it. Just-Cause-style traversal that
 * turns magnetic infrastructure into a movement system.
 *
 * <p>The actual pull is sustained per-tick by {@link GrappleTickHandler} —
 * vanilla drag/gravity decelerate the player too quickly for a one-shot
 * impulse to traverse the full scan range, so we re-apply velocity each tick
 * until the player nears the anchor or the duration cap fires. Cooldown is
 * applied at pull-end, not click time.
 *
 * <p>Only attractive (SOUTH-polarity) fields qualify — repulsive emitters
 * push away, which would be unusable as a grapple anchor. Players can use the
 * Polarity Inverter to flip a NORTH emitter for grapple use.
 */
public class MagneticGrappleItem extends Item {

    private static final double SCAN_RADIUS = 24.0d;

    public MagneticGrappleItem(final Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        if (GrappleTickHandler.isPulling(player)) return InteractionResultHolder.fail(stack);

        final BlockPos anchor = findAttractiveAnchor(level, player.position());
        if (anchor == null) {
            player.displayClientMessage(
                    Component.translatable("grapple.magnetization.no_anchor").withStyle(ChatFormatting.GRAY),
                    true);
            return InteractionResultHolder.fail(stack);
        }

        GrappleTickHandler.start(player, anchor);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LODESTONE_HIT, SoundSource.PLAYERS, 0.6f, 1.4f);
        return InteractionResultHolder.success(stack);
    }

    private static @Nullable BlockPos findAttractiveAnchor(final Level level, final net.minecraft.world.phys.Vec3 from) {
        BlockPos best = null;
        double bestDistSqr = SCAN_RADIUS * SCAN_RADIUS;
        // Iterate the registry instead of walking chunks. Set is per-side so the
        // server-side use() finds entries registered server-side.
        for (var pos : EmitterRegistry.snapshot(level)) {
            final double d2 = pos.getCenter().distanceToSqr(from);
            if (d2 >= bestDistSqr) continue;
            final BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof MagneticFieldSource source)) continue;
            final MagneticField field = source.currentField();
            if (field == null) continue;
            // Only attractive emitters qualify as grapple anchors. Repel would push
            // the player back, which is unusable.
            if (field.polarity() != MagneticPolarity.SOUTH) continue;
            best = pos.immutable();
            bestDistSqr = d2;
        }
        return best;
    }
}
