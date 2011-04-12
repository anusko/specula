package specula.jvstm;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import jvstm.VBox;
import jvstm.VBoxBody;
import jvstm.util.Pair;

import org.apache.commons.javaflow.Continuation;


public class ThreadContext {

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
		_abortedTx = tx;
	}

	public Continuation getResumePoint() {
		assert (hasTxAborted());

		Continuation c = _abortedTx._resumeAt;
		_abortedTx = null;
		return c;
	}

	public void reset() {
		System.err.println("Reseting the context state!");
		
		Iterator<SpeculaTopLevelTransaction> it = getTransactions().iterator();
		while (it.hasNext()) {
			for (Pair<VBox, VBoxBody> pair : it.next()._ws) {
				pair.first.body = pair.second.next;
			}
		}
	}

}
