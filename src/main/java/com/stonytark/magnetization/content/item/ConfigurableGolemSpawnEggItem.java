package com.stonytark.magnetization.content.item;

import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Creative spawn egg that honours the same live config gate as its golem. */
public final class ConfigurableGolemSpawnEggItem extends DeferredSpawnEggItem {
    private final BooleanSupplier enabled;

    public ConfigurableGolemSpawnEggItem(final Supplier<? extends EntityType<? extends Mob>> type,
                                         final int backgroundColor, final int highlightColor,
                                         final BooleanSupplier enabled, final Properties properties) {
        super(type, backgroundColor, highlightColor, properties);
        this.enabled = enabled;
    }

    public boolean isGolemEnabled() {
        return enabled.getAsBoolean();
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        if (isGolemEnabled()) return super.useOn(context);
        notifyDisabled(context.getPlayer(), context.getLevel());
        return InteractionResult.FAIL;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player,
                                                   final InteractionHand hand) {
        if (isGolemEnabled()) return super.use(level, player, hand);
        notifyDisabled(player, level);
        return InteractionResultHolder.fail(player.getItemInHand(hand));
    }

    @Override
    public Optional<Mob> spawnOffspringFromSpawnEgg(final Player player, final Mob parent,
                                                     final EntityType<? extends Mob> type,
                                                     final ServerLevel level, final Vec3 position,
                                                     final ItemStack stack) {
        return isGolemEnabled()
                ? super.spawnOffspringFromSpawnEgg(player, parent, type, level, position, stack)
                : Optional.empty();
    }

    @Override
    protected DispenseItemBehavior createDispenseBehavior() {
        final DispenseItemBehavior delegate = super.createDispenseBehavior();
        return delegate == null ? null
                : (source, stack) -> isGolemEnabled() ? delegate.dispense(source, stack) : stack;
    }

    private static void notifyDisabled(final Player player, final Level level) {
        if (player != null && !level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.magnetization.golem_disabled"), true);
        }
    }
}
