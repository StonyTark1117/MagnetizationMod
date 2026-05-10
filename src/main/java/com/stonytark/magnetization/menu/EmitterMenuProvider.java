package com.stonytark.magnetization.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;

/**
 * Helper for opening the {@link EmitterMenu} on the server. Sends the open
 * payload so the client knows the BE position and capability bitmap.
 */
public final class EmitterMenuProvider implements MenuProvider {

    private final BlockPos pos;
    private final int caps;
    private final Component title;
    private final ContainerLevelAccess access;

    public EmitterMenuProvider(final ContainerLevelAccess access, final BlockPos pos,
                               final int caps, final Component title) {
        this.access = access;
        this.pos = pos;
        this.caps = caps;
        this.title = title;
    }

    @Override
    public Component getDisplayName() {
        return title;
    }

    @Override
    public AbstractContainerMenu createMenu(final int id, final Inventory inv, final Player player) {
        return new EmitterMenu(id, inv, access, pos, caps);
    }

    /** Send the network payload alongside the open packet so {@code fromNetwork}
     *  reconstructs the same caps bitmap on the client. */
    public EmitterMenu.OpenPayload payload() {
        return new EmitterMenu.OpenPayload(pos, caps);
    }

    /** Drives {@link ServerPlayer#openMenu} with the payload. Returns true on success. */
    public boolean openFor(final ServerPlayer player) {
        player.openMenu(this, buf -> EmitterMenu.OpenPayload.STREAM_CODEC.encode(buf, payload()));
        return true;
    }
}
