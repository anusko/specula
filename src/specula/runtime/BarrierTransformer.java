package specula.runtime;

import java.io.IOException;

import org.apache.commons.javaflow.bytecode.transformation.asm.AsmClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.EmptyVisitor;

import specula.bytecode.InjectCustomRunClassAdapter;
import asmlib.InfoClass;
import asmlib.InfoClassAdapter;
import asmlib.Type;

public class BarrierTransformer {

	private ClassReader _cr;
	private InfoClass _currentClass;
	private final byte[] _originalClass;
	private boolean _active = true;
	
	
	public BarrierTransformer(Type type) throws IOException {
		// Forçar a geração de FRAMES para todas as classes, logo no inicio de toda a cadeia,
		// para evitar possíveis problemas em tudo o que se segue e que vai processar esta classe
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		new ClassReader(type.commonName()).accept(cw, ClassReader.EXPAND_FRAMES);

		_cr = new ClassReader(cw.toByteArray());
		_originalClass = cw.toByteArray();
		harvestInfoClass();
	}
	
	public BarrierTransformer(byte[] classBytes) {
		_cr = new ClassReader(classBytes);
		_originalClass = classBytes;
		harvestInfoClass();
	}
	
	private void harvestInfoClass() {
		// Popular informação da classe
		_currentClass = new InfoClass(_cr.getClassName(), _cr.getSuperName());
		ClassVisitor cv = new EmptyVisitor();
		cv = new InfoClassAdapter(cv, _currentClass);
		_cr.accept(cv, 0);
	}
	
	public byte[] transform() {
		if (! _active) {
			return _originalClass;
		}
		
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		ClassVisitor cv = cw;
		_cr.accept(cv, 0);
		
		final byte[] output = new AsmClassTransformer().transform(cw.toByteArray());
		
//		if (specula.SpeculaClassLoader.PRINTCLASS) asmlib.Util.printClass(output);
//		
//		if (specula.SpeculaClassLoader.WRITECLASS) {
//			try {
//				java.io.FileOutputStream fos = new java.io.FileOutputStream(
//					"output/" + cr.getClassName().replace('/', '.') + ".class");
//				fos.write(output);
//				fos.close();
//			} catch (java.io.FileNotFoundException e) { throw new Error(e);	}
//			  catch (java.io.IOException e) { throw new Error(e); }
//		}
		
		Utils.checkBytecode(output);
		
		return output;
	}
	
}
