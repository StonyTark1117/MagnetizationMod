import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import jdk.internal.org.objectweb.asm.*;

/** Diagnostic-only ownership tracing for the pinned Registrate class. Never packaged in the mod. */
public final class RegistrateOwnershipAgent {
    private static final String OWNER = "com/tterrag/registrate/AbstractRegistrate";
    public static void premain(String arguments, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> type,
                                               ProtectionDomain domain, byte[] bytes) {
                if (!OWNER.equals(name)) return null;
                var reader = new ClassReader(bytes);
                var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
                    @Override public void visitEnd() {
                        visitField(Opcodes.ACC_PRIVATE, "auditOwners", "Ljava/util/Map;", null, null).visitEnd();
                        visitField(Opcodes.ACC_PRIVATE, "auditActive", "Ljava/util/concurrent/atomic/AtomicInteger;", null, null).visitEnd();
                        super.visitEnd();
                    }
                    @Override public MethodVisitor visitMethod(int access, String method, String descriptor,
                                                                String signature, String[] exceptions) {
                        var delegate = super.visitMethod(access, method, descriptor, signature, exceptions);
                        boolean mutation = method.equals("accept") || (method.equals("addRegisterCallback") && descriptor.startsWith("(Ljava/lang/String;"));
                        return new MethodVisitor(Opcodes.ASM8, delegate) {
                            @Override public void visitCode() {
                                super.visitCode();
                                if (mutation) {
                                    visitVarInsn(Opcodes.ALOAD, 0); visitFieldInsn(Opcodes.GETFIELD, OWNER, "auditOwners", "Ljava/util/Map;");
                                    visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder"); visitInsn(Opcodes.DUP);
                                    visitLdcInsn(method + "/active=");
                                    visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false);
                                    visitVarInsn(Opcodes.ALOAD, 0); visitFieldInsn(Opcodes.GETFIELD, OWNER, "auditActive", "Ljava/util/concurrent/atomic/AtomicInteger;");
                                    visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/concurrent/atomic/AtomicInteger", "incrementAndGet", "()I", false);
                                    visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
                                    visitLdcInsn("/thread=");
                                    visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                                    thread(); visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Thread", "threadId", "()J", false);
                                    visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(J)Ljava/lang/StringBuilder;", false);
                                    thread(); visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Thread", "getName", "()Ljava/lang/String;", false);
                                    visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                                    visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
                                    thread(); visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Thread", "getStackTrace", "()[Ljava/lang/StackTraceElement;", false);
                                    visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Arrays", "toString", "([Ljava/lang/Object;)Ljava/lang/String;", false);
                                    visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
                                    visitInsn(Opcodes.POP);
                                }
                                if (method.equals("onRegister")) {
                                    printField("REGISTRATE_OWNERS ", "auditOwners", "Ljava/util/Map;");
                                    printField("REGISTRATE_CALLBACKS ", "registerCallbacks", "Lcom/google/common/collect/Multimap;");
                                }
                            }
                            private void thread() {
                                visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                            }
                            private void printField(String label, String field, String descriptor) {
                                visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "err", "Ljava/io/PrintStream;");
                                visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder"); visitInsn(Opcodes.DUP);
                                visitLdcInsn(label);
                                visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false);
                                visitVarInsn(Opcodes.ALOAD, 0);
                                visitMethodInsn(Opcodes.INVOKEVIRTUAL, OWNER, "getModid", "()Ljava/lang/String;", false);
                                visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                                visitVarInsn(Opcodes.ALOAD, 0); visitFieldInsn(Opcodes.GETFIELD, OWNER, field, descriptor);
                                visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);
                                visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
                                visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
                            }
                            @Override public void visitInsn(int opcode) {
                                if (method.equals("<init>") && opcode == Opcodes.RETURN) {
                                    initialize("auditOwners", "java/util/concurrent/ConcurrentHashMap", "Ljava/util/Map;");
                                    initialize("auditActive", "java/util/concurrent/atomic/AtomicInteger", "Ljava/util/concurrent/atomic/AtomicInteger;");
                                }
                                if (mutation && (opcode == Opcodes.ARETURN || opcode == Opcodes.ATHROW)) {
                                    visitVarInsn(Opcodes.ALOAD, 0); visitFieldInsn(Opcodes.GETFIELD, OWNER, "auditActive", "Ljava/util/concurrent/atomic/AtomicInteger;");
                                    visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/concurrent/atomic/AtomicInteger", "decrementAndGet", "()I", false);
                                    super.visitInsn(Opcodes.POP);
                                }
                                super.visitInsn(opcode);
                            }
                            private void initialize(String field, String implementation, String descriptor) {
                                visitVarInsn(Opcodes.ALOAD, 0); visitTypeInsn(Opcodes.NEW, implementation); super.visitInsn(Opcodes.DUP);
                                visitMethodInsn(Opcodes.INVOKESPECIAL, implementation, "<init>", "()V", false);
                                visitFieldInsn(Opcodes.PUTFIELD, OWNER, field, descriptor);
                            }
                        };
                    }
                }, 0);
                return writer.toByteArray();
            }
        });
    }
}
