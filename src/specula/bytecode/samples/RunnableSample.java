package specula.bytecode.samples;

import org.apache.commons.javaflow.Continuation;

import specula.runtime.ThreadContext;

public abstract class RunnableSample implements Runnable {
	
	private static final Object specula$IN_USE = new Object();

	private static final ThreadLocal<Object> specula$inContinuation =
		new ThreadLocal<Object>();

	public void run() {
		if (! (this instanceof Runnable)) {
			specula$runInContinuation_id();
			return;
		}
		
		if (specula$inContinuation.get() == null) {
			try {
				specula$inContinuation.set(specula$IN_USE);
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
				specula$inContinuation.set(null);
			}
		} else {
			specula$runInContinuation_id();
		}
	}

	private void specula$runInContinuation_id() {
		// the original run() method
	}

}
