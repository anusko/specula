package specula.runtime;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.javaflow.Continuation;

import specula.jvstm.SpeculaTopLevelTransaction;

public class ThreadContext {
	
	private List<SpeculaTopLevelTransaction> transactions;
	private Continuation lastContinuation;
	private SpeculaTopLevelTransaction abortedTx;
	
	private final Thread startingThread;
	
	
	public ThreadContext() {
		this.transactions = new LinkedList<SpeculaTopLevelTransaction>();
		
		this.startingThread = Thread.currentThread();
	}
	
	public Thread getStartingThread() {
		return this.startingThread;
	}
	
	public void addTransaction(SpeculaTopLevelTransaction tx) {
		assert (startingThread == Thread.currentThread());
		
		this.transactions.add(tx);
	}
	
	public Collection<SpeculaTopLevelTransaction> getTransactions() {
		assert (startingThread == Thread.currentThread());
		
		return Collections.unmodifiableCollection(transactions);
	}
	
	public Continuation getLastContinuation() {
		return this.lastContinuation;
	}
	
	public void setLastContinuation(Continuation c) {
		this.lastContinuation = c;
	}
	
	public boolean hasTxAborted() {
		return (abortedTx != null) ? true : false;
	}
	
	public void setAbortedTx(jvstm.Transaction tx) {
		assert (abortedTx == null);
		
		((SpeculaTopLevelTransaction) tx).getResumeAt();
	}
	
	public Continuation retry() {
		assert (abortedTx != null);
		
		Continuation c = abortedTx.getResumeAt();
		this.abortedTx = null;
		
		return Continuation.continueWith(c, this);
	}
	
}
