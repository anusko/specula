package specula.jvstm;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.javaflow.Continuation;


public class ThreadContext {
	
	//final ReentrantLock lock = new ReentrantLock(true);
	
	private final List<SpeculaTopLevelTransaction> _transactions;
	private Continuation _lastContinuation;
	private final AtomicReference< SpeculaTopLevelTransaction> _abortedTx;
	
	private final Thread _startingThread;
	
	
	public ThreadContext() {
		_transactions = new LinkedList<SpeculaTopLevelTransaction>();
		_abortedTx = new AtomicReference<SpeculaTopLevelTransaction>(null);
		
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
		
		return _transactions;
	}
	
	public Continuation getLastContinuation() {
		return _lastContinuation;
	}
	
	public void setLastContinuation(Continuation c) {
		_lastContinuation = c;
	}
	
	public boolean hasTxAborted() {
		return ! (_abortedTx.compareAndSet(null, null));
	}
	
	public void setAbortedTx(SpeculaTopLevelTransaction tx) {
		if (_abortedTx.compareAndSet(null, tx)) {
			//tx.markForAbortion();
		}
	}
	
	public Continuation getResumePoint() {
		assert (hasTxAborted());
		
		SpeculaTopLevelTransaction tx = _abortedTx.get();
		Continuation c = tx._resumeAt;
		if (! _abortedTx.compareAndSet(tx, null)) {
			throw new Error("getResumePoint failed - concurrency detected");
		}
		return c;
	}
	
	public void reset() {
		Iterator<SpeculaTopLevelTransaction> it = getTransactions().iterator();
		while (it.hasNext()) {
			SpeculaTopLevelTransaction tx = it.next();
			synchronized (tx) {
				if (tx._status != TxStatus.COMMITTED) {
					tx.abortTx();
					it.remove();
					while (it.hasNext()) {
						SpeculaTopLevelTransaction temp = it.next();
						synchronized (temp) {
							temp.abortTx();
						}
						it.remove();
					}
					return;
				}
			}
			it.remove();
		}
	}
	
}
