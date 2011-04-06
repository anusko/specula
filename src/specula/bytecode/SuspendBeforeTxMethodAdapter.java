package specula.bytecode;

import org.apache.commons.javaflow.Continuation;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import asmlib.Type;

public class SuspendBeforeTxMethodAdapter extends MethodAdapter implements Opcodes {

	public SuspendBeforeTxMethodAdapter(MethodVisitor mv) {
		super(mv);
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name,
			String desc) {
		
		if (opcode == INVOKESTATIC && owner.equals("jvstm/Transaction")
				&& name.equals("begin") && desc.equals("()V")) {
			
			super.visitMethodInsn(INVOKESTATIC,
					Type.fromClass(Continuation.class).asmName(),
					"suspend", "()Ljava/lang/Object;");
			super.visitInsn(POP);
		}
		
		super.visitMethodInsn(opcode, owner, name, desc);
	}

}
