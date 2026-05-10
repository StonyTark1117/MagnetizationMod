package com.stonytark.magnetization.content.item;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives the sustained pull for {@link MagneticGrappleItem}. The grapple
 * registers a target on right-click and we re-apply velocity toward that
 * anchor each tick until the player gets close, time runs out, or sneak
 * cancels. Without this re-application, vanilla drag and gravity decelerate
 * the player after one tick and they never reach a far anchor.
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

    public static void start(final Player player, final BlockPos anchor) {
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

        final Vec3 to = Vec3.atCenterOf(pull.anchor).subtract(player.position());
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
        final BlockPos anchor;
        int ticksLeft;
        ActivePull(final BlockPos anchor, final int ticksLeft) {
            this.anchor = anchor;
            this.ticksLeft = ticksLeft;
        }
    }
}
