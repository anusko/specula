package specula.bytecode;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import asmlib.InfoClass;
import asmlib.InfoMethod;
import asmlib.Util;

/**
 * Modifica classes que implementem um método "public <final> void run()".
 * 
 * Renomeia o método "public <final> void run()" para
 * "private void runInContinuation()" e cria o método "public void run()".
 * 
 * Esta classe não suporta concurrência.
 *
 */
public class RunnableModifierClassAdapter extends ClassAdapter implements Opcodes {

	private static int counter = 0;

	//	private boolean implementsRunnable = false;
	private boolean needsModification = false;

	private MethodVisitor clinitVisitor;
	private MethodVisitor runVisitor;

	private String name;
	private int id;


	public RunnableModifierClassAdapter(ClassVisitor cv, InfoClass infoClass) {
		super(cv);
		assert (infoClass != null);

		//		// ver se a classe ou superclasse(s) concretiza a interface java.lang.Runnable
		//		InfoClass ic_pointer = infoClass;
		//		while (ic_pointer != null) {
		//			if (ic_pointer.hasInterfaceName(asmlib.Type.fromCommon("java.lang.Runnable"))) {
		//				this.implementsRunnable = true;
		//				break;
		//			} else {
		//				ic_pointer = ic_pointer.superclass();
		//			}
		//		}

		InfoMethod im = infoClass.getMethod("run", "()V");
		if (im != null) {
			boolean isAbstract = Util.checkFlag(
					im.access(),
					ACC_ABSTRACT);
			boolean isStatic = Util.checkFlag(
					im.access(),
					ACC_STATIC);

			this.needsModification = ! (isAbstract || isStatic);
		}
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces);

		this.name = name;

		if (this.needsModification) {
			FieldVisitor fv = super.visitField(ACC_PRIVATE + ACC_FINAL + ACC_STATIC,
					"specula$IN_USE", "Ljava/lang/Object;", null, null);
			fv.visitEnd();

			fv = super.visitField(ACC_PRIVATE + ACC_FINAL + ACC_STATIC,
					"specula$inContinuation", "Ljava/lang/ThreadLocal;",
					"Ljava/lang/ThreadLocal<Ljava/lang/Object;>;", null);
			fv.visitEnd();
		}
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		if (this.needsModification 	&& Util.checkFlag(access, ACC_STATIC)
				&& name.equals("<clinit>") && desc.equals("()V")) {
			assert (this.clinitVisitor == null);

			this.clinitVisitor = super.visitMethod(access, name, desc, signature, exceptions);
		}

		if (this.needsModification && name.equals("run") && desc.equals("()V")) {
			assert (this.runVisitor == null);

			// remover a flag final... hacky!
			this.runVisitor = super.visitMethod(ACC_PUBLIC, name, desc, signature, exceptions);
			// rename the method
			access = ACC_PRIVATE;
			this.id = counter++;
			name = "specula$runInContinuation_" + id;	
		}

		return super.visitMethod(access, name, desc, signature, exceptions);	
	}

	@Override
	public void visitEnd() {
		if (this.needsModification) {
			if (this.clinitVisitor == null) {
				this.clinitVisitor = super.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
			}
			clinit(this.clinitVisitor);

			assert (this.runVisitor != null);
			run(this.runVisitor);
		}

		super.visitEnd();
	}

	private void clinit(MethodVisitor mv) {
		mv.visitCode();
		Label l0 = new Label();
		mv.visitLabel(l0);
		mv.visitLineNumber(9, l0);
		mv.visitTypeInsn(NEW, "java/lang/Object");
		mv.visitInsn(DUP);
		mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
		mv.visitFieldInsn(PUTSTATIC, this.name, "specula$IN_USE", "Ljava/lang/Object;");
		Label l1 = new Label();
		mv.visitLabel(l1);
		mv.visitLineNumber(12, l1);
		mv.visitTypeInsn(NEW, "java/lang/ThreadLocal");
		mv.visitInsn(DUP);
		mv.visitMethodInsn(INVOKESPECIAL, "java/lang/ThreadLocal", "<init>", "()V");
		Label l2 = new Label();
		mv.visitLabel(l2);
		mv.visitLineNumber(11, l2);
		mv.visitFieldInsn(PUTSTATIC, this.name, "specula$inContinuation", "Ljava/lang/ThreadLocal;");
		Label l3 = new Label();
		mv.visitLabel(l3);
		mv.visitLineNumber(7, l3);
		mv.visitInsn(RETURN);
		mv.visitMaxs(2, 0);
		mv.visitEnd();
	}

	private void run(MethodVisitor mv) {
		mv.visitCode();
		Label l0 = new Label();
		Label l1 = new Label();
		mv.visitTryCatchBlock(l0, l1, l1, null);
		Label l2 = new Label();
		mv.visitLabel(l2);
		mv.visitLineNumber(15, l2);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitTypeInsn(INSTANCEOF, "java/lang/Runnable");
		Label l3 = new Label();
		mv.visitJumpInsn(IFNE, l3);
		Label l4 = new Label();
		mv.visitLabel(l4);
		mv.visitLineNumber(16, l4);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitMethodInsn(INVOKESPECIAL, this.name, "specula$runInContinuation_" + this.id, "()V");
		Label l5 = new Label();
		mv.visitLabel(l5);
		mv.visitLineNumber(17, l5);
		mv.visitInsn(RETURN);
		mv.visitLabel(l3);
		mv.visitLineNumber(20, l3);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		mv.visitFieldInsn(GETSTATIC, this.name, "specula$inContinuation", "Ljava/lang/ThreadLocal;");
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/ThreadLocal", "get", "()Ljava/lang/Object;");
		Label l6 = new Label();
		mv.visitJumpInsn(IFNONNULL, l6);
		mv.visitLabel(l0);
		mv.visitLineNumber(22, l0);
		mv.visitFieldInsn(GETSTATIC, this.name, "specula$inContinuation", "Ljava/lang/ThreadLocal;");
		mv.visitFieldInsn(GETSTATIC, this.name, "specula$IN_USE", "Ljava/lang/Object;");
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/ThreadLocal", "set", "(Ljava/lang/Object;)V");
		Label l7 = new Label();
		mv.visitLabel(l7);
		mv.visitLineNumber(23, l7);
		mv.visitTypeInsn(NEW, "specula/runtime/ThreadContext");
		mv.visitInsn(DUP);
		mv.visitMethodInsn(INVOKESPECIAL, "specula/runtime/ThreadContext", "<init>", "()V");
		mv.visitVarInsn(ASTORE, 1);
		Label l8 = new Label();
		mv.visitLabel(l8);
		mv.visitLineNumber(24, l8);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitVarInsn(ALOAD, 1);
		mv.visitMethodInsn(INVOKESTATIC, "org/apache/commons/javaflow/Continuation", "startWith", "(Ljava/lang/Runnable;Ljava/lang/Object;)Lorg/apache/commons/javaflow/Continuation;");
		mv.visitVarInsn(ASTORE, 2);
		Label l9 = new Label();
		mv.visitLabel(l9);
		mv.visitLineNumber(26, l9);
		mv.visitFrame(Opcodes.F_APPEND,2, new Object[] {"specula/runtime/ThreadContext", "org/apache/commons/javaflow/Continuation"}, 0, null);
		mv.visitVarInsn(ALOAD, 1);
		mv.visitMethodInsn(INVOKEVIRTUAL, "specula/runtime/ThreadContext", "hasTxAborted", "()Z");
		Label l10 = new Label();
		mv.visitJumpInsn(IFNE, l10);
		Label l11 = new Label();
		mv.visitLabel(l11);
		mv.visitLineNumber(27, l11);
		mv.visitVarInsn(ALOAD, 2);
		Label l12 = new Label();
		mv.visitJumpInsn(IFNULL, l12);
		Label l13 = new Label();
		mv.visitLabel(l13);
		mv.visitLineNumber(28, l13);
		mv.visitVarInsn(ALOAD, 2);
		mv.visitVarInsn(ALOAD, 1);
		mv.visitMethodInsn(INVOKESTATIC, "org/apache/commons/javaflow/Continuation", "continueWith", "(Lorg/apache/commons/javaflow/Continuation;Ljava/lang/Object;)Lorg/apache/commons/javaflow/Continuation;");
		mv.visitVarInsn(ASTORE, 2);
		Label l14 = new Label();
		mv.visitLabel(l14);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		mv.visitJumpInsn(GOTO, l9);
		mv.visitLabel(l10);
		mv.visitLineNumber(33, l10);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		mv.visitVarInsn(ALOAD, 1);
		mv.visitMethodInsn(INVOKEVIRTUAL, "specula/runtime/ThreadContext", "retry", "()Lorg/apache/commons/javaflow/Continuation;");
		mv.visitVarInsn(ASTORE, 2);
		Label l15 = new Label();
		mv.visitLabel(l15);
		mv.visitLineNumber(35, l15);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		mv.visitJumpInsn(GOTO, l9);
		mv.visitLabel(l1);
		mv.visitLineNumber(36, l1);
		mv.visitFrame(Opcodes.F_FULL, 1, new Object[] {this.name}, 1, new Object[] {"java/lang/Throwable"});
		mv.visitVarInsn(ASTORE, 3);
		Label l16 = new Label();
		mv.visitLabel(l16);
		mv.visitLineNumber(37, l16);
		mv.visitFieldInsn(GETSTATIC, this.name, "specula$inContinuation", "Ljava/lang/ThreadLocal;");
		mv.visitInsn(ACONST_NULL);
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/ThreadLocal", "set", "(Ljava/lang/Object;)V");
		Label l17 = new Label();
		mv.visitLabel(l17);
		mv.visitLineNumber(38, l17);
		mv.visitVarInsn(ALOAD, 3);
		mv.visitInsn(ATHROW);
		mv.visitLabel(l12);
		mv.visitLineNumber(37, l12);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		mv.visitFieldInsn(GETSTATIC, this.name, "specula$inContinuation", "Ljava/lang/ThreadLocal;");
		mv.visitInsn(ACONST_NULL);
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/ThreadLocal", "set", "(Ljava/lang/Object;)V");
		Label l18 = new Label();
		mv.visitJumpInsn(GOTO, l18);
		mv.visitLabel(l6);
		mv.visitLineNumber(40, l6);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitMethodInsn(INVOKESPECIAL, this.name, "specula$runInContinuation_" + this.id, "()V");
		mv.visitLabel(l18);
		mv.visitLineNumber(42, l18);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		mv.visitInsn(RETURN);
		Label l19 = new Label();
		mv.visitLabel(l19);
		mv.visitLocalVariable("this", "L" + this.name + ";", null, l2, l19, 0);
		mv.visitLocalVariable("tc", "Lspecula/runtime/ThreadContext;", null, l8, l1, 1);
		mv.visitLocalVariable("c", "Lorg/apache/commons/javaflow/Continuation;", null, l9, l1, 2);
		mv.visitMaxs(2, 4);
		mv.visitEnd();
	}

}
