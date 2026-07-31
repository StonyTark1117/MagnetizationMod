package com.stonytark.magnetization.config;

import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** One-shot, non-destructive migration for config keys moved between releases. */
public final class MagConfigMigration {

    private static final Logger LOG = LoggerFactory.getLogger("magnetization/config");
    private static final Pattern SECTION = Pattern.compile("\\s*\\[([^]]+)]\\s*");

    private MagConfigMigration() {}

    /**
     * Copy keys that were moved from the old SERVER worldgen section into the
     * current COMMON sections. Existing new-format values always win. The old
     * file is retained so operators can review the migration and keep backups.
     */
    public static void migrateLegacyServerWorldgen() {
        final Path configDir = FMLPaths.CONFIGDIR.get();
        final Path oldFile = configDir.resolve("magnetization-server.toml");
        if (!Files.isRegularFile(oldFile)) return;

        try {
            final String oldText = Files.readString(oldFile, StandardCharsets.UTF_8);
            String commonText = Files.isRegularFile(configDir.resolve("magnetization-common.toml"))
                    ? Files.readString(configDir.resolve("magnetization-common.toml"), StandardCharsets.UTF_8) : "";
            boolean changed = false;

            final String ae2 = valueInSection(oldText, "worldgen", "ae2MeteoriteHookEnabled");
            if (ae2 != null && valueInSection(commonText, "compat", "ae2MeteoriteHookEnabled") == null) {
                commonText = upsert(commonText, "compat", "ae2MeteoriteHookEnabled", ae2);
                changed = true;
                LOG.info("Migrated magnetization-server.toml [worldgen].ae2MeteoriteHookEnabled to magnetization-common.toml [compat].");
            }

            final String switchRange = valueInSection(oldText, "worldgen", "magneticSwitchRange");
            if (switchRange != null && valueInSection(commonText, "content", "magneticSwitchRange") == null) {
                commonText = upsert(commonText, "content", "magneticSwitchRange", switchRange);
                changed = true;
                LOG.info("Migrated magnetization-server.toml [worldgen].magneticSwitchRange to magnetization-common.toml [content].");
            }

            if (changed) Files.writeString(configDir.resolve("magnetization-common.toml"), commonText,
                    StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            LOG.warn("Could not migrate legacy Magnetization config values; existing files were left untouched.", exception);
        }
    }

    private static String valueInSection(final String text, final String wantedSection, final String wantedKey) {
        boolean inSection = false;
        for (final String line : text.split("\\R", -1)) {
            final var section = SECTION.matcher(line);
            if (section.matches()) {
                inSection = wantedSection.equals(section.group(1).trim());
                continue;
            }
            if (!inSection) continue;
            final String withoutComment = line.split("#", 2)[0].trim();
            final int equals = withoutComment.indexOf('=');
            if (equals <= 0) continue;
            if (wantedKey.equals(withoutComment.substring(0, equals).trim())) {
                return withoutComment.substring(equals + 1).trim();
            }
        }
        return null;
    }

    private static String upsert(String text, final String section, final String key, final String value) {
        if (text.isEmpty()) text = "\n";
        final String[] lines = text.split("\\R", -1);
        int sectionLine = -1;
        int nextSection = lines.length;
        for (int i = 0; i < lines.length; i++) {
            final var match = SECTION.matcher(lines[i]);
            if (!match.matches()) continue;
            if (sectionLine >= 0) {
                nextSection = i;
                break;
            }
            if (section.equals(match.group(1).trim())) sectionLine = i;
        }

        final String insertion = "  " + key + " = " + value;
        if (sectionLine < 0) {
            return text + (text.endsWith("\n") ? "" : "\n") + "\n[" + section + "]\n" + insertion + "\n";
        }
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i == nextSection) out.append(insertion).append('\n');
            out.append(lines[i]);
            if (i + 1 < lines.length) out.append('\n');
        }
        if (nextSection == lines.length) out.append('\n').append(insertion);
        return out.toString();
    }
}
