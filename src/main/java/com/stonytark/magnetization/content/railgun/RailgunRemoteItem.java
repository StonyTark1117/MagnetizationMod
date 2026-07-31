package com.stonytark.magnetization.content.railgun;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Railgun remote trigger. Inserted into a Railgun emitter's GUI slot, it binds to
 * that emitter (and, via the {@link RailgunHandler}, its sibling rail) and puts the
 * arc in manual mode. Held and used in-hand anywhere, it fires a {@code HOLDING}
 * arc — letting a player trap a ship on the rail, board it, then launch.
 *
 * <p>Binding is deliberately verbose about failure: a use that can't reach its rail
 * says <i>why</i> (nothing bound / wrong dimension / chunk not loaded / railgun gone)
 * rather than silently passing the click through, and sneak-use always clears the
 * remote's own binding even when the rail is unreachable — otherwise a remote bound
 * to a since-demolished railgun in another dimension could never be un-stuck.
 */
public class RailgunRemoteItem extends Item {

    public RailgunRemoteItem(final Properties props) { super(props); }

    /**
     * Write the bound emitter's identity into the remote's CustomData: position,
     * dimension, and the descriptive bits the tooltip needs so it can name the rail
     * without the emitter being loaded — the rail's facing, its scanned length, and
     * any anvil-set custom label the remote was carrying when it was paired.
     */
    public static void bind(final ItemStack stack, final BlockPos pos, final ResourceKey<Level> dim,
                            final @Nullable Direction facing, final int railLength,
                            final @Nullable String label) {
        final CompoundTag tag = new CompoundTag();
        tag.putLong("BoundPos", pos.asLong());
        tag.putString("BoundDim", dim.location().toString());
        if (facing != null) tag.putString("BoundFacing", facing.getSerializedName());
        if (railLength > 0) tag.putInt("BoundLength", railLength);
        if (label != null && !label.isBlank()) tag.putString("BoundLabel", label);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** Bind from a live emitter, lifting facing/length/label off it. */
    public static void bind(final ItemStack stack, final RailgunEmitterBlockEntity be,
                            final ResourceKey<Level> dim) {
        final var state = be.getBlockState();
        final Direction facing = state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING) : null;
        // An anvil-renamed remote keeps its name as the rail's label, so a chest of
        // otherwise-identical remotes stays readable.
        final var custom = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        bind(stack, be.getBlockPos(), dim, facing, be.railLength(),
                custom == null ? null : custom.getString());
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

    /**
     * Move an existing binding without discarding its cached label/geometry.
     * Used by optional ship-transfer integrations after the emitter's Sable
     * plot is reconstructed in another dimension.
     *
     * @return {@code true} when this stack contained the expected old binding
     *         and was updated
     */
    public static boolean remapBinding(final ItemStack stack,
                                       final ResourceKey<Level> oldDim,
                                       final ResourceKey<Level> newDim,
                                       final java.util.function.UnaryOperator<BlockPos> positionMapper) {
        final CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        final BlockPos oldPos = boundPos(stack);
        final ResourceKey<Level> dim = boundDim(stack);
        if (data == null || oldPos == null || !oldDim.equals(dim)) return false;

        final BlockPos newPos = positionMapper.apply(oldPos);
        if (newPos == null || (newPos.equals(oldPos) && newDim.equals(oldDim))) return false;

        final CompoundTag tag = data.copyTag();
        tag.putLong("BoundPos", newPos.asLong());
        tag.putString("BoundDim", newDim.location().toString());
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return true;
    }

    private static @Nullable String boundLabel(final ItemStack stack) {
        final CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains("BoundLabel")) return null;
        final String s = data.copyTag().getString("BoundLabel");
        return s.isBlank() ? null : s;
    }

    private static @Nullable Direction boundFacing(final ItemStack stack) {
        final CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains("BoundFacing")) return null;
        return Direction.byName(data.copyTag().getString("BoundFacing"));
    }

    private static int boundLength(final ItemStack stack) {
        final CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains("BoundLength")) return 0;
        return data.copyTag().getInt("BoundLength");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(level instanceof ServerLevel server)) return InteractionResultHolder.pass(stack);

        final boolean unbinding = player.isShiftKeyDown();
        final BlockPos pos = boundPos(stack);
        final ResourceKey<Level> dim = boundDim(stack);

        // Sneak-use is an unconditional local un-pair: it always strips this remote's
        // binding, and additionally returns the arc to automatic when the rail happens
        // to be reachable. Without the unconditional half, a remote bound to a rail in
        // an unloaded chunk (or a deleted dimension) would be permanently stuck manual.
        if (unbinding) {
            if (pos == null || dim == null) {
                feedback(player, "item.magnetization.railgun_remote.not_bound", ChatFormatting.GRAY);
                return InteractionResultHolder.success(stack);
            }
            final RailgunEmitterBlockEntity be = reachableEmitter(server, dim, pos);
            clearBinding(stack);
            if (be != null) {
                be.unpair();
                feedback(player, "item.magnetization.railgun_remote.unbound", ChatFormatting.GRAY);
            } else {
                // The rail keeps whatever mode it had; say so instead of implying we
                // reset a railgun we never touched.
                feedback(player, "item.magnetization.railgun_remote.unbound_local", ChatFormatting.YELLOW);
            }
            return InteractionResultHolder.success(stack);
        }

        if (pos == null || dim == null) {
            feedback(player, "item.magnetization.railgun_remote.not_bound", ChatFormatting.RED);
            return InteractionResultHolder.success(stack);
        }
        if (!server.dimension().equals(dim)) {
            player.displayClientMessage(Component.translatable(
                            "item.magnetization.railgun_remote.wrong_dimension",
                            dim.location().toString(), server.dimension().location().toString())
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.success(stack);
        }
        if (!server.isLoaded(pos)) {
            player.displayClientMessage(Component.translatable(
                            "item.magnetization.railgun_remote.not_loaded",
                            pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.success(stack);
        }
        if (!(server.getBlockEntity(pos) instanceof RailgunEmitterBlockEntity be)) {
            player.displayClientMessage(Component.translatable(
                            "item.magnetization.railgun_remote.missing",
                            pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.success(stack);
        }

        be.requestFire();   // the handler launches a HOLDING arc on the next tick
        feedback(player, "item.magnetization.railgun_remote.fired", ChatFormatting.AQUA);
        return InteractionResultHolder.success(stack);
    }

    /** @return the bound emitter if it is in this level and its chunk is loaded, else {@code null}. */
    private static @Nullable RailgunEmitterBlockEntity reachableEmitter(
            final ServerLevel server, final ResourceKey<Level> dim, final BlockPos pos) {
        if (!server.dimension().equals(dim) || !server.isLoaded(pos)) return null;
        return server.getBlockEntity(pos) instanceof RailgunEmitterBlockEntity be ? be : null;
    }

    private static void feedback(final Player player, final String key, final ChatFormatting style) {
        player.displayClientMessage(Component.translatable(key).withStyle(style), true);
    }

    /** Strip the bound-emitter data so the remote reads as unpaired again. */
    public static void clearBinding(final ItemStack stack) {
        stack.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext ctx, final List<Component> tip,
                                final TooltipFlag flag) {
        final BlockPos pos = boundPos(stack);
        if (pos == null) {
            tip.add(Component.translatable("item.magnetization.railgun_remote.unpaired").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        // Rail identity first — the label a player gave it, or the generic block name.
        final String label = boundLabel(stack);
        tip.add(Component.translatable("item.magnetization.railgun_remote.rail",
                label != null ? Component.literal(label)
                        : Component.translatable("block.magnetization.railgun_emitter"))
                .withStyle(ChatFormatting.WHITE));

        tip.add(Component.translatable("item.magnetization.railgun_remote.bound",
                pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.AQUA));

        // Dimension, highlighted when the holder is standing somewhere else — the
        // single most common reason a use "does nothing".
        final ResourceKey<Level> dim = boundDim(stack);
        if (dim != null) {
            final ResourceKey<Level> here = ctx.level() == null ? null : ctx.level().dimension();
            final boolean elsewhere = here != null && !here.equals(dim);
            tip.add(Component.translatable("item.magnetization.railgun_remote.dimension",
                            dim.location().toString())
                    .withStyle(elsewhere ? ChatFormatting.RED : ChatFormatting.DARK_AQUA));
            if (elsewhere) {
                tip.add(Component.translatable("item.magnetization.railgun_remote.dimension_warning")
                        .withStyle(ChatFormatting.RED));
            }
        }

        final Direction facing = boundFacing(stack);
        final int length = boundLength(stack);
        if (facing != null || length > 0) {
            tip.add(Component.translatable("item.magnetization.railgun_remote.geometry",
                            length > 0 ? String.valueOf(length) : "?",
                            facing != null ? facing.getSerializedName() : "?")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        tip.add(Component.translatable("item.magnetization.railgun_remote.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
