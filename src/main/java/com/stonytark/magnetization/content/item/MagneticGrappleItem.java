package com.stonytark.magnetization.content.item;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticFieldSource;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.physics.EmitterRegistry;
import com.stonytark.magnetization.physics.MagneticFields;
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
import java.util.HashSet;
import java.util.Set;

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
        return tryActivate(level, player, stack)
                ? InteractionResultHolder.success(stack)
                : InteractionResultHolder.fail(stack);
    }

    /** Shared server-side activation for both the in-hand right-click and the
     *  Curios keybind. Takes the REAL source stack so the FIRED_AT visual stamp
     *  lands on the item that actually fired — the held stack for hand use, the
     *  Curios stack for the keybind (was: the keybind stamped the unrelated
     *  main-hand stack, so the charm never glowed). Returns false if it couldn't
     *  fire (on cooldown, already pulling, or no anchor in range).
     *
     * @param sourceStack the grapple that fired, to stamp FIRED_AT on. */
    public boolean tryActivate(final Level level, final Player player, final ItemStack sourceStack) {
        if (level.isClientSide) return false;
        if (com.stonytark.magnetization.config.MagConfig.isItemDisabled(sourceStack)) return false;
        if (player.getCooldowns().isOnCooldown(this)) return false;
        if (GrappleTickHandler.isPulling(player)) return false;

        final Supplier<Vec3> anchor = findAnyAnchor(level, player);
        if (anchor == null) {
            player.displayClientMessage(
                    Component.translatable("grapple.magnetization.no_anchor").withStyle(ChatFormatting.GRAY),
                    true);
            return false;
        }

        GrappleTickHandler.start(player, anchor);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LODESTONE_HIT, SoundSource.PLAYERS, 0.6f, 1.4f);

        // Stamp fire time so the client swaps in the glowing-prong model variant
        // for a few ticks — the nozzle/claw lights up as the hook launches.
        sourceStack.set(MagDataComponents.FIRED_AT.get(), level.getGameTime());
        return true;
    }

    /** Pick the closest valid target across all three categories. */
    private static @Nullable Supplier<Vec3> findAnyAnchor(final Level level, final Player player) {
        final Vec3 from = player.position();
        double bestDistSqr = SCAN_RADIUS * SCAN_RADIUS;
        Supplier<Vec3> best = null;

        // 1. Attractive emitters (static position).
        final Set<net.minecraft.core.BlockPos> emitterPositions;
        if (level instanceof ServerLevel server) {
            final net.minecraft.core.BlockPos target = net.minecraft.core.BlockPos.containing(from);
            emitterPositions = new HashSet<>(EmitterRegistry.snapshotNativeNear(
                    server, target, (int) Math.ceil(SCAN_RADIUS)));
            emitterPositions.addAll(EmitterRegistry.snapshotExternalNear(
                    server, target, (int) Math.ceil(SCAN_RADIUS), Integer.MAX_VALUE));
        } else {
            emitterPositions = EmitterRegistry.snapshot(level);
        }
        for (var pos : emitterPositions) {
            final double d2 = pos.getCenter().distanceToSqr(from);
            if (d2 >= bestDistSqr) continue;
            final MagneticField field;
            if (level instanceof ServerLevel server) {
                field = MagneticFields.fieldAtLoaded(server, pos);
            } else {
                final BlockEntity be = level.getBlockEntity(pos);
                field = be instanceof MagneticFieldSource source ? source.currentField() : null;
            }
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
