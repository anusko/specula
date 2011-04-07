package specula.bytecode;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import asmlib.InfoClass;
import asmlib.InfoMethod;
import asmlib.Util;

/**
 * Modifica classes que implementem um método "public <final> void run()".
 * 
 * Renomeia o método "public <final> void run()" para
 * "private void specula$run()" e cria o método "public void run()"
 * com código que pode ser visto na classe specula.bytecode.samples.RunnableSample
 * 
 * De notar que a flag "final" cai. É "hackish" mas, IMHO, não tem grande problema
 * pois o código original cumpre a especificação.
 * 
 * -----
 * NOTA: é necessário modificar mesmo as classes que não implementam a interface
 * java.lang.Runnable pois classes que a concretizam podem herdar desta.
 * Exemplo:
 * 
 * class C1 {
 *   public void run() { ... }
 * }
 * 
 * class C2 extends C1 implements Runnable { }
 * 
 * A classe C2 é válida.
 * -----
 * 
 * TODO
 * 1) (Optimização) Evitar injectar o método run() em todas as classes.
 *
 */
public class InjectCustomRunClassAdapter extends ClassAdapter implements Opcodes {

	private boolean _needsModification = false;

	private MethodVisitor _clinitVisitor;
	private MethodVisitor _runVisitor;

	private final InfoClass _currentClass;


	public InjectCustomRunClassAdapter(ClassVisitor cv, InfoClass infoClass) {
		super(cv);
		assert (infoClass != null);

		_currentClass = infoClass;

		InfoMethod im = infoClass.getMethod("run", "()V");
		if (im != null) {
			boolean isAbstract = Util.checkFlag(
					im.access(),
					ACC_ABSTRACT);
			boolean isStatic = Util.checkFlag(
					im.access(),
					ACC_STATIC);

			_needsModification = ! (isAbstract || isStatic);
		}
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces);

		if (_needsModification) {
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
		// temos que inicializar os fields no clinit
		if (_needsModification 	&& Util.checkFlag(access, ACC_STATIC)
				&& name.equals("<clinit>") && desc.equals("()V")) {
			assert (_clinitVisitor == null);

			_clinitVisitor = super.visitMethod(access, name, desc, signature, exceptions);
			clinit(_clinitVisitor, false);
			return _clinitVisitor;
		}

		if (_needsModification && name.equals("run") && desc.equals("()V")) {
			assert (_runVisitor == null);

			// remover a flag final... na prática é para por só public. Hacky!
			_runVisitor = super.visitMethod(ACC_PUBLIC, name, desc, signature, exceptions);
			// rename the method
			MethodVisitor mv = 
				super.visitMethod(ACC_PRIVATE, "specula$run", desc, signature, exceptions);
			return new MethodAdapter(mv) {

				@Override
				public void visitMethodInsn(int opcode, String owner,
						String name, String desc) {

					if (opcode == INVOKESPECIAL
							&& owner.equals(_currentClass.name().asmName())
							&& name.equals("run") && desc.equals("()V")) {

						super.visitMethodInsn(opcode, owner, "specula$run", desc);
					} else {
						super.visitMethodInsn(opcode, owner, name, desc);
					}
					
				}	

			};
		}

		return super.visitMethod(access, name, desc, signature, exceptions);	
	}

	@Override
	public void visitEnd() {
		if (_needsModification) {
			if (_clinitVisitor == null) {
				_clinitVisitor = super.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
				clinit(_clinitVisitor, true);
			}

			assert (_runVisitor != null);
			run(_runVisitor);
		}

		super.visitEnd();
	}

	private void clinit(MethodVisitor mv, boolean newMethod) {
		mv.visitCode();
		Label l0 = new Label();
		mv.visitLabel(l0);
		mv.visitLineNumber(9, l0);
		mv.visitTypeInsn(NEW, "java/lang/Object");
		mv.visitInsn(DUP);
		mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
		mv.visitFieldInsn(PUTSTATIC, _currentClass.name().asmName(), "specula$IN_USE", "Ljava/lang/Object;");
		Label l1 = new Label();
		mv.visitLabel(l1);
		mv.visitLineNumber(12, l1);
		mv.visitTypeInsn(NEW, "java/lang/ThreadLocal");
		mv.visitInsn(DUP);
		mv.visitMethodInsn(INVOKESPECIAL, "java/lang/ThreadLocal", "<init>", "()V");
		Label l2 = new Label();
		mv.visitLabel(l2);
		mv.visitLineNumber(11, l2);
		mv.visitFieldInsn(PUTSTATIC, _currentClass.name().asmName(), "specula$inContinuation", "Ljava/lang/ThreadLocal;");
		if (newMethod) {
			Label l3 = new Label();
			mv.visitLabel(l3);
			mv.visitLineNumber(7, l3);
			mv.visitInsn(RETURN);
		}
		mv.visitMaxs(2, 0);
		mv.visitEnd();
	}

	private void run(MethodVisitor mv) {
		mv.visitCode();
		Label l0 = new Label();
		Label l1 = new Label();
		Label l2 = new Label();
		mv.visitTryCatchBlock(l0, l1, l2, "java/lang/NullPointerException");
		Label l3 = new Label();
		Label l4 = new Label();
		mv.visitTryCatchBlock(l3, l4, l4, null);
		Label l5 = new Label();
		mv.visitLabel(l5);
		mv.visitLineNumber(16, l5);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitTypeInsn(INSTANCEOF, "java/lang/Runnable");
		Label l6 = new Label();
		mv.visitJumpInsn(IFNE, l6);
		Label l7 = new Label();
		mv.visitLabel(l7);
		mv.visitLineNumber(17, l7);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitMethodInsn(INVOKESPECIAL, _currentClass.name().asmName(), "specula$run", "()V");
		Label l8 = new Label();
		mv.visitLabel(l8);
		mv.visitLineNumber(18, l8);
		mv.visitInsn(RETURN);
		mv.visitLabel(l6);
		mv.visitLineNumber(21, l6);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		mv.visitInsn(ACONST_NULL);
		mv.visitVarInsn(ASTORE, 1);
		mv.visitLabel(l0);
		mv.visitLineNumber(23, l0);
		mv.visitMethodInsn(INVOKESTATIC, "org/apache/commons/javaflow/Continuation", "getContext", "()Ljava/lang/Object;");
		mv.visitTypeInsn(CHECKCAST, "specula/runtime/ThreadContext");
		mv.visitVarInsn(ASTORE, 2);
		Label l9 = new Label();
		mv.visitLabel(l9);
		mv.visitLineNumber(24, l9);
		mv.visitVarInsn(ALOAD, 2);
		Label l10 = new Label();
		mv.visitJumpInsn(IFNULL, l10);
		Label l11 = new Label();
		mv.visitLabel(l11);
		mv.visitLineNumber(25, l11);
		mv.visitVarInsn(ALOAD, 2);
		mv.visitMethodInsn(INVOKEVIRTUAL, "specula/runtime/ThreadContext", "getStartingThread", "()Ljava/lang/Thread;");
		mv.visitVarInsn(ASTORE, 1);
		mv.visitLabel(l1);
		mv.visitFrame(Opcodes.F_APPEND,2, new Object[] {"java/lang/Thread", "specula/runtime/ThreadContext"}, 0, null);
		mv.visitJumpInsn(GOTO, l10);
		mv.visitLabel(l2);
		mv.visitLineNumber(27, l2);
		mv.visitFrame(Opcodes.F_FULL, 2, new Object[] {_currentClass.name().asmName(), "java/lang/Thread"}, 1, new Object[] {"java/lang/NullPointerException"});
		mv.visitVarInsn(ASTORE, 2);
		mv.visitLabel(l10);
		mv.visitLineNumber(29, l10);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		mv.visitFieldInsn(GETSTATIC, _currentClass.name().asmName(), "specula$inContinuation", "Ljava/lang/ThreadLocal;");
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/ThreadLocal", "get", "()Ljava/lang/Object;");
		Label l12 = new Label();
		mv.visitJumpInsn(IFNONNULL, l12);
		Label l13 = new Label();
		mv.visitLabel(l13);
		mv.visitLineNumber(30, l13);
		mv.visitVarInsn(ALOAD, 1);
		Label l14 = new Label();
		mv.visitJumpInsn(IFNULL, l14);
		mv.visitVarInsn(ALOAD, 1);
		mv.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;");
		mv.visitJumpInsn(IF_ACMPEQ, l12);
		mv.visitLabel(l14);
		mv.visitLineNumber(31, l14);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		mv.visitFieldInsn(GETSTATIC, "java/lang/System", "err", "Ljava/io/PrintStream;");
		mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
		mv.visitInsn(DUP);
		mv.visitLdcInsn("Starting a new ThreadContext @ ");
		mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V");
		mv.visitVarInsn(ALOAD, 0);
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;");
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;");
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V");
		mv.visitLabel(l3);
		mv.visitLineNumber(33, l3);
		mv.visitFieldInsn(GETSTATIC, _currentClass.name().asmName(), "specula$inContinuation", "Ljava/lang/ThreadLocal;");
		mv.visitFieldInsn(GETSTATIC, _currentClass.name().asmName(), "specula$IN_USE", "Ljava/lang/Object;");
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/ThreadLocal", "set", "(Ljava/lang/Object;)V");
		Label l15 = new Label();
		mv.visitLabel(l15);
		mv.visitLineNumber(34, l15);
		mv.visitTypeInsn(NEW, "specula/runtime/ThreadContext");
		mv.visitInsn(DUP);
		mv.visitMethodInsn(INVOKESPECIAL, "specula/runtime/ThreadContext", "<init>", "()V");
		mv.visitVarInsn(ASTORE, 2);
		Label l16 = new Label();
		mv.visitLabel(l16);
		mv.visitLineNumber(35, l16);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitVarInsn(ALOAD, 2);
		mv.visitMethodInsn(INVOKESTATIC, "org/apache/commons/javaflow/Continuation", "startWith", "(Ljava/lang/Runnable;Ljava/lang/Object;)Lorg/apache/commons/javaflow/Continuation;");
		mv.visitVarInsn(ASTORE, 3);
		Label l17 = new Label();
		mv.visitLabel(l17);
		mv.visitLineNumber(37, l17);
		mv.visitFrame(Opcodes.F_APPEND,2, new Object[] {"specula/runtime/ThreadContext", "org/apache/commons/javaflow/Continuation"}, 0, null);
		mv.visitVarInsn(ALOAD, 2);
		mv.visitMethodInsn(INVOKEVIRTUAL, "specula/runtime/ThreadContext", "hasTxAborted", "()Z");
		Label l18 = new Label();
		mv.visitJumpInsn(IFNE, l18);
		Label l19 = new Label();
		mv.visitLabel(l19);
		mv.visitLineNumber(38, l19);
		mv.visitVarInsn(ALOAD, 3);
		Label l20 = new Label();
		mv.visitJumpInsn(IFNULL, l20);
		Label l21 = new Label();
		mv.visitLabel(l21);
		mv.visitLineNumber(39, l21);
		mv.visitVarInsn(ALOAD, 2);
		mv.visitVarInsn(ALOAD, 3);
		mv.visitMethodInsn(INVOKEVIRTUAL, "specula/runtime/ThreadContext", "setLastContinuation", "(Lorg/apache/commons/javaflow/Continuation;)V");
		Label l22 = new Label();
		mv.visitLabel(l22);
		mv.visitLineNumber(40, l22);
		mv.visitVarInsn(ALOAD, 3);
		mv.visitVarInsn(ALOAD, 2);
		mv.visitMethodInsn(INVOKESTATIC, "org/apache/commons/javaflow/Continuation", "continueWith", "(Lorg/apache/commons/javaflow/Continuation;Ljava/lang/Object;)Lorg/apache/commons/javaflow/Continuation;");
		mv.visitVarInsn(ASTORE, 3);
		Label l23 = new Label();
		mv.visitLabel(l23);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		mv.visitJumpInsn(GOTO, l17);
		mv.visitLabel(l18);
		mv.visitLineNumber(45, l18);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		mv.visitVarInsn(ALOAD, 2);
		mv.visitMethodInsn(INVOKEVIRTUAL, "specula/runtime/ThreadContext", "retry", "()Lorg/apache/commons/javaflow/Continuation;");
		mv.visitVarInsn(ASTORE, 3);
		Label l24 = new Label();
		mv.visitLabel(l24);
		mv.visitLineNumber(47, l24);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		mv.visitJumpInsn(GOTO, l17);
		mv.visitLabel(l4);
		mv.visitLineNumber(48, l4);
		mv.visitFrame(Opcodes.F_FULL, 2, new Object[] {_currentClass.name().asmName(), "java/lang/Thread"}, 1, new Object[] {"java/lang/Throwable"});
		mv.visitVarInsn(ASTORE, 4);
		Label l25 = new Label();
		mv.visitLabel(l25);
		mv.visitLineNumber(49, l25);
		mv.visitFieldInsn(GETSTATIC, _currentClass.name().asmName(), "specula$inContinuation", "Ljava/lang/ThreadLocal;");
		mv.visitInsn(ACONST_NULL);
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/ThreadLocal", "set", "(Ljava/lang/Object;)V");
		Label l26 = new Label();
		mv.visitLabel(l26);
		mv.visitLineNumber(50, l26);
		mv.visitVarInsn(ALOAD, 4);
		mv.visitInsn(ATHROW);
		mv.visitLabel(l20);
		mv.visitLineNumber(49, l20);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		mv.visitFieldInsn(GETSTATIC, _currentClass.name().asmName(), "specula$inContinuation", "Ljava/lang/ThreadLocal;");
		mv.visitInsn(ACONST_NULL);
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/ThreadLocal", "set", "(Ljava/lang/Object;)V");
		Label l27 = new Label();
		mv.visitJumpInsn(GOTO, l27);
		mv.visitLabel(l12);
		mv.visitLineNumber(52, l12);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		mv.visitFieldInsn(GETSTATIC, "java/lang/System", "err", "Ljava/io/PrintStream;");
		mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
		mv.visitInsn(DUP);
		mv.visitLdcInsn("Using an old ThreadContext @ ");
		mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V");
		mv.visitVarInsn(ALOAD, 0);
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;");
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;");
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V");
		Label l28 = new Label();
		mv.visitLabel(l28);
		mv.visitLineNumber(53, l28);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitMethodInsn(INVOKESPECIAL, _currentClass.name().asmName(), "specula$run", "()V");
		mv.visitLabel(l27);
		mv.visitLineNumber(55, l27);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		mv.visitInsn(RETURN);
		Label l29 = new Label();
		mv.visitLabel(l29);
		mv.visitLocalVariable("this", "L" + _currentClass.name().asmName() + ";", null, l5, l29, 0);
		mv.visitLocalVariable("lastThread", "Ljava/lang/Thread;", null, l0, l29, 1);
		mv.visitLocalVariable("tc", "Lspecula/runtime/ThreadContext;", null, l9, l2, 2);
		mv.visitLocalVariable("tc", "Lspecula/runtime/ThreadContext;", null, l16, l4, 2);
		mv.visitLocalVariable("c", "Lorg/apache/commons/javaflow/Continuation;", null, l17, l4, 3);
		mv.visitMaxs(4, 5);
		mv.visitEnd();
	}

}
