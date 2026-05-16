package com.stonytark.magnetization.content.item;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.physics.SableBridge;
import com.stonytark.magnetization.registry.MagItems;
import com.stonytark.magnetization.registry.MagParticles;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Handheld repulsor — opposite-of-grapple. Right-click fires a short-range
 * conical repulsive field anchored at the player along their look direction.
 *
 * <ul>
 *   <li><b>Ships</b> in the cone get pushed away (single impulse via
 *       {@link FieldApplicator} — no sustained drain).</li>
 *   <li><b>Magnetized entities</b> (anything with the Magnetized effect or
 *       armor carrying a polarity stamp) in the cone get a knockback impulse.
 *       Crowd control.</li>
 *   <li><b>Dropped items and falling blocks</b> in the cone get nudged away
 *       too — defensive use against thieves, or a way to clear an area of
 *       loose drops. Placed blocks and entities not in our magnet
 *       predicates are unaffected.</li>
 *   <li><b>Self-recoil</b>: a ray-trace from the player's eyes; if the first
 *       block hit is in {@code #magnetization:magnetic_emitter}, the player is
 *       launched away from it in the opposite direction. Aim down at a
 *       lodestone in your path → backflip away from it. Aim at a magnetic
 *       anchor from across the room → meaningful traversal distance.</li>
 * </ul>
 *
 * <p>Cooldown-gated like the Magnetic Grapple — no FE/RF, no charge component.
 */
public class RepulsorGunItem extends Item {

    public RepulsorGunItem(final Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);

        fire(level, player);

        // Cooldown applied at fire-time (not pull-end, since this is a single pulse).
        player.getCooldowns().addCooldown(this, cooldownTicks());
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.6f, 1.8f);
        return InteractionResultHolder.success(stack);
    }

    /** Public so the curios-keybind handler can invoke a shot when the item
     *  lives in a charm slot and there's no hand to right-click from. */
    public void fire(final Level level, final Player player) {
        if (level.isClientSide || !(level instanceof ServerLevel server)) return;

        final Vec3 origin = player.getEyePosition();
        final Vec3 look = player.getLookAngle().normalize();
        final double range = repulsorGunRange();
        final MagneticStrength strength = MagneticStrength.MEDIUM;

        // Build a transient conical repulsive field and let FieldApplicator do
        // the ship + entity push, same code path the Repulsor Coil block uses.
        // NORTH polarity = repulsive on our convention (default-target = NORTH,
        // like polarities repel).
        final MagneticField field = new MagneticField(
                origin, look, MagneticPolarity.NORTH, strength,
                MagneticField.Shape.CONICAL, range);
        FieldApplicator.apply(server, field);

        // Push dropped items + falling blocks separately — FieldApplicator's
        // entity path covers entities with magnetic predicates, but plain
        // ItemEntity / FallingBlockEntity aren't in those tags. Iterate the
        // cone AABB and push anything dropped/loose along the look direction
        // with a soft falloff.
        pushLooseObjects(server, origin, look, range);

        // Self-recoil: if the player is aiming at a magnetic emitter block
        // within scan range, push the player back along the inverse of their
        // look. The "opposite of the grapple" headline use case.
        applySelfRecoil(server, player, origin, look);

        // Particle plume along the cone for visual feedback.
        spawnConeParticles(server, origin, look, range);
    }

    private static void pushLooseObjects(final ServerLevel level, final Vec3 origin,
                                         final Vec3 look, final double range) {
        final double pushStrength = 0.6;
        final double cosHalfAngle = repulsorGunConicalHalfAngleCos();
        final net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                origin.x - range, origin.y - range, origin.z - range,
                origin.x + range, origin.y + range, origin.z + range);
        for (final var entity : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box,
                e -> e instanceof net.minecraft.world.entity.item.ItemEntity
                  || e instanceof net.minecraft.world.entity.item.FallingBlockEntity)) {
            final Vec3 toEntity = entity.position().subtract(origin);
            final double dist = toEntity.length();
            if (dist < 0.1 || dist > range) continue;
            final Vec3 dir = toEntity.normalize();
            if (dir.dot(look) < cosHalfAngle) continue; // outside the cone
            final double falloff = 1.0 - (dist / range);
            final Vec3 nudge = dir.scale(pushStrength * falloff);
            entity.setDeltaMovement(entity.getDeltaMovement().add(nudge));
            entity.hasImpulse = true;
        }
    }

    private static void applySelfRecoil(final ServerLevel level, final Player player,
                                         final Vec3 origin, final Vec3 look) {
        final double scanDist = repulsorGunRange();
        final Vec3 end = origin.add(look.scale(scanDist));
        final BlockHitResult hit = level.clip(new ClipContext(
                origin, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return;
        final BlockPos hitPos = hit.getBlockPos();
        final BlockState state = level.getBlockState(hitPos);
        if (!state.is(MagTags.MAGNETIC_EMITTER_BLOCKS)) return;
        // Closer magnet = harder kickback. Linear falloff from full strength at
        // 1 block to zero at scan range — makes point-blank shots dramatic and
        // long shots negligible. Velocity injection is server-authoritative;
        // hurtMarked + connection.send is what NeoForge's player-move sync
        // expects when a server forces a player velocity change.
        final double dist = origin.distanceTo(Vec3.atCenterOf(hitPos));
        final double fallOff = Math.max(0.0, 1.0 - dist / scanDist);
        final Vec3 recoil = look.scale(-repulsorGunSelfRecoilStrength() * fallOff);
        player.push(recoil.x, recoil.y, recoil.z);
        player.hurtMarked = true;
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            // Send velocity packet so the client sees the kickback immediately
            // rather than next tick (which is jittery at long range).
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
        }
    }

    private static void spawnConeParticles(final ServerLevel level, final Vec3 origin,
                                            final Vec3 look, final double range) {
        // Sample a handful of points along the cone axis and at the rim. A
        // dozen-or-so particles is enough to read as a directional blast
        // without flooding the screen.
        for (int i = 1; i <= 6; i++) {
            final double t = (i / 6.0) * range;
            final Vec3 p = origin.add(look.scale(t));
            level.sendParticles(MagParticles.MAG_SOUTH.get(), p.x, p.y, p.z, 3, 0.15, 0.15, 0.15, 0.0);
        }
    }

    private static double repulsorGunRange() {
        try { return MagConfig.REPULSOR_GUN_RANGE.get(); } catch (Throwable t) { return 12.0d; }
    }

    private static double repulsorGunConicalHalfAngleCos() {
        try { return MagConfig.REPULSOR_GUN_CONICAL_HALF_ANGLE_COS.get(); }
        catch (Throwable t) { return 0.866d; }
    }

    private static double repulsorGunSelfRecoilStrength() {
        try { return MagConfig.REPULSOR_GUN_SELF_RECOIL_STRENGTH.get(); }
        catch (Throwable t) { return 0.8d; }
    }

    private static int cooldownTicks() {
        try { return MagConfig.REPULSOR_GUN_COOLDOWN_TICKS.get(); } catch (Throwable t) { return 20; }
    }
}
