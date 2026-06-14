package com.stonytark.magnetization.content;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Makes our interactible emitter blocks behave like normal blocks when you're
 * trying to build against them: right-clicking one while holding a block
 * <em>places</em> the block, and shift-right-click opens the GUI / flips
 * polarity. Players kept getting stuck because the block's own
 * {@code useItemOn}/{@code useWithoutItem} ate every right-click, so the only
 * way to place against a magnet was to sneak.
 *
 * <p>This has to be done from {@link PlayerInteractEvent.RightClickBlock} rather
 * than the block's interaction methods because vanilla skips block interaction
 * entirely when you sneak with an item in hand — so the block can't see the
 * "shift = interact" gesture. The event runs before that branch and lets us
 * route place-vs-interact explicitly via {@code setUseBlock}/{@code setUseItem}.
 *
 * <p>Only triggers while holding a {@link BlockItem}; empty hand and non-block
 * items (tools, our own emitter tools) fall through untouched, so they keep
 * interacting on right-click and the wrench / lens / imprint-module handlers
 * still run. Gated by {@link MagConfig#blockPlacementFirst()} (default on); off
 * restores the legacy click-to-interact / sneak-to-place behavior.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class EmitterPlacementHandler {

    private EmitterPlacementHandler() {}

    // LOW priority so the special emitter-tool handlers (wrench, hematite lens,
    // imprint module) get first crack and can cancel; we only act on a plain
    // block item, which none of them care about, but the guard is cheap.
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()) return;
        if (!MagConfig.blockPlacementFirst()) return; // legacy behavior — don't interfere

        final ItemStack held = event.getItemStack();
        if (held.isEmpty() || !(held.getItem() instanceof BlockItem)) return; // only block-in-hand

        final BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (!(be instanceof AbstractEmitterBlockEntity)) return; // only our interactible emitters

        final Player player = event.getEntity();
        if (player.isShiftKeyDown()) {
            // Shift + block → interact (open GUI / flip polarity), don't place.
            // setUseBlock(TRUE) forces the block interaction even though vanilla
            // would normally skip it while sneaking with an item.
            event.setUseBlock(TriState.TRUE);
            event.setUseItem(TriState.FALSE);
        } else {
            // Block + right-click → place the block, skip the block's GUI/flip.
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.TRUE);
        }
    }
}
