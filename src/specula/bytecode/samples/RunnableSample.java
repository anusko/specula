package specula.bytecode.samples;

import org.apache.commons.javaflow.Continuation;

import specula.jvstm.ThreadContext;

public abstract class RunnableSample implements Runnable {

	private static final Object specula$IN_USE = new Object();

	private static final ThreadLocal<Object> specula$inContinuation =
		new ThreadLocal<Object>();


	public void run() {
		if (! (this instanceof Runnable)) {
			specula$run();
			return;
		}

		Thread lastThread = null;
		try {
			ThreadContext tc = (ThreadContext) Continuation.getContext();
			if (tc != null) {
				lastThread = tc.getStartingThread();
			}
		} catch (NullPointerException e) { }

		if (specula$inContinuation.get() == null
				&& (lastThread == null || lastThread != Thread.currentThread())) {
			System.err.println("Starting a new ThreadContext @ " + this.getClass().getName());
			try {
				specula$inContinuation.set(specula$IN_USE);
				ThreadContext tc = new ThreadContext();
				Continuation c = Continuation.startWith(this, tc);
				do {
					if (! tc.hasTxAborted()) {
						if (c != null) {
							tc.setLastContinuation(c);
							c = Continuation.continueWith(c, tc);
						} else {
							break;
						}
					} else {
						c = tc.getResumePoint();
						if (c != null){
							c = Continuation.continueWith(c, tc);
						} else {
							c = Continuation.startWith(this, tc);
						}
					}
				} while (true);
			} finally {
				specula$inContinuation.set(null);
			}
		} else {
			System.err.println("Using an old ThreadContext @ " + this.getClass().getName());
			specula$run();
		}
	}

	private void specula$run() {
		// the original run() method
	}

}
