package com.stonytark.magnetization.content.item.magnetized;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hoe signature ability: sneak + right-click with a magnetized hoe runs a one-shot
 * dowsing ping. Every {@code #magnetization:ferromagnetic_blocks} block within the
 * configured radius gets marked with a brief particle column so the player can spot
 * ore veins through the wall. Effectively a magnetic metal detector.
 *
 * <p>Sneak gate keeps the normal till-grass right-click intact: plain right-click =
 * vanilla hoe tilling, sneak right-click = dowsing.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MagHoeDowse {

    /** Per-player last-fire tick — limits ping spam without a real cooldown component. */
    private static final ConcurrentHashMap<UUID, Long> LAST_FIRE = new ConcurrentHashMap<>();
    /** Particle count emitted per detected block per "burst" along the column. */
    private static final int PARTICLES_PER_COLUMN = 6;
    /** Hard cap on detected blocks per ping — protects against pinging in a magnetite biome. */
    private static final int MAX_HITS = 64;

    private MagHoeDowse() {}

    @SubscribeEvent
    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        if (tryDowse(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(final PlayerInteractEvent.RightClickItem event) {
        if (tryDowse(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
        }
    }

    /** @return {@code true} if a dowse fired (the caller should cancel the event so vanilla hoe behavior doesn't also fire). */
    private static boolean tryDowse(final Player player, final ItemStack stack) {
        if (!enabled()) return false;
        if (!player.isShiftKeyDown()) return false;
        if (stack.isEmpty() || !stack.is(ItemTags.HOES) || !stack.is(MagTags.METAL_TOOLS)) return false;
        final MagneticPolarity pol = stack.get(MagDataComponents.ARMOR_POLARITY.get());
        if (pol == null || pol == MagneticPolarity.NONE) return false;
        if (!(player instanceof ServerPlayer server)) return true; // client-side fires too; cancel + wait for server
        if (!(player.level() instanceof ServerLevel level)) return false;

        // Per-player cooldown.
        final long now = level.getGameTime();
        final Long last = LAST_FIRE.get(server.getUUID());
        final int cooldown = cooldownTicks();
        if (last != null && now - last < cooldown) return true; // swallow click but no ping
        LAST_FIRE.put(server.getUUID(), now);

        final int radius = radius();
        final BlockPos origin = player.blockPosition();
        int hits = 0;
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius && hits < MAX_HITS; dx++) {
            for (int dy = -radius; dy <= radius && hits < MAX_HITS; dy++) {
                for (int dz = -radius; dz <= radius && hits < MAX_HITS; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    final BlockState s = level.getBlockState(cursor);
                    if (!s.is(MagTags.FERROMAGNETIC_BLOCKS)) continue;
                    pingBlock(level, cursor, server);
                    hits++;
                }
            }
        }

        // Chat feedback: how many veins did we find?
        final ChatFormatting color = hits > 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY;
        server.displayClientMessage(
                Component.translatable("hoe.magnetization.dowse_result", hits).withStyle(color),
                true);
        level.playSound(null, origin, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                0.5f, hits > 0 ? 1.4f : 0.8f);
        return true;
    }

    private static void pingBlock(final ServerLevel level, final BlockPos pos, final ServerPlayer player) {
        // Brief column of north-pole (red) particles from the block face up to ~3 blocks
        // above — visible through nearby walls because particles aren't occluded by blocks.
        final double cx = pos.getX() + 0.5d;
        final double cz = pos.getZ() + 0.5d;
        for (int i = 0; i < PARTICLES_PER_COLUMN; i++) {
            final double y = pos.getY() + 0.5d + (i * 0.5d);
            level.sendParticles(player,
                    net.minecraft.core.particles.ParticleTypes.GLOW,
                    true,
                    cx, y, cz,
                    1, 0.05d, 0.05d, 0.05d, 0.0d);
        }
        // One puff of the mod's north-pole particle right at the block — colors it
        // unambiguously as a magnetism ping.
        level.sendParticles(player,
                MagParticles.MAG_NORTH.get(),
                true,
                cx, pos.getY() + 0.5d, cz,
                3, 0.2d, 0.2d, 0.2d, 0.02d);
    }

    private static boolean enabled() {
        try { return MagConfig.TOOL_HOE_DOWSE_ENABLED.get(); } catch (Throwable t) { return true; }
    }

    private static int radius() {
        try { return MagConfig.TOOL_HOE_DOWSE_RADIUS.get(); } catch (Throwable t) { return 8; }
    }

    private static int cooldownTicks() {
        try { return MagConfig.TOOL_HOE_DOWSE_COOLDOWN_TICKS.get(); } catch (Throwable t) { return 60; }
    }
}
