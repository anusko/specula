package specula.runtime;

import asmlib.Type;

public class ContinuationFilter {

	private final String[] prefixes = { "java.", "sun.", "jvstm.", "transactifier.", "specula.runtime.",
			"org.eclipse.tptp.", "com.yourkit.runtime.", "org.apache.commons.", "xtramy.", "specula.jvstm." };

		protected String[] prefixes() {
			return prefixes;
		}

		// Overload que recebe tipo numa String em formato ASM
		public boolean filter(String type) {
			return filter(Type.fromAsm(type));
		}

		public boolean filter(Type type) {
			for (String s : prefixes()) if (type.commonName().startsWith(s)) return true;
			return false;
		}
		
}
