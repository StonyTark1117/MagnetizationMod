package com.stonytark.magnetization.content;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.mixin.ItemCombinerMenuAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;

/**
 * A magnetic block placed next to (or under) an anvil dampens it: the steady
 * field keeps the anvil from degrading. Handles the durability half via
 * {@link AnvilRepairEvent#setBreakChance}; the matching sound-deadening is done
 * client-side (see {@code AnvilSoundMixin}), sharing {@link #hasAdjacentDampener}.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class AnvilDampenerHandler {

    private AnvilDampenerHandler() {}

    @SubscribeEvent
    public static void onAnvilRepair(final AnvilRepairEvent event) {
        final Player player = event.getEntity();
        if (!(player.containerMenu instanceof AnvilMenu menu)) return;
        final ContainerLevelAccess access = ((ItemCombinerMenuAccessor) (Object) menu).magnetization$access();
        final boolean dampened = access.evaluate(AnvilDampenerHandler::hasAdjacentDampener).orElse(false);
        if (dampened) {
            event.setBreakChance(0.0f); // magnet steadies the anvil — never degrades
        }
    }

    /** True if any of the 6 blocks touching the anvil is an
     *  {@code #magnetization:anvil_dampeners} magnet. */
    public static boolean hasAdjacentDampener(final Level level, final BlockPos anvilPos) {
        for (final Direction dir : Direction.values()) {
            if (level.getBlockState(anvilPos.relative(dir)).is(MagTags.ANVIL_DAMPENERS)) {
                return true;
            }
        }
        return false;
    }
}
