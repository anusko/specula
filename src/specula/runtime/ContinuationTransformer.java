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

public class ContinuationTransformer {
	
	private static final ContinuationFilter filter = new ContinuationFilter();
	
	private ClassReader cr;
	private InfoClass currentClass;
	private final byte[] originalClass;
	private boolean enabled = true;
	
	
	public ContinuationTransformer(Type type) throws IOException {
		// Forçar a geração de FRAMES para todas as classes, logo no inicio de toda a cadeia,
		// para evitar possíveis problemas em tudo o que se segue e que vai processar esta classe
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		new ClassReader(type.commonName()).accept(cw, ClassReader.EXPAND_FRAMES);

		cr = new ClassReader(cw.toByteArray());
		this.originalClass = cw.toByteArray();
		harvestInfoClass();
		
		if (filter.filter(currentClass.name())) {
			this.enabled = false;
		}
	}
	
	public ContinuationTransformer(byte[] classBytes) {
		cr = new ClassReader(classBytes);
		this.originalClass = classBytes;
		harvestInfoClass();
		
		if (filter.filter(currentClass.name())) {
			this.enabled = false;
		}
	}
	
	private void harvestInfoClass() {
		// Popular informação da classe
		currentClass = new InfoClass(cr.getClassName(), cr.getSuperName());
		ClassVisitor cv = new EmptyVisitor();
		cv = new InfoClassAdapter(cv, currentClass);
		cr.accept(cv, 0);
	}
	
	public byte[] transform() {
		if (! this.enabled) {
			return originalClass;
		}
		
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		ClassVisitor cv = cw;
		cv = new InjectCustomRunClassAdapter(cv, currentClass);
		cr.accept(cv, 0);
		
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
