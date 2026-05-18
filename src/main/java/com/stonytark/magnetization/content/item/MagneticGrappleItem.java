package com.stonytark.magnetization.content.item;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticFieldSource;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.physics.EmitterRegistry;
import com.stonytark.magnetization.physics.ShipMagneticRegistry;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagEffects;
import com.stonytark.magnetization.api.EquippedArmor;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Right-click to scan for the nearest grappleable target within 24 blocks and
 * yank the player toward it. Just-Cause-style traversal that turns magnetic
 * infrastructure (and ships, and magnetized mobs) into a movement system.
 *
 * <p>The actual pull is sustained per-tick by {@link GrappleTickHandler} —
 * vanilla drag/gravity decelerate the player too quickly for a one-shot
 * impulse to traverse the full scan range, so we re-apply velocity each tick
 * until the player nears the anchor or the duration cap fires. Cooldown is
 * applied at pull-end, not click time.
 *
 * <p>Three target types qualify (closest wins):
 * <ul>
 *   <li><b>Attractive emitters</b> — SOUTH-polarity emitters in
 *       {@link EmitterRegistry}. Static position. Polarity gate prevents
 *       players from grappling into a repulsor that would just shove them
 *       back.</li>
 *   <li><b>Sable sub-levels (ships)</b> — any ship with non-zero
 *       susceptibility. Mobile target: the supplier resolves the ship's
 *       current logical-pose center each tick, so a moving ship still tracks
 *       correctly.</li>
 *   <li><b>Magnetized entities</b> — any {@link LivingEntity} wearing
 *       magnetized armor or carrying the Magnetized status effect. Mobile
 *       target: the supplier returns the entity's current position each tick;
 *       returns null when the entity dies (ending the pull cleanly).</li>
 * </ul>
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

        final Supplier<Vec3> anchor = findAnyAnchor(level, player);
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

    /** Pick the closest valid target across all three categories. */
    private static @Nullable Supplier<Vec3> findAnyAnchor(final Level level, final Player player) {
        final Vec3 from = player.position();
        double bestDistSqr = SCAN_RADIUS * SCAN_RADIUS;
        Supplier<Vec3> best = null;

        // 1. Attractive emitters (static position).
        for (var pos : EmitterRegistry.snapshot(level)) {
            final double d2 = pos.getCenter().distanceToSqr(from);
            if (d2 >= bestDistSqr) continue;
            final BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof MagneticFieldSource source)) continue;
            final MagneticField field = source.currentField();
            if (field == null) continue;
            if (field.polarity() != MagneticPolarity.SOUTH) continue;
            final Vec3 center = pos.getCenter();
            best = () -> center;
            bestDistSqr = d2;
        }

        // 2. Sable sub-levels with non-zero susceptibility (mobile target).
        if (level instanceof ServerLevel server) {
            final SubLevelContainer container = SubLevelContainer.getContainer(server);
            if (container != null) {
                final BoundingBox3d searchBox = new BoundingBox3d(
                        from.x - SCAN_RADIUS, from.y - SCAN_RADIUS, from.z - SCAN_RADIUS,
                        from.x + SCAN_RADIUS, from.y + SCAN_RADIUS, from.z + SCAN_RADIUS);
                for (SubLevel sub : container.queryIntersecting(searchBox)) {
                    if (!(sub instanceof ServerSubLevel ship)) continue;
                    if (ship.getMassTracker().isInvalid() || ship.getMassTracker().getMass() <= 0) continue;
                    // Require some susceptibility — a pure-stone ship isn't a
                    // magnetic target. ShipMagneticRegistry returns DEFAULT
                    // (susceptibility = 1.0) for unknown ships, so this is
                    // effectively a "non-broken ship" gate today.
                    final var state = ShipMagneticRegistry.get(server, ship);
                    if (state.susceptibility() <= 0) continue;
                    final BoundingBox3dc box = sub.boundingBox();
                    final Vec3 shipCenter = new Vec3(
                            (box.minX() + box.maxX()) * 0.5,
                            (box.minY() + box.maxY()) * 0.5,
                            (box.minZ() + box.maxZ()) * 0.5);
                    final double d2 = shipCenter.distanceToSqr(from);
                    if (d2 >= bestDistSqr) continue;
                    final ServerSubLevel target = ship;
                    best = () -> {
                        if (target.getMassTracker().isInvalid()) return null;
                        final BoundingBox3dc b = target.boundingBox();
                        return new Vec3(
                                (b.minX() + b.maxX()) * 0.5,
                                (b.minY() + b.maxY()) * 0.5,
                                (b.minZ() + b.maxZ()) * 0.5);
                    };
                    bestDistSqr = d2;
                }
            }
        }

        // 3. Magnetized living entities (mobile target).
        final AABB box = AABB.ofSize(from, 2 * SCAN_RADIUS, 2 * SCAN_RADIUS, 2 * SCAN_RADIUS);
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && isMagnetized(e))) {
            final double d2 = le.position().distanceToSqr(from);
            if (d2 >= bestDistSqr) continue;
            final LivingEntity target = le;
            best = () -> target.isAlive()
                    ? target.position().add(0, target.getBbHeight() * 0.5, 0)
                    : null;
            bestDistSqr = d2;
        }

        return best;
    }

    /** An entity is magnetized if any armor piece carries an
     *  {@link MagDataComponents#ARMOR_POLARITY} stamp, or the entity has the
     *  {@link MagEffects#MAGNETIZED} effect. */
    private static boolean isMagnetized(final LivingEntity e) {
        if (e.hasEffect(MagEffects.MAGNETIZED)) return true;
        for (final ItemStack armor : EquippedArmor.all(e)) {
            if (armor.has(MagDataComponents.ARMOR_POLARITY.get())) return true;
        }
        return false;
    }
}
