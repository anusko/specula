package specula.runtime;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;

import util.Log;
import asmlib.DuplicateMethodChecker;
import asmlib.UninitializedCallChecker;

public class Utils {

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
