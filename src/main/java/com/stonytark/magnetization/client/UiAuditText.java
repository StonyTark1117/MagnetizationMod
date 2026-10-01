package com.stonytark.magnetization.client;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Opt-in observation of text submitted by actual native screens and HUDs. */
@EventBusSubscriber(modid="magnetization", value=Dist.CLIENT)
public final class UiAuditText {
    public static int guiDepth;
    public static final boolean ENABLED=Boolean.getBoolean("magnetization.audit.ui");
    private static final java.util.Set<String> items = new java.util.HashSet<>();
    public static void recordItem(net.minecraft.world.item.ItemStack stack) { items.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()); }
    public static java.util.Set<String> items() { return java.util.Set.copyOf(items); }
    private static final List<String> lines = new ArrayList<>();
    public static void record(String text) { if (ENABLED) lines.add(text); }
    public static List<String> lines() { return List.copyOf(lines); }
    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event) {
        if (ENABLED) { lines.clear(); items.clear(); }
    }
}
