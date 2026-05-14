package com.stonytark.magnetization.content.item.magnetized;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagBlocks;
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
 * Axe block-yank: sneaking with a magnetized axe periodically rips one nearby
 * petrified-wood block out of the world as a drop. Petrified wood is intentionally
 * NOT in {@code FERROMAGNETIC_BLOCKS}, so the Magnetic Excavator and Magnetic
 * Pickaxe rip don't touch it — the magnetized axe is the only thing that yanks it.
 *
 * <p>Throttled at one block per {@link MagConfig#TOOL_PICKAXE_RIP_INTERVAL_TICKS} ticks
 * (shared with the pickaxe rip cadence to keep both abilities feeling equivalent),
 * and uses the axe's own radius {@link MagConfig#TOOL_PICKAXE_RIP_RADIUS} for the
 * same reason.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MagAxePetrifiedRip {

    private MagAxePetrifiedRip() {}

    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event) {
        if (!enabled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.isShiftKeyDown()) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        if ((level.getGameTime() % Math.max(1L, intervalTicks())) != 0L) return;

        final ItemStack axe = findMagnetizedAxe(player);
        if (axe == null) return;

        final BlockPos found = findPetrifiedNear(level, player.blockPosition(), radius());
        if (found == null) return;

        final BlockState state = level.getBlockState(found);
        Block.dropResources(state, level, found, level.getBlockEntity(found), player, axe);
        level.destroyBlock(found, false, player);
    }

    private static ItemStack findMagnetizedAxe(final ServerPlayer player) {
        for (final ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.isEmpty()) continue;
            if (!stack.is(ItemTags.AXES)) continue;
            if (!stack.is(MagTags.METAL_TOOLS)) continue;
            final MagneticPolarity pol = stack.get(MagDataComponents.ARMOR_POLARITY.get());
            if (pol == null || pol == MagneticPolarity.NONE) continue;
            return stack;
        }
        return null;
    }

    private static BlockPos findPetrifiedNear(final ServerLevel level, final BlockPos origin, final int radius) {
        BlockPos best = null;
        double bestDistSqr = Double.MAX_VALUE;
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    final BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) continue;
                    if (!state.is(MagBlocks.PETRIFIED_WOOD.get())) continue;
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

    // Reuse the pickaxe rip's tuning knobs — same throttle, same radius. If the user
    // wants axe-specific tuning later, split these into their own config keys.
    private static boolean enabled() {
        try { return MagConfig.TOOL_AXE_PULSE_ENABLED.get(); } catch (Throwable t) { return true; }
    }

    private static int radius() {
        try { return MagConfig.TOOL_PICKAXE_RIP_RADIUS.get(); } catch (Throwable t) { return 4; }
    }

    private static int intervalTicks() {
        try { return MagConfig.TOOL_PICKAXE_RIP_INTERVAL_TICKS.get(); } catch (Throwable t) { return 20; }
    }
}
