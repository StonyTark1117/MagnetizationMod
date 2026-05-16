package com.stonytark.magnetization.client;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticFieldSource;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.EmitterRegistry;
import com.stonytark.magnetization.registry.MagItems;
import com.stonytark.magnetization.worldgen.AnomalyBiome;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Renders a small HUD overlay just above the hotbar when the player is holding
 * a Field Compass in either hand <em>or</em> has one in a Curios slot. Shows
 * bearing-to-target, distance, polarity, and a flavour line when standing in
 * the Anomaly biome.
 *
 * <p>Registered as a {@link LayeredDraw.Layer} via
 * {@link RegisterGuiLayersEvent} so it composes with vanilla HUD layering and
 * respects F1 (hide HUD).
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID, value = Dist.CLIENT)
public final class FieldCompassHud {

    /** Pixels above the hotbar to draw the first text line. */
    private static final int HOTBAR_OFFSET = 60;
    /** Horizontal centring — minecraft hotbar is centred at width / 2. */
    private static final int CENTER_OFFSET = 0;

    private FieldCompassHud() {}

    @SubscribeEvent
    public static void onRegisterGuiLayers(final RegisterGuiLayersEvent event) {
        event.registerAboveAll(Magnetization.id("field_compass_hud"), FieldCompassHud::render);
    }

    private static void render(final GuiGraphics g, final DeltaTracker delta) {
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) return;
        if (mc.getDebugOverlay() != null && mc.getDebugOverlay().showDebugScreen()) return; // don't clutter the debug overlay
        if (mc.screen != null) return; // suppress while a screen is open

        final ItemStack compass = findCompass(player);
        if (compass == null) return;
        final Level level = player.level();
        if (level == null) return;

        // Anomaly takes priority — show a flavour line instead of a real reading,
        // matching the needle's scramble behaviour so the HUD doesn't contradict
        // the rotating texture.
        final BlockPos at = player.blockPosition();
        if (level.getBiome(at).is(AnomalyBiome.KEY)) {
            drawCentered(g, mc.font,
                    Component.translatable("hud.magnetization.compass.anomaly")
                            .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC),
                    null);
            return;
        }

        final BlockPos target = findNearestEmitter(level, player.position());
        if (target == null) {
            drawCentered(g, mc.font,
                    Component.translatable("hud.magnetization.compass.no_target")
                            .withStyle(ChatFormatting.DARK_GRAY),
                    null);
            return;
        }
        final BlockEntity be = level.getBlockEntity(target);
        if (!(be instanceof MagneticFieldSource source)) return;
        final MagneticField field = source.currentField();
        if (field == null) return;

        // Top line: bearing + distance. Bearing as cardinal compass-rose (N/NE/E/…)
        // for at-a-glance reading; numeric degrees in parens for the picky.
        final double dx = target.getX() + 0.5 - player.getX();
        final double dz = target.getZ() + 0.5 - player.getZ();
        final double dist = Math.sqrt(dx * dx + dz * dz);
        final double bearingDeg = ((Math.toDegrees(Math.atan2(-dx, dz)) + 360.0) % 360.0);
        final String cardinal = cardinal(bearingDeg);
        final Component top = Component.translatable("hud.magnetization.compass.bearing",
                        cardinal, String.format("%.0f°", bearingDeg), String.format("%.1fm", dist))
                .withStyle(ChatFormatting.GRAY);

        // Bottom line: polarity + strength tier — colour-coded so a quick glance
        // tells the player whether they're walking toward an attract or repel.
        final MagneticPolarity pol = field.polarity();
        final ChatFormatting polColor = pol == MagneticPolarity.NORTH ? ChatFormatting.AQUA
                : (pol == MagneticPolarity.SOUTH ? ChatFormatting.RED : ChatFormatting.DARK_GRAY);
        final Component bottom = Component.translatable("hud.magnetization.compass.target",
                        Component.literal(pol.getSerializedName().toUpperCase()).withStyle(polColor),
                        field.strength().name())
                .withStyle(ChatFormatting.GRAY);

        drawCentered(g, mc.font, top, bottom);
    }

    private static void drawCentered(final GuiGraphics g, final Font font,
                                      final Component top, final @Nullable Component bottom) {
        final int screenW = g.guiWidth();
        final int screenH = g.guiHeight();
        final int yTop = screenH - HOTBAR_OFFSET;
        g.drawCenteredString(font, top, screenW / 2 + CENTER_OFFSET, yTop, 0xFFFFFFFF);
        if (bottom != null) {
            g.drawCenteredString(font, bottom, screenW / 2 + CENTER_OFFSET, yTop + 10, 0xFFFFFFFF);
        }
    }

    /** Find a Field Compass in either hand, or — if Curios is loaded — in any
     *  curios slot. Returns the first ItemStack found, or null if none. */
    private static @Nullable ItemStack findCompass(final LivingEntity entity) {
        final ItemStack main = entity.getMainHandItem();
        if (main.is(MagItems.FIELD_COMPASS.get())) return main;
        final ItemStack off = entity.getOffhandItem();
        if (off.is(MagItems.FIELD_COMPASS.get())) return off;
        if (!ModList.get().isLoaded("curios")) return null;
        return findCompassInCurios(entity);
    }

    /** Curios lookup isolated so the Curios class refs only resolve when the
     *  mod is actually loaded. */
    private static @Nullable ItemStack findCompassInCurios(final LivingEntity entity) {
        try {
            final var handler = entity.getCapability(top.theillusivec4.curios.api.CuriosCapability.INVENTORY);
            if (handler == null) return null;
            for (final var entry : handler.getCurios().entrySet()) {
                final var stacks = entry.getValue().getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    final ItemStack s = stacks.getStackInSlot(i);
                    if (s.is(MagItems.FIELD_COMPASS.get())) return s;
                }
            }
        } catch (final Throwable t) {
            // Curios API shifted under us — fail soft, don't crash the HUD render.
        }
        return null;
    }

    private static @Nullable BlockPos findNearestEmitter(final Level level, final Vec3 from) {
        final double range = compassRange();
        BlockPos best = null;
        double bestDistSqr = range * range;
        for (final BlockPos pos : EmitterRegistry.snapshot(level)) {
            final double d2 = pos.getCenter().distanceToSqr(from);
            if (d2 >= bestDistSqr) continue;
            final BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof MagneticFieldSource source)) continue;
            if (source.currentField() == null) continue;
            best = pos;
            bestDistSqr = d2;
        }
        return best;
    }

    private static double compassRange() {
        try { return MagConfig.COMPASS_RANGE.get(); } catch (Throwable t) { return 16.0d; }
    }

    /** Map 0..360° to an 8-point compass-rose label. */
    private static String cardinal(final double deg) {
        final String[] rose = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        final int idx = (int) Math.floor(((deg + 22.5) % 360.0) / 45.0);
        return rose[idx];
    }

    /** Unused — kept so onRegisterGuiLayers stays valid if we add per-player
     *  state later. */
    @SuppressWarnings("unused")
    private static UUID playerKey(final LocalPlayer p) { return p.getUUID(); }
}
