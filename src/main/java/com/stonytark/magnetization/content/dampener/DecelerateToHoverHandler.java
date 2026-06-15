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

/**
 * Visual/feel flair for the two magnetoresistive fall-savers (Magnetoresistive
 * Boots + G-Force Cushion): instead of an instant zero-damage thud, the wearer
 * is <i>magnetically braked</i> as they approach the landing surface, slowing to
 * a gentle hover just above it before settling. Damage negation is still owned by
 * {@link FallSaveHandler} (boots) and {@link GForceCushionBlock#fallOn} (block);
 * this only shapes the descent and spawns spark particles for the brake effect.
 *
 * <p>Activates only in the last {@link #ACTIVATION_HEIGHT} blocks of a fast fall,
 * so ordinary movement and short hops are untouched. Boots brake above any solid
 * ground; the cushion brakes only a wearer of metallic armor (the conductor the
 * field grips) approaching the cushion block.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class DecelerateToHoverHandler {

    /** Only brake within this many blocks of the landing surface. */
    private static final int ACTIVATION_HEIGHT = 7;
    /** Within this many blocks of the surface the descent is held to a near-stop
     *  hover ({@link #HOVER_CREEP}) before settling. */
    private static final double HOVER_BAND = 1.6d;
    /** Near-stop descent speed in the hover band — reads as a brief hover, then
     *  drifts down onto the surface. */
    private static final double HOVER_CREEP = 0.05d;
    /** Cap on the braked descent speed far from the surface. */
    private static final double MAX_DESCENT = 0.7d;

    private DecelerateToHoverHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event) {
        final Player player = event.getEntity();
        if (player.getAbilities().flying || player.isFallFlying() || player.onGround()) return;

        final Vec3 vel = player.getDeltaMovement();
        // Engage for the WHOLE descent (not just fast falls) so the brake can
        // hold a hover instead of disengaging the moment it slows the player.
        if (vel.y >= 0.0) return;

        final Level level = player.level();
        final boolean bootsWorn =
                player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof MagnetoresistiveBootsItem;
        final boolean metallicArmor = hasMetallicArmor(player);

        // Scan straight down for the first landing surface within reach: a G-Force
        // Cushion (only grips metallic armor) or, with boots, any solid ground.
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
        if (Double.isNaN(surfaceTop)) return; // no eligible landing below

        final double dist = player.getY() - surfaceTop;
        if (dist <= 0.0) return; // already at/under the surface

        // In the hover band, hold a near-stop descent (the visible hover); above
        // it, ramp from that near-stop up to the max descent. Only ever slow the
        // player, never speed them up.
        final double allowed;
        if (dist <= HOVER_BAND) {
            allowed = -HOVER_CREEP;
        } else {
            final double f = Math.min(1.0, (dist - HOVER_BAND) / (ACTIVATION_HEIGHT - HOVER_BAND));
            allowed = -(HOVER_CREEP + f * (MAX_DESCENT - HOVER_CREEP));
        }
        if (vel.y < allowed) {
            player.setDeltaMovement(vel.x, allowed, vel.z);
            player.hasImpulse = true;
            player.hurtMarked = true;   // resync the braked velocity to the client
            // NB: we deliberately do NOT reset fallDistance — the landing event
            // still fires with the true height so FallSaveHandler (boots) negates
            // damage + charges durability and the cushion's fallOn negates damage.

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
    }

    private static boolean hasMetallicArmor(final Player player) {
        for (final ItemStack armor : player.getArmorSlots()) {
            if (armor.is(MagTags.METAL_ARMOR)) return true;
        }
        return false;
    }
}
