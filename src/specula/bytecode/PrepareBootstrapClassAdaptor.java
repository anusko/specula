package specula.bytecode;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import asmlib.Type;

/**
 * O bootstrap não pode ser feito através do uso de reflection, portanto temos que
 * gerar dinamicamente a chamada ao método pretendido.
 * 
 * -----
 * NOTA: esta classe não faz verificações à classe de bootstrap. Esta tem que
 * ser publica, concretizar a interface java.lang.Runnable e não ser abstracta.
 *
 */
public class PrepareBootstrapClassAdaptor extends ClassAdapter implements Opcodes {

	private final String targetClassName;
	private String bootstrapRunnableClassName;


	public PrepareBootstrapClassAdaptor(ClassVisitor cv, Type targetClass) {
		super(cv);
		this.targetClassName = targetClass.asmName();
	}
	
	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces);
		this.bootstrapRunnableClassName = name;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		if (name.equals("run")) {
			
			MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
			mv.visitCode();
			Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitLineNumber(18, l0);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitFieldInsn(GETFIELD, this.bootstrapRunnableClassName, "args", "[Ljava/lang/String;");
			mv.visitMethodInsn(INVOKESTATIC, this.targetClassName, "main", "([Ljava/lang/String;)V");
			Label l1 = new Label();
			mv.visitLabel(l1);
			mv.visitLineNumber(19, l1);
			mv.visitInsn(RETURN);
			Label l2 = new Label();
			mv.visitLabel(l2);
			mv.visitLocalVariable("this", "L" + this.bootstrapRunnableClassName + ";", null, l0, l2, 0);
			mv.visitMaxs(1, 1);
			mv.visitEnd();
			
			return mv;
		} else {
			return super.visitMethod(access, name, desc, signature, exceptions);
		}
	}

}
