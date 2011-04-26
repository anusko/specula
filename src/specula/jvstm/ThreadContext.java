package specula.jvstm;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.javaflow.Continuation;

import specula.SpeculaTransaction;
import specula.TransactionStatus;


public class ThreadContext extends specula.ThreadContext {
	
	private final List<SpeculaTransaction> _transactions;
	private Continuation _lastContinuation;
	// o volatile obriga à sincronização entre a thread de validação e a
	// que está a olhar para o field
	private volatile SpeculaTransaction _abortedTx;
	//private final AtomicReference<SpeculaTransaction> _abortedTx;

	private final Thread _startingThread;


	public ThreadContext() {
		_transactions = new LinkedList<SpeculaTransaction>();
		//_abortedTx = new AtomicReference<SpeculaTransaction>();
		
		_startingThread = Thread.currentThread();
	}

	public Thread getThread() {
		return _startingThread;
	}

	void addTransaction(SpeculaTransaction tx) {
		assert (_startingThread == Thread.currentThread());

		_transactions.add(tx);
	}

	Collection<SpeculaTransaction> getTransactions() {
		assert (_startingThread == Thread.currentThread());

		return _transactions;
	}

	public Continuation getLastContinuation() {
		return _lastContinuation;
	}

	public void setLastContinuation(Continuation c) {
		_lastContinuation = c;
	}

	public boolean hasTransactionAborted() {
		synchronized (this) {
			return ! (_abortedTx == null);
		}
		
//		return ! _abortedTx.compareAndSet(null, null);
	}

	public void setAbortedTransaction(SpeculaTransaction tx) {
		synchronized (this) {
			if (_abortedTx == null) _abortedTx = tx;
		}
		
//		_abortedTx.compareAndSet(null, tx);
	}
	
	public boolean isTheAbortedTransaction(SpeculaTransaction tx) {
		synchronized (this) {
			return (_abortedTx == tx);
		}
		
//		return _abortedTx.compareAndSet(tx, tx);
	}

	public Continuation reset() {
		assert (_startingThread == Thread.currentThread());
		
		Iterator<SpeculaTransaction> it = getTransactions().iterator();
		boolean abort = false;
		
		while (it.hasNext()) {
			TopLevelTransaction tx = (TopLevelTransaction) it.next();
			
			if (abort) {
				tx.abortTx();
			} else if (tx.getStatus() == TransactionStatus.TO_ABORT) {
				assert (isTheAbortedTransaction(tx));
				tx.abortTx();
				abort = true;
			} else if (tx.getStatus() == TransactionStatus.COMPLETE) {
				throw new Error("Dead code...");
			}
			
			it.remove();
		}
		
		assert (_transactions.isEmpty());
		
		Continuation resumePoint = _abortedTx.getResumePoint();
		synchronized (this) {
			_abortedTx = null;	
		}
//		resumePoint = _abortedTx.get().getResumePoint();
//		_abortedTx.set(null);
		// ----
		_lastContinuation = null;
		
		return resumePoint;
	}

}
