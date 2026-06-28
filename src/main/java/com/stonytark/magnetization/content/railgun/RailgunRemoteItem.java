package com.stonytark.magnetization.content.railgun;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Railgun remote trigger. Inserted into a Railgun emitter's GUI slot, it binds to
 * that emitter (and, via the {@link RailgunHandler}, its sibling rail) and puts the
 * arc in manual mode. Held and used in-hand anywhere, it fires a {@code HOLDING}
 * arc — letting a player trap a ship on the rail, board it, then launch.
 */
public class RailgunRemoteItem extends Item {

    public RailgunRemoteItem(final Properties props) { super(props); }

    /** Write the bound emitter's pos + dimension into the remote's CustomData. */
    public static void bind(final ItemStack stack, final BlockPos pos, final ResourceKey<Level> dim) {
        final CompoundTag tag = new CompoundTag();
        tag.putLong("BoundPos", pos.asLong());
        tag.putString("BoundDim", dim.location().toString());
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static BlockPos boundPos(final ItemStack stack) {
        final CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains("BoundPos")) return null;
        return BlockPos.of(data.copyTag().getLong("BoundPos"));
    }

    public static ResourceKey<Level> boundDim(final ItemStack stack) {
        final CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains("BoundDim")) return null;
        final ResourceLocation loc = ResourceLocation.tryParse(data.copyTag().getString("BoundDim"));
        return loc == null ? null : ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, loc);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        final BlockPos pos = boundPos(stack);
        final ResourceKey<Level> dim = boundDim(stack);
        if (pos == null || dim == null || !(level instanceof ServerLevel server) || !server.dimension().equals(dim)) {
            return InteractionResultHolder.pass(stack);
        }
        if (server.getBlockEntity(pos) instanceof RailgunEmitterBlockEntity be) {
            be.requestFire();   // the handler launches a HOLDING arc on the next tick
            player.displayClientMessage(Component.translatable("item.magnetization.railgun_remote.fired")
                    .withStyle(ChatFormatting.AQUA), true);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext ctx, final List<Component> tip,
                                final TooltipFlag flag) {
        final BlockPos pos = boundPos(stack);
        if (pos == null) {
            tip.add(Component.translatable("item.magnetization.railgun_remote.unpaired").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tip.add(Component.translatable("item.magnetization.railgun_remote.bound",
                    pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.AQUA));
        }
    }
}
