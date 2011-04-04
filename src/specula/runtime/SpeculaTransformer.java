package specula.runtime;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.EmptyVisitor;
import org.objectweb.asm.util.CheckClassAdapter;

import util.Log;
import asmlib.DuplicateMethodChecker;
import asmlib.InfoClass;
import asmlib.InfoClassAdapter;
import asmlib.Type;
import asmlib.UninitializedCallChecker;

public class SpeculaTransformer {
	
	private ClassReader cr;
	private InfoClass currentClass;

	
	public SpeculaTransformer(Type type) throws IOException {
		// Forçar a geração de FRAMES para todas as classes, logo no inicio de toda a cadeia,
		// para evitar possíveis problemas em tudo o que se segue e que vai processar esta classe
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		new ClassReader(type.commonName()).accept(cw, ClassReader.EXPAND_FRAMES);

		cr = new ClassReader(cw.toByteArray());
		harvestInfoClass();
	}
	
	public SpeculaTransformer(byte[] classBytes) {
		cr = new ClassReader(classBytes);
		harvestInfoClass();
	}
	
	private void harvestInfoClass() {
		// Popular informação da classe
		currentClass = new InfoClass(cr.getClassName(), cr.getSuperName());
		ClassVisitor cv = new EmptyVisitor();
		cv = new InfoClassAdapter(cv, currentClass);
		cr.accept(cv, 0);
	}
	
	public byte[] transform() {
		byte[] output = null;
		
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		
		ClassVisitor cv = cw;
		cr.accept(cv, 0);
		
		output = cw.toByteArray();
		
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
		
		checkBytecode(output);
		
		return output;
	}
	
	public static void checkBytecode(byte[] output) {
		StringWriter sw = new StringWriter();
		try {
			ClassReader cr = new ClassReader(output);
			CheckClassAdapter.verify(cr, false, new PrintWriter(sw));
			DuplicateMethodChecker.verify(cr, new PrintWriter(sw));
			UninitializedCallChecker.verify(cr, new PrintWriter(sw));
			if (sw.toString().length() != 0) {
				Log.debug("Error(s) were detected on the output bytecode:\n" + sw.toString());
			} else {
				//Log.debug("Bytecode verification successful!");
			}
		} catch (Exception e) {
			if (sw.toString().length() != 0) {
				Log.debug("Error(s) were detected on the output bytecode:\n" + sw.toString());
			} else {
				Log.debug("Exception occurred during Bytecode checking: ");
				e.printStackTrace();
			}
		}
	}
	
}
