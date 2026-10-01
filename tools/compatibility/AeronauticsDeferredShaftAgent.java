import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Diagnostic candidate only: defer two AeroBlocks shaft lookups; never shipped in Magnetization. */
public final class AeronauticsDeferredShaftAgent {
    private static final String OWNER = "dev/eriksonn/aeronautics/index/AeroBlocks";
    private static final String CONNECT = "net/minecraft/client/gui/screens/ConnectScreen";
    public static void premain(String arguments, Instrumentation instrumentation) {
        boolean candidate = "candidate".equals(arguments);
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> type,
                                              ProtectionDomain domain, byte[] bytes) {
                if (OWNER.equals(name) && candidate) return defer(bytes);
                if (!CONNECT.equals(name)) return null;
                var node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
                for (var method : node.methods) if (method.name.equals("startConnecting")) {
                    var signal = new InsnList();
                    signal.add(new LdcInsnNode("magnetization.audit.registryReady"));
                    signal.add(new LdcInsnNode("true"));
                    signal.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty",
                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false));
                    signal.add(new InsnNode(Opcodes.POP)); method.instructions.insert(signal);
                }
                var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
            }
        });
        var probe = new Thread(() -> {
            try {
                while (!Boolean.getBoolean("magnetization.audit.registryReady")) Thread.sleep(100);
                var built = Arrays.stream(instrumentation.getAllLoadedClasses())
                        .filter(c -> c.getName().equals("net.minecraft.core.registries.BuiltInRegistries")).findFirst().orElseThrow();
                var loader = built.getClassLoader();
                var registry = loader.loadClass("net.minecraft.core.Registry");
                var resource = loader.loadClass("net.minecraft.resources.ResourceLocation");
                var key = registry.getMethod("getKey", Object.class);
                for (String field : List.of("BLOCK", "ITEM", "ENTITY_TYPE")) {
                    Object values = built.getField(field).get(null);
                    var ids = new TreeSet<String>();
                    for (Object value : (Iterable<?>)values) {
                        String id = key.invoke(values, value).toString();
                        if (id.startsWith("create:") || id.startsWith("aeronautics:")) ids.add(id);
                    }
                    System.err.println("CANDIDATE_REGISTRY " + field + " " + ids);
                }
                Object blocks = built.getField("BLOCK").get(null);
                Object shaft = registry.getMethod("get", resource).invoke(blocks, resource.getMethod("parse", String.class).invoke(null, "create:shaft"));
                Object variants = loader.loadClass("com.simibubi.create.content.decoration.encasing.EncasingRegistry")
                        .getMethod("getVariants", loader.loadClass("net.minecraft.world.level.block.Block")).invoke(null, shaft);
                var ids = new ArrayList<String>();
                for (Object variant : (Iterable<?>)variants) ids.add(key.invoke(blocks, variant).toString());
                Collections.sort(ids); // Retain duplicate registrations, which the unmodified artifact contains.
                System.err.println("CANDIDATE_SHAFT_VARIANTS " + ids);
                System.err.println("CANDIDATE_REGISTRY_PROBE_PASS candidate=" + candidate);
            } catch (Throwable error) { error.printStackTrace(); System.err.println("CANDIDATE_REGISTRY_PROBE_FAILED"); }
        }, "registrate-candidate-probe");
        probe.setDaemon(true); probe.start();
    }
    private static byte[] defer(byte[] bytes) {
        var node = new ClassNode(); new ClassReader(bytes).accept(node, 0); int changed = 0;
        for (var method : node.methods) for (var instruction : method.instructions.toArray()) {
            if (!(instruction instanceof FieldInsnNode field) || field.getOpcode()!=Opcodes.GETSTATIC
                    || !field.owner.equals("com/simibubi/create/AllBlocks") || !field.name.equals("SHAFT")) continue;
            var next = field.getNext();
            while (next != null && next.getOpcode() < 0) next=next.getNext();
            if (!(next instanceof MethodInsnNode call) || !call.owner.equals("com/simibubi/create/content/decoration/encasing/EncasingRegistry")
                    || !call.name.equals("addVariantTo")) continue;
            var bootstrap = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
            var get = Type.getMethodType("()Ljava/lang/Object;");
            method.instructions.set(field, new InvokeDynamicInsnNode("get", "()Ljava/util/function/Supplier;", bootstrap,
                    get, new Handle(Opcodes.H_INVOKESTATIC, OWNER, "auditDeferredShaft", "()Ljava/lang/Object;", false), get));
            changed++;
        }
        if (changed != 2) throw new IllegalStateException("Expected exactly two eager shaft lookups, found " + changed);
        var helper = new MethodNode(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC|Opcodes.ACC_SYNTHETIC,
                "auditDeferredShaft", "()Ljava/lang/Object;", null, null);
        helper.visitCode();
        helper.visitFieldInsn(Opcodes.GETSTATIC,"java/lang/System","err","Ljava/io/PrintStream;");
        helper.visitLdcInsn("AERONAUTICS_DEFERRED_SHAFT_LOOKUP");
        helper.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/io/PrintStream","println","(Ljava/lang/String;)V",false);
        helper.visitFieldInsn(Opcodes.GETSTATIC,"com/simibubi/create/AllBlocks","SHAFT","Lcom/tterrag/registrate/util/entry/BlockEntry;");
        helper.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"com/tterrag/registrate/util/entry/BlockEntry","get","()Ljava/lang/Object;",false);
        helper.visitInsn(Opcodes.ARETURN); helper.visitMaxs(0,0); helper.visitEnd(); node.methods.add(helper);
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer);
        System.err.println("AERONAUTICS_CANDIDATE_TRANSFORMED lookups=" + changed); return writer.toByteArray();
    }
}
