package com.stonytark.magnetization.menu;

import com.stonytark.magnetization.content.motor.HomopolarMotorBlockEntity;
import com.stonytark.magnetization.registry.MagMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * One-slot menu for the Homopolar Motor: a single magnet slot plus the player
 * inventory. The magnet slot is bound to the BE's container on the server and a
 * synced placeholder on the client.
 */
public final class HomopolarMotorMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    private final BlockPos pos;
    private final Container magnet;

    public HomopolarMotorMenu(final int id, final Inventory inv, final ContainerLevelAccess access,
                              final BlockPos pos, final Container magnet) {
        super(MagMenus.HOMOPOLAR_MOTOR.get(), id);
        this.access = access;
        this.pos = pos;
        this.magnet = magnet;
        checkContainerSize(magnet, 1);

        addSlot(new Slot(magnet, 0, 80, 35) {
            @Override public boolean mayPlace(final ItemStack stack) { return HomopolarMotorBlockEntity.isMagnet(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 142));
        }
    }

    public static HomopolarMotorMenu fromNetwork(final int id, final Inventory inv, final RegistryFriendlyByteBuf buf) {
        return new HomopolarMotorMenu(id, inv, ContainerLevelAccess.NULL, buf.readBlockPos(), new SimpleContainer(1));
    }

    public BlockPos pos() { return pos; }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        final Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        final ItemStack original = slot.getItem();
        final ItemStack copy = original.copy();
        if (index == 0) {
            // magnet slot → player inventory
            if (!moveItemStackTo(original, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else if (HomopolarMotorBlockEntity.isMagnet(original)) {
            // player inventory → magnet slot
            if (!moveItemStackTo(original, 0, 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (original.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(final Player player) {
        return access.evaluate((level, p) ->
                level.getBlockEntity(p) instanceof HomopolarMotorBlockEntity
                        && player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= 64.0,
                true);
    }
}
