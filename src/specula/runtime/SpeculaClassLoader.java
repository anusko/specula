package specula.runtime;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.apache.commons.javaflow.Continuation;
import org.apache.commons.javaflow.bytecode.BytecodeClassLoader;
import org.apache.commons.javaflow.bytecode.transformation.ResourceTransformer;
import org.apache.commons.javaflow.bytecode.transformation.asm.AsmClassTransformer;
import org.objectweb.asm.ClassReader;

import util.Log;
import util.StringList;
import asmlib.InstrumentationException;
import asmlib.Type;

public final class SpeculaClassLoader extends BytecodeClassLoader {

	private final ResourceTransformer transformer;
	
//	// PrintClass: Imprimir classes geradas pelo SpeculationTransformer
//	public static boolean PRINTCLASS = true;
//	// WriteClass: Escrever para o disco classes geradas pelo SpeculationTransformer
//	public static boolean WRITECLASS = false;
//	// Singleton
//	private static SpeculaClassLoader CLASSLOADER;

	public SpeculaClassLoader(final ResourceTransformer pTransformer) {
		this.transformer = pTransformer;
	}

	protected byte[] transform(final byte[] oldClass) throws IOException {
		final byte[] newClass = transformer.transform(oldClass);
		// CheckClassAdapter.verify(new ClassReader(newClass), true);
		return newClass;
	}

	@Override
	protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		//Log.debug("TransactifierClassLoader.loadClass(" + className + ")");

		String mode = "\033[32m[ NormalLoad ]\033[39m";
		try {
			Class<?> c = findLoadedClass(name);
			if (c != null) return c;

			if (name.startsWith("org")) {
				return getParent().loadClass(name);
			}

			c = findClass(name);
			if (resolve) resolveClass(c);
			return c;

		} finally {
			Log.debug(mode + " Loading " + name);
		}
	}

	@Override
	protected Class<?> findClass(String className) throws ClassNotFoundException {
		//Log.debug("TransactifierClassLoader.findClass(" + className + ")");

		byte[] classFile;
		try {
			classFile = getClassBytes(Type.fromCommon(className));
		} catch (IOException e) {
			throw new ClassNotFoundException("Could not load class " + className, e);
		} catch (InstrumentationException e) {
			throw new ClassNotFoundException("Could not load class " + className, e);
		}

		try {
			return defineClass(className, classFile, 0, classFile.length);
		} catch (SecurityException e) {
			Log.debug("JVM won't let me load a custom '" + className + "'. Loading non-transactified version.");
			return getParent().loadClass(className);
		}
	}

	protected byte[] getClassBytes(Type className) throws IOException {
		return transform(new ClassReader(className.commonName()).b);
	}

	public static void main(String[] argv) throws Throwable {
		SpeculaClassLoader loader = new SpeculaClassLoader(new AsmClassTransformer());

		StringList args = new StringList(argv);
		if (args.size() == 0) {
			System.err.println(loader.getClass().getName() + "\n\tSyntax: Class [<arg0> ... <argn>]");
			return;
		}

		Log.debug(loader.getClass().getName() + " (Args: " + args + ")");

		Method m = loader.loadClass(args.pollFirst(), true).getMethod("main", String[].class);
		m.setAccessible(true);
		new BootstrapRunnable(m, new Object[] { args.toArray() }).run();
	}
	
//	public static void main(String[] argArray) throws Throwable {
//		SpeculaClassLoader loader = new SpeculaClassLoader();
//		
//		Options opts = new Options();
//		OptionGroup group = new OptionGroup();
//		opts.addOptionGroup(group);
//		
//		group.addOption(new Option("p", "print", false, "Print the generated class bytecode (default)."));
//		group.addOption(new Option("w", "write", false, "Write to disk the generated class."));
//		
//		BasicParser parser = new BasicParser();
//		CommandLine cl = parser.parse(opts, argArray);
//		
//		if (cl.hasOption("w")) {
//			PRINTCLASS = false;
//			WRITECLASS = true;
//		}
//		
//		StringList args = new StringList(cl.getArgs());
//		if (args.size() == 0) {
//			System.err.println(loader.getClass().getName() + "\n\tSyntax: Class [<arg0> ... <argn>]");
//			for (Object o : opts.getOptions()) {
//				System.out.println(o);
//			}
//			return;
//		}
//
//		Log.debug(loader.getClass().getName() + " (Args: " + args + ")");
//
//		Method m = loader.loadClass(args.pollFirst(), true).getMethod("main", String[].class);
//		m.setAccessible(true);
//		Log.debug("Trying to invoke main...");
//		try {
//			m.invoke(null, new Object[] { args.toArray() });
//		} catch (InvocationTargetException e) {
//			throw e.getCause();
//		}
//	}

	/**
	 * Em princípio esta classe é só para fazer bootstrap à main
	 * pretendida.
	 * 
	 */
	private static class BootstrapRunnable implements Runnable {

		private static boolean used = false;

		private final Method method;
		private final Object[] args;
		private boolean inContinuation = false;


		public BootstrapRunnable(Method m, Object[] args) {
			assert Modifier.isStatic(m.getModifiers());

			this.method = m;
			this.args = args;
		}

		@Override
		public synchronized void run() {
			if (used) {
				throw new Error(this.getClass().getName() + " can only be used once.");
			}

			if (! this.inContinuation) {
				try {
					this.inContinuation = true;
					ThreadContext tc = new ThreadContext();
					Continuation c = Continuation.startWith(this, tc);
					do {
						if (! tc.hasTxAborted()) {
							if (c != null) {
								c = Continuation.continueWith(c, tc);
							} else {
								break;
							}
						} else {
							c = tc.retry();
						}
					} while (true);
				} finally {
					this.inContinuation = false;
				}
			} else {
				used = true;
				try {
					this.method.invoke(null, this.args);
				} catch (IllegalArgumentException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvocationTargetException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}

	}


}
