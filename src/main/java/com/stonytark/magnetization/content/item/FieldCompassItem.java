package com.stonytark.magnetization.content.item;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticFieldSource;
import com.stonytark.magnetization.worldgen.AnomalyBiome;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Diagnostic tool. Right-click in air for one of two modes:
 * <ul>
 *   <li>Default: report the strongest active field within 16 blocks (single
 *       action-bar line).</li>
 *   <li>Sneaking: list every active field in range, sorted by score, in chat.
 *       Useful for debugging multi-emitter setups where a far weaker emitter
 *       might be invisible behind a closer dominant one.</li>
 * </ul>
 */
public class FieldCompassItem extends Item {

    private static final double SCAN_RADIUS = 16.0d;

    public FieldCompassItem(final Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);

        // Inside the magnetic anomaly the compass loses calibration: every reading is
        // a randomly-chosen field, often misreporting strength/polarity/distance, so
        // the player can't trust it for navigation while standing in the field flux.
        if (AnomalyBiome.isAt(level, player.blockPosition())) {
            final List<Found> hits = scanAll(level, player.position());
            if (hits.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("compass.magnetization.anomaly_empty")
                                .withStyle(ChatFormatting.DARK_PURPLE),
                        true);
            } else {
                final Found random = hits.get(level.random.nextInt(hits.size()));
                player.displayClientMessage(
                        Component.translatable("compass.magnetization.anomaly_prefix")
                                .withStyle(ChatFormatting.DARK_PURPLE)
                                .append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
                                .append(format(random)),
                        true);
            }
            return InteractionResultHolder.success(stack);
        }

        final List<Found> hits = scanAll(level, player.position());

        if (hits.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("compass.magnetization.none").withStyle(ChatFormatting.GRAY),
                    true);
            return InteractionResultHolder.success(stack);
        }

        if (player.isShiftKeyDown()) {
            // Verbose mode: chat dump of every hit.
            player.displayClientMessage(Component.literal("--- Active fields (" + hits.size() + ") ---")
                    .withStyle(ChatFormatting.GRAY), false);
            for (Found f : hits) {
                player.displayClientMessage(format(f), false);
            }
        } else {
            // Default mode: only the top hit, action bar.
            player.displayClientMessage(format(hits.getFirst()), true);
        }
        return InteractionResultHolder.success(stack);
    }

    private static Component format(final Found f) {
        final Vec3 origin = f.field.origin();
        return Component.literal(String.format(
                "%s %s @ (%.1f, %.1f, %.1f) — %.1fm away, range %.0f",
                f.field.strength().name(), f.field.polarity().name(),
                origin.x, origin.y, origin.z,
                Math.sqrt(f.distanceSqr), f.field.range()
        )).withStyle(f.field.polarity().sign() > 0 ? ChatFormatting.RED : ChatFormatting.AQUA);
    }

    private record Found(MagneticField field, double distanceSqr, double score) {}

    private static List<Found> scanAll(final Level level, final Vec3 from) {
        final List<Found> results = new ArrayList<>();
        final int rChunks = (int) Math.ceil(SCAN_RADIUS / 16.0d);
        final int pcx = (int) Math.floor(from.x / 16.0d);
        final int pcz = (int) Math.floor(from.z / 16.0d);
        for (int cx = pcx - rChunks; cx <= pcx + rChunks; cx++) {
            for (int cz = pcz - rChunks; cz <= pcz + rChunks; cz++) {
                final var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof MagneticFieldSource source)) continue;
                    final MagneticField field = source.currentField();
                    if (field == null) continue;
                    final double d2 = be.getBlockPos().getCenter().distanceToSqr(from);
                    if (d2 > SCAN_RADIUS * SCAN_RADIUS) continue;
                    final double score = field.strength().force() / Math.max(1.0d, Math.sqrt(d2));
                    results.add(new Found(field, d2, score));
                }
            }
        }
        results.sort(Comparator.comparingDouble((Found f) -> f.score).reversed());
        return results;
    }
}
