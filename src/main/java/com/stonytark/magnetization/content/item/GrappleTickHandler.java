package com.stonytark.magnetization.content.item;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.registry.MagItems;
import com.stonytark.magnetization.registry.MagParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Drives the sustained pull for {@link MagneticGrappleItem}. The grapple
 * registers a target on right-click and we re-apply velocity toward that
 * anchor each tick until the player gets close, time runs out, or sneak
 * cancels. Without this re-application, vanilla drag and gravity decelerate
 * the player after one tick and they never reach a far anchor.
 *
 * <p>The anchor is a {@link Supplier} of {@link Vec3} (not a static
 * {@link net.minecraft.core.BlockPos}) so it can resolve a moving target —
 * a Sable ship or a magnetized entity continues to update its position
 * between ticks and the grapple tracks it. If the supplier returns
 * {@code null} (e.g. entity died, ship unloaded), the pull ends.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class GrappleTickHandler {

    /** Max duration of a single pull. 50 ticks = 2.5s — covers the 24-block scan range with margin. */
    static final int MAX_PULL_TICKS = 50;
    /** Velocity per tick during the pull (blocks/tick). */
    static final double PULL_SPEED = 1.4d;
    /** Stop pulling once the player is within this distance — prevents overshoot. */
    static final double STOP_DISTANCE = 1.8d;
    /** Cooldown applied to the item once the pull ends, so spam-clicking can't chain. */
    static final int POST_PULL_COOLDOWN = 30;

    private static final Map<UUID, ActivePull> ACTIVE = new ConcurrentHashMap<>();

    private GrappleTickHandler() {}

    public static void start(final Player player, final Supplier<Vec3> anchor) {
        ACTIVE.put(player.getUUID(), new ActivePull(anchor, MAX_PULL_TICKS));
    }

    public static boolean isPulling(final Player player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event) {
        final Player player = event.getEntity();
        if (player.level().isClientSide) return;
        final ActivePull pull = ACTIVE.get(player.getUUID());
        if (pull == null) return;

        // Sneaking cancels — gives players an "out" mid-flight.
        if (player.isShiftKeyDown()) {
            end(player);
            return;
        }

        final Vec3 target = pull.anchor.get();
        // Target may have disappeared (ship unloaded, entity died) — end gracefully.
        if (target == null) {
            end(player);
            return;
        }

        final Vec3 to = target.subtract(player.position());
        final double dist = to.length();
        if (dist <= STOP_DISTANCE || pull.ticksLeft <= 0) {
            end(player);
            return;
        }

        final Vec3 vel = to.scale(PULL_SPEED / dist);
        player.setDeltaMovement(vel);
        player.hurtMarked = true;
        player.fallDistance = 0f;
        player.resetFallDistance();
        pull.ticksLeft--;

        // Visual grapple line: a taut chain of particles from the player to the
        // anchor each tick, so the pull visibly REACHES the thing being grappled
        // (emitter, ship or magnetized mob) instead of being an invisible yank.
        if (player.level() instanceof ServerLevel server) {
            drawGrappleLine(server, player.getEyePosition(), target);
        }
    }

    /** Particle line from {@code from} to {@code to}, ~one pip per block, drawn
     *  each pull tick so it tracks a moving anchor. */
    private static void drawGrappleLine(final ServerLevel level, final Vec3 from, final Vec3 to) {
        final Vec3 delta = to.subtract(from);
        final double len = delta.length();
        if (len < 0.1) return;
        final Vec3 dir = delta.scale(1.0 / len);
        final int steps = (int) Math.ceil(len);
        for (int i = 0; i <= steps; i++) {
            final double d = Math.min(i, len);
            final Vec3 p = from.add(dir.scale(d));
            level.sendParticles(MagParticles.MAG_NORTH.get(), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void end(final Player player) {
        ACTIVE.remove(player.getUUID());
        player.fallDistance = 0f;
        player.getCooldowns().addCooldown(MagItems.MAGNETIC_GRAPPLE.get(), POST_PULL_COOLDOWN);
    }

    /** Drop state for players that disconnect — guards against memory leak. */
    public static void clear(final UUID id) {
        ACTIVE.remove(id);
    }

    /** Sweep ACTIVE for stale entries. Called from… (intentionally not auto — server stop is enough). */
    static void clearAll() {
        for (final Iterator<Map.Entry<UUID, ActivePull>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            it.next();
            it.remove();
        }
    }

    private static final class ActivePull {
        final Supplier<Vec3> anchor;
        int ticksLeft;
        ActivePull(final Supplier<Vec3> anchor, final int ticksLeft) {
            this.anchor = anchor;
            this.ticksLeft = ticksLeft;
        }
    }
}
