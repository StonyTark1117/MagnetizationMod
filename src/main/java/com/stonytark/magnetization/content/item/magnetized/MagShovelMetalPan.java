package com.stonytark.magnetization.content.item.magnetized;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Shovel signature ability: digging loose earth (dirt / sand / gravel / clay / soil)
 * with a magnetized shovel has a small chance to drop trace ferrous material — but the
 * drop is sampled from <i>any</i> metal known to the game, not just iron.
 *
 * <h2>Drop pool</h2>
 * Built fresh on each trigger from the {@code c:nuggets/*} tag tree:
 * <ul>
 *   <li>Every {@code c:nuggets/<metal>} sub-tag contributes its items at weight 10.</li>
 *   <li>Every {@code c:ingots/<metal>} <i>without</i> a corresponding nugget tag
 *       contributes its ingots at weight 1 — these fall through as "metal scrap" so
 *       mods like vanilla copper (no copper_nugget) still produce something pannable.</li>
 * </ul>
 *
 * <p>This avoids hard-coding any specific item: any mod that registers
 * {@code c:nuggets/<metal>} (the common-tag convention) automatically becomes a
 * possible shovel drop.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MagShovelMetalPan {

    /** Common-tag root for nuggets ({@code c:nuggets}). Individual sub-tags hang off it. */
    private static final TagKey<Item> C_NUGGETS_ROOT = TagKey.create(
            net.minecraft.core.registries.Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "nuggets"));
    /** Common-tag root for ingots ({@code c:ingots}). */
    private static final TagKey<Item> C_INGOTS_ROOT = TagKey.create(
            net.minecraft.core.registries.Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "ingots"));

    private MagShovelMetalPan() {}

    @SubscribeEvent
    public static void onBlockBreak(final BlockEvent.BreakEvent event) {
        if (!enabled()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!isShovelTarget(event.getState())) return;

        final ItemStack shovel = player.getMainHandItem();
        if (!shovel.is(ItemTags.SHOVELS) || !shovel.is(MagTags.METAL_TOOLS)) return;
        final MagneticPolarity pol = shovel.get(MagDataComponents.ARMOR_POLARITY.get());
        if (pol == null || pol == MagneticPolarity.NONE) return;

        if (level.random.nextDouble() >= chance()) return;

        final ItemStack drop = rollDrop(level);
        if (drop == null || drop.isEmpty()) return;

        final BlockPos pos = event.getPos();
        final ItemEntity item = new ItemEntity(level,
                pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d, drop);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    /**
     * Build a weighted pool of (item, weight) entries from c:nuggets/* and c:ingots/*,
     * then pick one. Returns {@code null} if nothing is in the registry — extremely
     * unlikely with vanilla loaded (iron + gold nuggets), but defensive.
     */
    private static ItemStack rollDrop(final ServerLevel level) {
        final List<Entry> pool = new ArrayList<>();
        // Track which metal-name buckets already contributed a nugget so we don't
        // double-count by adding the ingot fallback when a nugget already exists.
        final java.util.Set<String> metalsWithNugget = new java.util.HashSet<>();

        // Pass 1: every direct child tag of c:nuggets contributes its items at weight 10.
        forEachChildTag(C_NUGGETS_ROOT, (childPath, items) -> {
            for (final Item it : items) pool.add(new Entry(it, 10));
            metalsWithNugget.add(childPath);
        });
        // Pass 2: every c:ingots/<metal> without a c:nuggets/<metal> sibling adds its
        // ingot items at weight 1. Lets vanilla copper (no nugget) still produce scrap.
        forEachChildTag(C_INGOTS_ROOT, (childPath, items) -> {
            if (metalsWithNugget.contains(childPath)) return;
            for (final Item it : items) pool.add(new Entry(it, 1));
        });

        if (pool.isEmpty()) return null;

        int total = 0;
        for (final Entry e : pool) total += e.weight;
        int roll = level.random.nextInt(total);
        for (final Entry e : pool) {
            roll -= e.weight;
            if (roll < 0) {
                // Vary the count a bit so the drop feels organic: 1–2 for nuggets, 1 for ingots.
                final int count = (e.weight >= 10) ? (1 + level.random.nextInt(2)) : 1;
                return new ItemStack(e.item, count);
            }
        }
        return null;
    }

    /**
     * Walk the immediate sub-tags of a common-tag root (e.g. all of c:nuggets/X).
     * For each match, hand the consumer the sub-tag path (e.g. "iron") and the
     * resolved item list.
     */
    private static void forEachChildTag(final TagKey<Item> root, final ChildTagConsumer consumer) {
        final String prefix = root.location().getPath() + "/";
        for (final var entry : BuiltInRegistries.ITEM.getTags().toList()) {
            final TagKey<Item> tagKey = entry.getFirst();
            // Match sub-tags of the same namespace at exactly one level deeper.
            if (!tagKey.location().getNamespace().equals(root.location().getNamespace())) continue;
            final String path = tagKey.location().getPath();
            if (!path.startsWith(prefix)) continue;
            final String child = path.substring(prefix.length());
            if (child.isEmpty() || child.contains("/")) continue; // only direct children
            final List<Item> items = new ArrayList<>();
            for (final Holder<Item> holder : entry.getSecond()) {
                items.add(holder.value());
            }
            if (!items.isEmpty()) consumer.accept(child, items);
        }
    }

    private interface ChildTagConsumer {
        void accept(String childPath, List<Item> items);
    }

    private record Entry(Item item, int weight) {}

    private static boolean isShovelTarget(final net.minecraft.world.level.block.state.BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_SHOVEL);
    }

    private static boolean enabled() {
        try { return MagConfig.TOOL_SHOVEL_PAN_ENABLED.get(); } catch (Throwable t) { return true; }
    }

    private static double chance() {
        try { return MagConfig.TOOL_SHOVEL_PAN_CHANCE.get(); } catch (Throwable t) { return 0.04d; }
    }
}
