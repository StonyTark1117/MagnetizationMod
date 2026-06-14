package com.stonytark.magnetization.mixin;

import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link ItemCombinerMenu}'s protected {@code access} so the anvil
 * dampener can resolve the anvil's world position from a player's open menu
 * (the {@code AnvilRepairEvent} doesn't carry the position).
 */
@Mixin(ItemCombinerMenu.class)
public interface ItemCombinerMenuAccessor {
    @Accessor("access")
    ContainerLevelAccess magnetization$access();
}
