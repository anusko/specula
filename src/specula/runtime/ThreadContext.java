package specula.runtime;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.javaflow.Continuation;

import specula.jvstm.SpeculaTopLevelTransaction;

public class ThreadContext {
	
	private List<SpeculaTopLevelTransaction> _transactions;
	private Continuation _lastContinuation;
	private SpeculaTopLevelTransaction _abortedTx;
	
	private final Thread _startingThread;
	
	
	public ThreadContext() {
		_transactions = new LinkedList<SpeculaTopLevelTransaction>();
		
		_startingThread = Thread.currentThread();
	}
	
	public Thread getStartingThread() {
		return _startingThread;
	}
	
	public void addTransaction(SpeculaTopLevelTransaction tx) {
		assert (_startingThread == Thread.currentThread());
		
		_transactions.add(tx);
	}
	
	public Collection<SpeculaTopLevelTransaction> getTransactions() {
		assert (_startingThread == Thread.currentThread());
		
		return Collections.unmodifiableCollection(_transactions);
	}
	
	public Continuation getLastContinuation() {
		return _lastContinuation;
	}
	
	public void setLastContinuation(Continuation c) {
		_lastContinuation = c;
	}
	
	public boolean hasTxAborted() {
		return (_abortedTx != null) ? true : false;
	}
	
	public void setAbortedTx(jvstm.Transaction tx) {
		assert (_abortedTx == null);
		
		((SpeculaTopLevelTransaction) tx).getResumeAt();
	}
	
	public Continuation retry() {
		assert (_abortedTx != null);
		
		Continuation c = _abortedTx.getResumeAt();
		_abortedTx = null;
		
		return Continuation.continueWith(c, this);
	}
	
}
