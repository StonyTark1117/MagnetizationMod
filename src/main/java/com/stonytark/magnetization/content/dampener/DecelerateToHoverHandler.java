package com.stonytark.magnetization.content.dampener;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Feel flair for the two magnetoresistive fall-savers (Magnetic Cushion Boots +
 * Magnetic Cushion block): a genuine fall is braked as it nears the surface, then
 * held in a brief HOVER, then released to settle. One hover per descent — it is
 * NOT a constant field, so walking up to the block doesn't get shoved around.
 *
 * <p>Damage negation stays owned by {@link FallSaveHandler} (boots) and
 * {@link GForceCushionBlock#fallOn} (block); this only shapes the descent.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class DecelerateToHoverHandler {

    /** Brake only within this many blocks of the landing surface. */
    private static final int ACTIVATION_HEIGHT = 7;
    /** Only ENGAGE on a genuine fall this fast (blocks/tick, negative) — so
     *  ordinary walking/stepping near the block never triggers it. */
    private static final double ENGAGE_FALL_SPEED = -0.6d;
    /** Start the hover once this close to the surface. */
    private static final double HOVER_TRIGGER = 1.3d;
    /** Descent speed held during the hover — a near-stop that reads as a hover. */
    private static final double HOVER_DESCENT = 0.02d;
    /** How long the hover holds (ticks) before releasing to settle. */
    private static final long HOVER_DURATION = 16L;
    /** Cap on the braked descent speed far from the surface. */
    private static final double MAX_DESCENT = 0.7d;
    /** Sentinel: this descent already had its hover — settle normally, don't re-hover. */
    private static final long DONE = Long.MIN_VALUE;

    /** Per-player hover state: gametick the hover ends, or {@link #DONE}. Cleared
     *  when the player lands / flies / leaves the zone. Transient (server-side). */
    private static final Map<UUID, Long> HOVER = new HashMap<>();

    private DecelerateToHoverHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event) {
        final Player player = event.getEntity();
        final UUID id = player.getUUID();
        if (player.getAbilities().flying || player.isFallFlying() || player.onGround()) {
            HOVER.remove(id); // reset for the next fall
            return;
        }

        final Level level = player.level();
        final boolean bootsWorn =
                player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof MagnetoresistiveBootsItem;
        final boolean metallicArmor = hasMetallicArmor(player);

        // Scan straight down for the first landing surface within reach: a Magnetic
        // Cushion (grips metallic armor only) or, with boots, any solid ground.
        final BlockPos feet = player.blockPosition();
        double surfaceTop = Double.NaN;
        boolean cushioned = false;
        for (int dy = 0; dy <= ACTIVATION_HEIGHT; dy++) {
            final BlockPos p = feet.below(dy);
            final BlockState state = level.getBlockState(p);
            if (state.is(MagBlocks.G_FORCE_CUSHION.get()) && metallicArmor) {
                surfaceTop = p.getY() + 1.0;
                cushioned = true;
                break;
            }
            if (bootsWorn && state.blocksMotion()) {
                surfaceTop = p.getY() + 1.0;
                break;
            }
        }
        if (Double.isNaN(surfaceTop)) { HOVER.remove(id); return; }

        final double dist = player.getY() - surfaceTop;
        if (dist <= 0.0) { HOVER.remove(id); return; }

        final Vec3 vel = player.getDeltaMovement();
        final long now = level.getGameTime();
        final Long state = HOVER.get(id);

        if (state != null) {
            if (state == DONE) return;            // already hovered this descent — settle
            if (now < state) {                    // holding the hover (near-stop)
                if (vel.y < -HOVER_DESCENT) {
                    apply(player, vel, -HOVER_DESCENT, level, cushioned, dist);
                }
                return;
            }
            HOVER.put(id, DONE);                  // hover over — release, settle from here
            return;
        }

        // Not yet engaged: only a genuine fall triggers the brake.
        if (vel.y >= ENGAGE_FALL_SPEED) return;

        // Decelerate, ramping the allowed descent down as the surface nears.
        final double f = Math.min(1.0, Math.max(0.0, (dist - HOVER_TRIGGER) / (ACTIVATION_HEIGHT - HOVER_TRIGGER)));
        final double allowed = -(HOVER_DESCENT + f * (MAX_DESCENT - HOVER_DESCENT));
        if (vel.y < allowed) {
            apply(player, vel, allowed, level, cushioned, dist);
        }
        // Once close enough, begin the brief hover.
        if (dist <= HOVER_TRIGGER) {
            HOVER.put(id, now + HOVER_DURATION);
        }
    }

    /** Clamp the player's descent to {@code targetY} (only ever slowing them) and
     *  emit the brake spark/chime. Leaves fallDistance intact so the landing event
     *  still fires for damage negation + boot durability. */
    private static void apply(final Player player, final Vec3 vel, final double targetY,
                              final Level level, final boolean cushioned, final double dist) {
        player.setDeltaMovement(vel.x, targetY, vel.z);
        player.hasImpulse = true;
        player.hurtMarked = true;
        if (level instanceof ServerLevel server && (player.tickCount % 2) == 0) {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    player.getX(), player.getY() + 0.1, player.getZ(),
                    3, 0.25, 0.05, 0.25, 0.02);
            if (cushioned && dist < 1.5) {
                level.playSound(null, player.blockPosition(), SoundEvents.LODESTONE_PLACE,
                        SoundSource.BLOCKS, 0.3f, 1.6f);
            }
        }
    }

    private static boolean hasMetallicArmor(final Player player) {
        for (final ItemStack armor : player.getArmorSlots()) {
            if (armor.is(MagTags.METAL_ARMOR)) return true;
        }
        return false;
    }
}
