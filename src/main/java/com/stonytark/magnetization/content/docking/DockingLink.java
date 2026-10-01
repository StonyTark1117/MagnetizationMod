package com.stonytark.magnetization.content.docking;

import com.stonytark.magnetization.content.anchor.MagneticAnchorBlockEntity;
import com.stonytark.magnetization.content.switchblock.MagneticSwitchBlockEntity;
import com.stonytark.magnetization.physics.SableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Empty-hand sneak-use selects an anchor, then links any number of nearby switches. */
public final class DockingLink {
    private static final String KEY = "MagnetizationDockSelection";
    private DockingLink() {}
    public static void select(ServerPlayer player, BlockPos anchor) {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putLong("Pos", anchor.asLong());
        tag.putString("Dimension", player.level().dimension().location().toString());
        player.getPersistentData().put(KEY, tag);
        player.displayClientMessage(Component.translatable("dock.magnetization.selected", anchor.toShortString()), true);
    }
    public static boolean link(ServerPlayer player, MagneticSwitchBlockEntity sw) {
        var tag = player.getPersistentData().getCompound(KEY);
        var level = player.serverLevel();
        if (!tag.contains("Pos") || !tag.getString("Dimension").equals(level.dimension().location().toString())) return fail(player);
        BlockPos pos = BlockPos.of(tag.getLong("Pos"));
        if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof MagneticAnchorBlockEntity anchor)) return fail(player);
        var host = SableBridge.subLevelOf(anchor);
        Vec3 anchorWorld = host == null ? pos.getCenter() : host.logicalPose().transformPosition(pos.getCenter());
        var switchHost = SableBridge.subLevelOf(sw);
        Vec3 switchWorld = switchHost == null ? sw.getBlockPos().getCenter() : switchHost.logicalPose().transformPosition(sw.getBlockPos().getCenter());
        if (anchorWorld.distanceToSqr(switchWorld) > 64 * 64) return fail(player);
        sw.linkAnchor(pos);
        player.displayClientMessage(Component.translatable("dock.magnetization.linked", pos.toShortString()), true);
        return true;
    }
    private static boolean fail(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("dock.magnetization.select_first"), true);
        return false;
    }
}
