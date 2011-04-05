package specula.runtime;

import java.io.IOException;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.EmptyVisitor;

import specula.bytecode.PrepareBootstrapClassAdaptor;
import asmlib.InfoClass;
import asmlib.InfoClassAdapter;
import asmlib.Type;

public class BootstrapTransformer {

	private ClassReader cr;
	private InfoClass currentClass;
	private final Type targetClass;

	
	public BootstrapTransformer(Type type, Type targetClass) throws IOException {
		// Forçar a geração de FRAMES para todas as classes, logo no inicio de toda a cadeia,
		// para evitar possíveis problemas em tudo o que se segue e que vai processar esta classe
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		new ClassReader(type.commonName()).accept(cw, ClassReader.EXPAND_FRAMES);

		cr = new ClassReader(cw.toByteArray());
		harvestInfoClass();
		
		this.targetClass = targetClass;
	}
	
	public BootstrapTransformer(byte[] classBytes, Type targetClass) {
		cr = new ClassReader(classBytes);
		harvestInfoClass();
		
		this.targetClass = targetClass;
	}
	
	private void harvestInfoClass() {
		// Popular informação da classe
		currentClass = new InfoClass(cr.getClassName(), cr.getSuperName());
		ClassVisitor cv = new EmptyVisitor();
		cv = new InfoClassAdapter(cv, currentClass);
		cr.accept(cv, 0);
	}
	
	public byte[] transform() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		ClassVisitor cv = cw;
		cv = new PrepareBootstrapClassAdaptor(cv, this.targetClass);
		cr.accept(cv, 0);
		
		final byte[] output = cw.toByteArray();
		Utils.checkBytecode(output);
		
		return output;
	}
}
