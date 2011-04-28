package specula.core;

import org.apache.commons.javaflow.Continuation;

public abstract class ThreadContext {
	
	private static ThreadContextFactory _factory;
	
	
	public static void setThreadContextFactory(ThreadContextFactory factory) {
		_factory = factory;
	}
	
	public static ThreadContext makeNew() {
		if (_factory == null) {
			throw new Error("No factory setted.");
		}
		
		return _factory.makeNew();
	}
	
	public static ThreadContext getCurrent() {
		return (ThreadContext) Continuation.getContext();
	}
	
	public abstract Thread getThread();
	
	public abstract Continuation getLastContinuation();
	
	public abstract void setLastContinuation(Continuation c);
	
	public abstract void setAbortedTransaction(SpeculaTransaction tx);

	public abstract boolean isTheAbortedTransaction(SpeculaTransaction tx);
	
	public abstract boolean hasTransactionAborted();
	
	public abstract Continuation reset();
	
}
