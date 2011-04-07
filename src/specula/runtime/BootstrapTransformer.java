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

	private ClassReader _cr;
	private InfoClass _currentClass;
	private final Type _targetClass;

	
	public BootstrapTransformer(Type type, Type targetClass) throws IOException {
		// Forçar a geração de FRAMES para todas as classes, logo no inicio de toda a cadeia,
		// para evitar possíveis problemas em tudo o que se segue e que vai processar esta classe
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		new ClassReader(type.commonName()).accept(cw, ClassReader.EXPAND_FRAMES);

		_cr = new ClassReader(cw.toByteArray());
		harvestInfoClass();
		
		_targetClass = targetClass;
	}
	
	public BootstrapTransformer(byte[] classBytes, Type targetClass) {
		_cr = new ClassReader(classBytes);
		harvestInfoClass();
		
		_targetClass = targetClass;
	}
	
	private void harvestInfoClass() {
		// Popular informação da classe
		_currentClass = new InfoClass(_cr.getClassName(), _cr.getSuperName());
		ClassVisitor cv = new EmptyVisitor();
		cv = new InfoClassAdapter(cv, _currentClass);
		_cr.accept(cv, 0);
	}
	
	public byte[] transform() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		ClassVisitor cv = cw;
		cv = new PrepareBootstrapClassAdaptor(cv, _targetClass);
		_cr.accept(cv, 0);
		
		final byte[] output = cw.toByteArray();
		Utils.checkBytecode(output);
		
		return output;
	}
}
