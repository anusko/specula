package specula.jvstm;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.javaflow.Continuation;


public class ThreadContext {
	
	public final ReentrantLock lock = new ReentrantLock(true);

	private final List<SpeculaTopLevelTransaction> _transactions;
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

		return _transactions;
	}

	public Continuation getLastContinuation() {
		return _lastContinuation;
	}

	public void setLastContinuation(Continuation c) {
		_lastContinuation = c;
	}

	public boolean hasTxAborted() {
		synchronized (this) {
			return ! (_abortedTx == null);
		}
	}

	public void setAbortedTx(SpeculaTopLevelTransaction tx) {
		synchronized (this) {
			_abortedTx = tx;	
		}
	}

	public Continuation getResumePoint() {
		synchronized (this) {
			assert (hasTxAborted());

			Continuation c = _abortedTx._resumeAt;
			_abortedTx = null;
			return c;
		}
	}

	public void reset() {
		Iterator<SpeculaTopLevelTransaction> it = getTransactions().iterator();
		boolean abort = false;
		
		while (it.hasNext()) {
			SpeculaTopLevelTransaction tx = it.next();
			
			if (abort) {
				tx.abortTx();
			} else if (tx._status == TxStatus.TO_ABORT) {
				tx.abortTx();
				abort = true;
			} else {
				throw new Error("Dead code...");
			}
			
			it.remove();
		}
	}

}
