package specula.bootstrap;


public final class BootstrapRunnable implements Runnable {

	@SuppressWarnings("unused")
	private final String[] _args;
	
	
	public BootstrapRunnable(String[] args) {
		_args = args;
	}

	@Override
	public void run() {
		/*
		 * Sim, é para estar vazio! É modificado dinamicamente já que
		 * não se pode usar reflection.
		 */
	}
	
	public static void main(String[] args) {
		new BootstrapRunnable(args).run();
	}
	
}
