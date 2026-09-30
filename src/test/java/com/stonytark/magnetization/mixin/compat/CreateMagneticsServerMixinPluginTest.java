package com.stonytark.magnetization.mixin.compat;

import com.stonytark.magnetization.config.MagConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CreateMagneticsServerMixinPluginTest {
    @TempDir Path directory;

    @Test void defaultsOffAndRequiresProcessRestart() {
        assertFalse(MagConfig.CREATE_MAGNETICS_SERVER_CRASH_WORKAROUND.getDefault());
        assertEquals(ModConfigSpec.RestartType.GAME,
                MagConfig.CREATE_MAGNETICS_SERVER_CRASH_WORKAROUND.getSpec().restartType());
        assertFalse(CreateMagneticsServerMixinPlugin.readEnabled(directory.resolve("missing.toml")));
    }

    @Test void onlyExplicitTomlBooleanOptsIn() throws Exception {
        final var file = directory.resolve("magnetization-common.toml");
        for (String value : new String[]{"false", "\"true\"", "1"}) {
            Files.writeString(file, "[compat]\ncreateMagneticsServerCrashWorkaround = " + value + "\n");
            assertFalse(CreateMagneticsServerMixinPlugin.readEnabled(file), value);
        }
        Files.writeString(file, "[compat]\ncreateMagneticsServerCrashWorkaround = true # opt in\n");
        assertTrue(CreateMagneticsServerMixinPlugin.readEnabled(file));
        Files.writeString(file, "[elsewhere]\ncreateMagneticsServerCrashWorkaround = true\n");
        assertFalse(CreateMagneticsServerMixinPlugin.readEnabled(file));
    }

    @Test void malformedConfigStaysDisabledAndUnmodified() throws Exception {
        final var file = directory.resolve("magnetization-common.toml");
        final String malformed = "[compat\ncreateMagneticsServerCrashWorkaround=true";
        Files.writeString(file, malformed);
        assertFalse(CreateMagneticsServerMixinPlugin.readEnabled(file));
        assertEquals(malformed, Files.readString(file));
    }

    @Test void requiresDedicatedServerExactUpstreamAndOptIn() {
        assertTrue(CreateMagneticsServerMixinPlugin.supports(true, "0.0.4-alpha", true));
        assertFalse(CreateMagneticsServerMixinPlugin.supports(false, "0.0.4-alpha", true));
        assertFalse(CreateMagneticsServerMixinPlugin.supports(true, "0.0.4-alpha", false));
        assertFalse(CreateMagneticsServerMixinPlugin.supports(true, "", true));
        assertFalse(CreateMagneticsServerMixinPlugin.supports(true, "0.0.5", true));
    }

    @Test void preservesServerLogicAndRemovesOnlySoundMembers() {
        final var node = fixture();
        final var tick = node.methods.getLast();
        final var tickCode = tick.instructions.getFirst();
        CreateMagneticsServerMixinPlugin.isolateServerSounds(node);
        assertEquals(1, node.fields.size());
        assertEquals("range", node.fields.getFirst().name);
        assertEquals(3, node.methods.size());
        for (var method : node.methods.subList(0, 2)) {
            assertEquals(1, method.instructions.size());
            assertEquals(Opcodes.RETURN, method.instructions.getFirst().getOpcode());
        }
        assertSame(tickCode, tick.instructions.getFirst());
    }

    @Test void changedUpstreamShapeFailsBeforeMutation() {
        final var node = fixture();
        node.methods.removeFirst();
        assertThrows(IllegalStateException.class, () -> CreateMagneticsServerMixinPlugin.isolateServerSounds(node));
        assertEquals(2, node.fields.size());
        assertEquals(2, node.methods.getFirst().instructions.size());
    }

    private static ClassNode fixture() {
        final var node = new ClassNode();
        node.fields.add(new FieldNode(0, "clientLoopingSound",
                "Lcom/koudesuk/create_magnetics/block/kinetic/KineticMagnetBlockEntity$KineticMagnetLoopingSound;", null, null));
        node.fields.add(new FieldNode(0, "range", "I", null, null));
        for (String name : new String[]{"ensureLoopingSound", "stopLoopingSound", "tick"}) {
            final var method = new MethodNode(0, name, "()V", null, null);
            method.instructions.add(new InsnNode(Opcodes.NOP));
            method.instructions.add(new InsnNode(Opcodes.RETURN));
            node.methods.add(method);
        }
        return node;
    }
}
