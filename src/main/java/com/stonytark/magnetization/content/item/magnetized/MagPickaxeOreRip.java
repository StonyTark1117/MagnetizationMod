package com.stonytark.magnetization.content.item.magnetized;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Pickaxe signature ability: a sneaking player holding a magnetized pickaxe periodically
 * rips a single ferromagnetic ore block out of the world as drops. The block is
 * destroyed in-place (no movement animation — fits the "magnet plucks it off the wall"
 * fantasy), and {@link com.stonytark.magnetization.content.item.MagneticToolPullHandler}
 * sweeps the dropped {@link net.minecraft.world.entity.item.ItemEntity} toward the holder.
 *
 * <p>Throttled at one block per {@link MagConfig#TOOL_PICKAXE_RIP_INTERVAL_TICKS} ticks
 * to prevent griefing — even a full magnetized loadout can only rip one block at a time
 * per pickaxe. Respects {@link MagTags#EXCAVATOR_IMMUNE} for the same "claim mod boundary"
 * reason the Magnetic Excavator does.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MagPickaxeOreRip {

    private MagPickaxeOreRip() {}

    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event) {
        if (!enabled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.isShiftKeyDown()) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        // Throttle: only fire on the configured cadence.
        if ((level.getGameTime() % Math.max(1L, intervalTicks())) != 0L) return;

        // Find a magnetized pickaxe in either hand.
        final ItemStack pickaxe = findMagnetizedPickaxe(player);
        if (pickaxe == null) return;

        final int radius = radius();
        final BlockPos found = findOreNear(level, player.blockPosition(), radius);
        if (found == null) return;

        final BlockState state = level.getBlockState(found);
        // Drop in-place with the pickaxe as the breaking tool — preserves Fortune /
        // Silk Touch on the pickaxe itself.
        Block.dropResources(state, level, found, level.getBlockEntity(found), player, pickaxe);
        level.destroyBlock(found, false, player);
    }

    private static ItemStack findMagnetizedPickaxe(final ServerPlayer player) {
        for (final ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.isEmpty()) continue;
            if (!stack.is(ItemTags.PICKAXES)) continue;
            if (!stack.is(MagTags.METAL_TOOLS)) continue;
            final MagneticPolarity pol = stack.get(MagDataComponents.ARMOR_POLARITY.get());
            if (pol == null || pol == MagneticPolarity.NONE) continue;
            return stack;
        }
        return null;
    }

    /** Scan the cuboid around the player for the closest ferromagnetic-block ore. Returns
     *  the first match in a deterministic order (square-shell-outward) so behavior is
     *  predictable when standing at a vein boundary. */
    private static BlockPos findOreNear(final ServerLevel level, final BlockPos origin, final int radius) {
        BlockPos best = null;
        double bestDistSqr = Double.MAX_VALUE;
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    final BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) continue;
                    if (!state.is(MagTags.FERROMAGNETIC_BLOCKS)) continue;
                    if (state.is(MagTags.EXCAVATOR_IMMUNE)) continue;
                    final double d2 = origin.distSqr(cursor);
                    if (d2 < bestDistSqr) {
                        bestDistSqr = d2;
                        best = cursor.immutable();
                    }
                }
            }
        }
        return best;
    }

    private static boolean enabled() {
        try { return MagConfig.TOOL_PICKAXE_ORE_RIP_ENABLED.get(); } catch (Throwable t) { return true; }
    }

    private static int radius() {
        try { return MagConfig.TOOL_PICKAXE_RIP_RADIUS.get(); } catch (Throwable t) { return 4; }
    }

    private static int intervalTicks() {
        try { return MagConfig.TOOL_PICKAXE_RIP_INTERVAL_TICKS.get(); } catch (Throwable t) { return 20; }
    }
}
