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
        access.evaluate((level, pos) -> {
            // Magnetic-metal anvils carry their own per-metal break chance.
            final Float metalChance = magneticAnvilBreakChance(level.getBlockState(pos));
            if (metalChance != null) event.setBreakChance(metalChance);
            // An adjacent dampener magnet steadies any anvil — never degrades (wins).
            if (hasAdjacentDampener(level, pos)) event.setBreakChance(0.0f);
            return null;
        });
    }

    /** Public read-only view of a magnetic anvil's per-metal break chance (or null
     *  if {@code state} isn't one of our anvils) — for HUD/WTHIT display. */
    public static Float breakChanceFor(final net.minecraft.world.level.block.state.BlockState state) {
        return magneticAnvilBreakChance(state);
    }

    /** Per-metal break chance for our anvils, or null if {@code state} isn't one.
     *  Most are weaker than a vanilla anvil + dampener (which is 0); titanomagnetite
     *  is the only one that beats it (0, never degrades). */
    private static Float magneticAnvilBreakChance(final net.minecraft.world.level.block.state.BlockState state) {
        if (!state.is(MagTags.DAMPENED_ANVILS)) return null;
        final String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return switch (path) {
            case "magnetite_anvil" -> com.stonytark.magnetization.config.MagConfig.anvilBreakMagnetite();
            case "maghemite_anvil" -> com.stonytark.magnetization.config.MagConfig.anvilBreakMaghemite();
            case "hematite_anvil" -> com.stonytark.magnetization.config.MagConfig.anvilBreakHematite();
            case "titanomagnetite_anvil" -> com.stonytark.magnetization.config.MagConfig.anvilBreakTitanomagnetite();
            default -> com.stonytark.magnetization.config.MagConfig.anvilBreakDefault();
        };
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
