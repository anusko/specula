package specula.bytecode.samples;

import org.apache.commons.javaflow.Continuation;

import specula.ThreadContext;

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
				lastThread = tc.getThread();
			}
		} catch (NullPointerException e) { }

		if (specula$inContinuation.get() == null
				&& (lastThread == null || lastThread != Thread.currentThread())) {
			System.err.println("Starting a new ThreadContext @ " + this.getClass().getName());

			specula$inContinuation.set(specula$IN_USE);
			try {
				ThreadContext tc = ThreadContext.makeNew();
				Continuation c = Continuation.startWith(this, tc);
				do {
					if (! tc.hasTransactionAborted()) {
						if (c != null) {
							tc.setLastContinuation(c);
							c = Continuation.continueWith(c, tc);
						} else {
							return;
						}
					} else {
						c = tc.reset();
						if (c != null) {
							tc.setLastContinuation(c);
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
