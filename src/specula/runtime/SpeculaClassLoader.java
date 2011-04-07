package specula.runtime;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.commons.javaflow.bytecode.BytecodeClassLoader;
import org.objectweb.asm.ClassReader;

import util.Log;
import util.StringList;
import asmlib.InstrumentationException;
import asmlib.Type;
import asmlib.Util;

public final class SpeculaClassLoader extends BytecodeClassLoader {
	
	private static final String BOOTSTRAP_CLASS_NAME = "specula.bootstrap.BootstrapRunnable";

	private final String _bootClassName;

	//	// PrintClass: Imprimir classes geradas pelo SpeculationTransformer
	//	public static boolean PRINTCLASS = true;
	//	// WriteClass: Escrever para o disco classes geradas pelo SpeculationTransformer
	//	public static boolean WRITECLASS = false;
	//	// Singleton
	//	private static SpeculaClassLoader instance;

	private SpeculaClassLoader(String bootClass) {
		_bootClassName = bootClass;
		
		jvstm.Transaction.setTransactionFactory(new specula.jvstm.SpeculaTransactionFactory());
	}

	@Override
	protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		//Log.debug("TransactifierClassLoader.loadClass(" + className + ")");

		String mode = "\033[32m[ NormalLoad ]\033[39m";
		try {
			Class<?> c = findLoadedClass(name);
			if (c != null) return c;

			// TODO implementar um filter em condições
//			if (name.startsWith("org.") || name.startsWith("java.")) {
//				return getParent().loadClass(name);
//			}

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
			Log.debug("JVM won't let me load a custom '" + className + "'. Loading the non-modified version.");
			return getParent().loadClass(className);
		}
	}

	protected byte[] getClassBytes(Type type) throws IOException {
		byte[] byteArr = new ClassReader(type.commonName()).b;
		
		if (type.commonName().equals(BOOTSTRAP_CLASS_NAME)) {	
			byteArr = new BootstrapTransformer(byteArr, Type.fromCommon(_bootClassName)).transform();
		}
		byteArr = new ContinuationTransformer(byteArr).transform();
		
		//if (type.commonName().startsWith("continuationsTests.")) Util.printClass(byteArr);
		
		return byteArr;
	}

	public static void main(String[] argv) throws Throwable {
		StringList args = new StringList(argv);
		if (args.size() == 0) {
			System.err.println(SpeculaClassLoader.class.getName() + "\n\tSyntax: Class [<arg0> ... <argn>]");
			return;
		}
		
		SpeculaClassLoader loader = new SpeculaClassLoader(args.pollFirst());

		Log.debug(loader.getClass().getName() + " (Args: " + args + ")");
		
		Method target = loader.loadClass(BOOTSTRAP_CLASS_NAME, true).getMethod("main", String[].class);
		target.setAccessible(true);
		Log.debug("Trying to invoke main...");
		try {
			target.invoke(null, new Object[] { args.toArray() });
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
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

}
