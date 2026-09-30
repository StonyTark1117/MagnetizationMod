package com.stonytark.magnetization.mixin.compat;

import com.electronwill.nightconfig.toml.TomlParser;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Opt-in workaround for Create: Magnetics 0.0.4-alpha's dedicated-server SoundInstance crash. */
public final class CreateMagneticsServerMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOG = LoggerFactory.getLogger("magnetization/CreateMagneticsServerFix");
    static final String TARGET = "com.koudesuk.create_magnetics.block.kinetic.KineticMagnetBlockEntity";
    private static final String SOUND_DESCRIPTOR = "L" + TARGET.replace('.', '/') + "$KineticMagnetLoopingSound;";
    private boolean enabled;

    @Override
    public void onLoad(final String mixinPackage) {
        if (FMLLoader.getDist() != Dist.DEDICATED_SERVER) return;
        final var mods = LoadingModList.get();
        final String version = mods.getMods().stream()
                .filter(mod -> mod.getModId().equals("createmagnetics"))
                .map(mod -> mod.getVersion().toString()).findFirst().orElse("");
        // The standalone trial performs the same transformation. Let it own that
        // patch when present, so enabling this option never applies it twice.
        if (mods.getModFileById("create_magnetized_magnetics_compat") != null) return;
        enabled = supports(true, version, readEnabled(FMLPaths.CONFIGDIR.get().resolve("magnetization-common.toml")));
    }

    static boolean supports(final boolean dedicatedServer, final String version, final boolean optedIn) {
        return dedicatedServer && optedIn && "0.0.4-alpha".equals(version);
    }

    /** ModConfigSpec is not loaded yet. Read TOML without creating or changing it. */
    static boolean readEnabled(final Path file) {
        if (!Files.isRegularFile(file)) return false;
        try (var reader = Files.newBufferedReader(file)) {
            return Boolean.TRUE.equals(new TomlParser().parse(reader)
                    .get("compat.createMagneticsServerCrashWorkaround"));
        } catch (IOException | RuntimeException error) {
            LOG.warn("Cannot read Create: Magnetics server workaround option from {}; leaving it disabled", file, error);
            return false;
        }
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        return enabled && TARGET.equals(targetClassName);
    }
    @Override public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }

    @Override
    public void preApply(final String targetClassName, final ClassNode targetClass,
                         final String mixinClassName, final IMixinInfo mixinInfo) {
        isolateServerSounds(targetClass);
        LOG.info("Applied opt-in Create: Magnetics 0.0.4-alpha dedicated-server SoundInstance crash workaround");
    }

    static void isolateServerSounds(final ClassNode targetClass) {
        final var fields = targetClass.fields.stream().filter(field -> field.name.equals("clientLoopingSound")
                && field.desc.equals(SOUND_DESCRIPTOR)).toList();
        final var methods = targetClass.methods.stream().filter(method -> method.desc.equals("()V")
                && (method.name.equals("ensureLoopingSound") || method.name.equals("stopLoopingSound"))).toList();
        // Check the whole expected shape before making any changes. Never strip
        // arbitrary client references from a different upstream implementation.
        if (fields.size() != 1 || methods.size() != 2
                || methods.stream().map(method -> method.name).distinct().count() != 2) {
            throw new IllegalStateException("Create: Magnetics sound members differ from tested 0.0.4-alpha; cannot apply server workaround");
        }
        targetClass.fields.removeAll(fields);
        for (final var method : methods) {
            method.instructions.clear();
            method.instructions.add(new InsnNode(Opcodes.RETURN));
            method.tryCatchBlocks.clear();
            if (method.localVariables != null) method.localVariables.clear();
            method.visibleLocalVariableAnnotations = null;
            method.invisibleLocalVariableAnnotations = null;
            method.maxStack = 0;
            method.maxLocals = 1;
        }
    }

    @Override public void postApply(final String targetClassName, final ClassNode targetClass,
                                    final String mixinClassName, final IMixinInfo mixinInfo) {}
}
