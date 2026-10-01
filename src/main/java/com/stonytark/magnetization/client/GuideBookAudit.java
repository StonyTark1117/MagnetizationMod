package com.stonytark.magnetization.client;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static com.stonytark.magnetization.gametest.LifecyclePresentationAudit.check;

/** Opt-in native reader sweep; reflection preserves Patchouli's optional status. */
final class GuideBookAudit {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("magnetization/guide-audit");
    private static final Pattern LINK = Pattern.compile("\\$\\(l:(magnetization:[^)#]+)(?:#[^)]*)?\\)");
    private static final Gson JSON = new Gson();
    private static List<Object> entries;
    private static Object book;
    private static int pass, entryIndex, spread, age, totalPages, links, undersized;
    private static CompletableFuture<Void> reload;
    private static boolean opened, finished;

    private GuideBookAudit() {}

    static boolean tick(final Minecraft mc) throws Exception {
        if (finished) {
            if (reload != null && !reload.isDone()) return false;
            return true;
        }
        if (entries == null) {
            if (reload != null && !reload.isDone()) return false;
            final Object registry = Class.forName("vazkii.patchouli.common.book.BookRegistry").getField("INSTANCE").get(null);
            book = ((Map<?, ?>) registry.getClass().getField("books").get(registry))
                    .get(ResourceLocation.parse("magnetization:field_manual"));
            check(book != null, "Native full-book sweep lost book registration");
            final Object contents = book.getClass().getMethod("getContents").invoke(book);
            check(!(boolean) contents.getClass().getMethod("isErrored").invoke(contents), "Patchouli contents failed to build");
            entries = new ArrayList<>(((Map<?, ?>) contents.getClass().getField("entries").get(contents)).values());
            entries.sort(Comparator.comparing(e -> {
                try { return e.getClass().getMethod("getId").invoke(e).toString(); }
                catch (Exception error) { throw new IllegalStateException(error); }
            }));
            // Compare runtime entry/page coverage to authored resources, not a stale fixed count.
            final var resources = mc.getResourceManager().listResources("patchouli_books/field_manual/en_us/entries",
                    id -> id.getNamespace().equals("magnetization") && id.getPath().endsWith(".json"));
            check(entries.size() == resources.size(), "Native book omitted authored entries");
            int authoredPages = 0;
            for (final var resource : resources.values()) try (var reader = resource.openAsReader()) {
                authoredPages += JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("pages").size();
            }
            int nativePages = 0;
            for (final Object entry : entries) nativePages += pages(entry).size();
            check(nativePages == authoredPages, "Native book page coverage differs from authored definitions");
            mc.options.guiScale().set(pass % 2 == 0 ? 2 : 3);
            mc.resizeDisplay();
            LOG.info("GUIDE_SWEEP_START pass={} locale={} guiScale={} entries={} pages={}",
                    pass, mc.getLanguageManager().getSelected(), mc.options.guiScale().get(), entries.size(), nativePages);
        }
        final Object entry = entries.get(entryIndex);
        if (!opened) {
            final Class<?> bookType = Class.forName("vazkii.patchouli.common.book.Book");
            final Class<?> entryType = Class.forName("vazkii.patchouli.client.book.BookEntry");
            mc.setScreen((Screen) Class.forName("vazkii.patchouli.client.book.gui.GuiBookEntry")
                    .getConstructor(bookType, entryType, int.class).newInstance(book, entry, spread));
            opened = true; age = 0; return false;
        }
        if (++age < 6) return false; // Several ordinary rendered frames before capture.
        final Screen screen = mc.screen;
        check(screen != null && screen.getClass().getSimpleName().equals("GuiBookEntry"), "Native page sweep lost reader");
        final String id = entry.getClass().getMethod("getId").invoke(entry).toString();
        final List<?> pages = pages(entry);
        final List<Map<String, Object>> pageRecords = new ArrayList<>();
        for (int index = spread * 2; index < Math.min(pages.size(), spread * 2 + 2); index++) {
            final Object page = pages.get(index);
            final Map<String, Object> record = inspectPage(mc, screen, page, id, index);
            pageRecords.add(record); totalPages++;
        }
        final var record = new LinkedHashMap<String, Object>();
        record.put("pass", pass); record.put("locale", mc.getLanguageManager().getSelected());
        record.put("effectiveGuiScale", mc.getWindow().getGuiScale());
        record.put("guiScale", mc.options.guiScale().get()); record.put("entry", id); record.put("spread", spread);
        record.put("pages", pageRecords);
        final String name = "guide-" + pass + "-" + id.replace(':', '-').replace('/', '-') + "-" + spread;
        mc.getToasts().clear();
        Screenshot.grab(mc.gameDirectory, "validation-" + name + ".png", mc.getMainRenderTarget(), message -> {});
        record.put("capture", "validation-" + name + ".png");
        final var output = com.stonytark.magnetization.gametest.LifecyclePresentationAudit.control().resolve("book-pages.jsonl");
        Files.writeString(output, JSON.toJson(record) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        LOG.info("GUIDE_SPREAD_PASS pass={} entry={} spread={} pages={}", pass, id, spread, pageRecords.size());
        opened = false;
        if (++spread * 2 >= pages.size()) { spread = 0; entryIndex++; }
        if (entryIndex < entries.size()) return false;
        if (++pass < 4) {
            entryIndex = 0; entries = null;
            if (pass == 2) {
                mc.getLanguageManager().setSelected("de_de");
                reload = mc.reloadResourcePacks();
            }
            return false;
        }
        check(links > 0, "Native link validation was vacuous");
        LOG.info("GUIDE_SWEEP_COMPLETE renderedPages={} clickedLinks={} undersizedPages={}", totalPages, links, undersized);
        Files.writeString(com.stonytark.magnetization.gametest.LifecyclePresentationAudit.control().resolve("book-sweep-done"),
                "pages=" + totalPages + " links=" + links + " undersized=" + undersized);
        // Record all layout failures for repair in one pass; the final evidence gate requires zero.
        mc.getLanguageManager().setSelected("en_us");
        mc.setScreen(null);
        reload = mc.reloadResourcePacks();
        finished = true;
        return false;
    }

    private static Map<String, Object> inspectPage(final Minecraft mc, final Screen screen, final Object page,
                                                   final String entryId, final int pageIndex) throws Exception {
        final var record = new LinkedHashMap<String, Object>();
        record.put("page", pageIndex + 1); record.put("class", page.getClass().getSimpleName());
        final Object source = field(page, "sourceObject").get(page);
        record.put("definition", source);
        final var definition = (com.google.gson.JsonObject) source;
        if (definition.has("text")) check(!I18n.get(definition.get("text").getAsString()).startsWith("Format error:"),
                "Native localized body has a formatting error in " + entryId);
        final List<net.minecraft.network.chat.Component> titles = new ArrayList<>();
        switch (page.getClass().getSimpleName()) {
            case "PageText" -> {
                final Object entry = screen.getClass().getMethod("getEntry").invoke(screen);
                titles.add(pageIndex == 0 ? (net.minecraft.network.chat.Component) entry.getClass().getMethod("getName").invoke(entry)
                        : net.minecraft.network.chat.Component.literal(definition.has("title") ? I18n.get(definition.get("title").getAsString()) : ""));
            }
            case "PageSpotlight" -> {
                final var stacks = (net.minecraft.world.item.ItemStack[]) field(page, "stacks").get(page);
                check(stacks.length > 0 && !stacks[0].isEmpty(), "Native spotlight item missing in " + entryId);
                titles.add(definition.has("title") ? net.minecraft.network.chat.Component.literal(I18n.get(definition.get("title").getAsString())) : stacks[0].getHoverName());
            }
            case "PageCrafting", "PageSmelting" -> {
                check(field(page, "recipe1").get(page) != null, "Native displayed recipe missing in " + entryId);
                if (definition.has("recipe2")) check(field(page, "recipe2").get(page) != null, "Native secondary recipe missing in " + entryId);
                titles.add((net.minecraft.network.chat.Component) field(page, "title1").get(page));
                titles.add((net.minecraft.network.chat.Component) field(page, "title2").get(page));
            }
            default -> { }
        }
        if (!titles.isEmpty()) {
            final int width = titles.stream().filter(java.util.Objects::nonNull).mapToInt(mc.font::width).max().orElse(0);
            record.put("titleWidth", width);
            if (width > 116) LOG.warn("GUIDE_TITLE_OVERFLOW entry={} page={} width={} titles={}", entryId, pageIndex+1, width, titles);
        }
        Field rendererField;
        try { rendererField = field(page, "textRender"); }
        catch (NoSuchFieldException noText) { return record; }
        final Object renderer = rendererField.get(page);
        if (renderer == null) return record;
        final float scale = field(renderer, "scale").getFloat(renderer);
        record.put("textScale", scale);
        if (scale < 1.0f) { undersized++; LOG.warn("GUIDE_LAYOUT_UNDERSIZED entry={} page={} scale={}", entryId, pageIndex + 1, scale); }
        final List<?> words = (List<?>) field(renderer, "words").get(renderer);
        record.put("words", words.size());
        double maxX = 0, maxY = 0;
        if (!words.isEmpty()) {
            final int ox = field(words.getFirst(), "x").getInt(words.getFirst());
            final int oy = field(words.getFirst(), "y").getInt(words.getFirst());
            for (final Object word : words) {
                maxX = Math.max(maxX, ox + (field(word, "x").getInt(word) - ox + visibleWidth(mc, (net.minecraft.network.chat.Component) field(word, "text").get(word))) * scale);
                maxY = Math.max(maxY, oy + (field(word, "y").getInt(word) - oy + field(word, "height").getInt(word)) * scale);
            }
        }
        record.put("wordBounds", Map.of("maxX", maxX, "maxY", maxY, "widthLimit", 116, "heightLimit", 156));
        if (maxX > 116.01 || maxY > 156.01) LOG.warn("GUIDE_LAYOUT_OVERFLOW entry={} page={} x={} y={}", entryId, pageIndex+1, maxX, maxY);
        for (final Object word : words) {
            final var text = (net.minecraft.network.chat.Component) field(word, "text").get(word);
            check(!text.getString().contains("$(") && !text.getString().contains("Format error:"), "Unsupported Patchouli token rendered in " + entryId);
        }
        if (pass != 0 || words.isEmpty()) return record;
        if (!definition.has("text")) return record;
        final List<String> targets = new ArrayList<>();
        final var matcher = LINK.matcher(I18n.get(definition.get("text").getAsString()));
        while (matcher.find()) targets.add(matcher.group(1));
        if (targets.isEmpty()) return record;
        final IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        int targetIndex = 0;
        final int originX = field(words.getFirst(), "x").getInt(words.getFirst());
        final int originY = field(words.getFirst(), "y").getInt(words.getFirst());
        for (final Object word : words) {
            final Object click = field(word, "onClick").get(word);
            if (click == null || visited.put(click, true) != null) continue;
            check(targetIndex < targets.size(), "Unexpected clickable word in " + entryId);
            final double x = originX + (field(word, "x").getInt(word) + field(screen, "bookLeft").getInt(screen) - originX + 1) * scale;
            final double y = originY + (field(word, "y").getInt(word) + field(screen, "bookTop").getInt(screen) - originY + 1) * scale;
            final double bookScale = field(screen, "scaleFactor").getFloat(screen);
            check(screen.mouseClicked((field(page, "left").getInt(page) + x) * bookScale,
                    (field(page, "top").getInt(page) + y) * bookScale, 0),
                    "Authored link did not accept native screen click");
            final Object destination = mc.screen.getClass().getMethod("getEntry").invoke(mc.screen);
            final String actual = destination.getClass().getMethod("getId").invoke(destination).toString();
            check(actual.equals(targets.get(targetIndex)), "Native link navigated to wrong entry: " + actual);
            LOG.info("GUIDE_LINK_PASS source={} page={} destination={}", entryId, pageIndex + 1, actual);
            targetIndex++; links++; mc.setScreen(screen);
        }
        check(targetIndex == targets.size(), "Authored links omitted by native layout in " + entryId);
        record.put("clickedLinks", targetIndex);
        return record;
    }

    private static int visibleWidth(final Minecraft mc, final net.minecraft.network.chat.Component text) {
        // Patchouli's Word.width is the remaining span's hitbox, not this drawn substring.
        // Trailing wrap whitespace also has no visible glyphs. Preserve each segment's style.
        final int[] remaining = {text.getString().stripTrailing().length()};
        final var visible = net.minecraft.network.chat.Component.empty();
        text.visit((style, value) -> {
            final int count = Math.min(value.length(), remaining[0]);
            if (count > 0) visible.append(net.minecraft.network.chat.Component.literal(value.substring(0, count)).setStyle(style));
            remaining[0] -= count;
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return mc.font.width(visible);
    }

    private static List<?> pages(final Object entry) throws Exception {
        return (List<?>) entry.getClass().getMethod("getPages").invoke(entry);
    }

    private static Field field(final Object object, final String name) throws NoSuchFieldException {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { final Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException absent) { /* Inspect inherited page/renderer state. */ }
        }
        throw new NoSuchFieldException(name);
    }
}
