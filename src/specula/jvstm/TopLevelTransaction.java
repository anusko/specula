package specula.jvstm;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import jvstm.ActiveTransactionsRecord;
import jvstm.CommitException;
import jvstm.PerTxBox;
import jvstm.util.Cons;
import jvstm.util.Pair;

import org.apache.commons.javaflow.Continuation;

import specula.SpeculaTransaction;
import specula.TransactionStatus;

public class TopLevelTransaction extends jvstm.TopLevelTransaction implements SpeculaTransaction {

	/*
	 * TODO list
	 * 
	 * - Abortar transacções write-only ou pensar em solução melhor
	 * - Um get ou set fora duma transacção é bloqueante por causa
	 *   de possíveis inicializações no clinit
	 * - GC não está a funcionar
	 */

	private static final CommitException COMMIT_EXCEPTION = new CommitException();

	public static final ReentrantLock COMMIT_LOCK = new ReentrantLock(true);

	static Cons<jvstm.VBoxBody> _bodiesToGC = Cons.empty();
	static final ValidatingThread _validatingThread = new ValidatingThread(2);

	TransactionStatus _status;
	public Cons<Pair<jvstm.VBox, jvstm.VBoxBody>> _rs;
	Cons<Pair<VBox, VBoxBody>> _ws;
	Cons<jvstm.VBoxBody> _bodiesCommitted;
	Map<PerTxBox, Object> _perTxValues;

	private final Continuation _resumePoint;
	private final ThreadContext _tc;
	private boolean _ghostTransaction;

	static {
		_validatingThread.start();
	}


	public TopLevelTransaction(ActiveTransactionsRecord activeRecord) {
		super(activeRecord);

		_ws = Cons.empty();
		_tc = (ThreadContext) Continuation.getContext();
		_resumePoint = _tc.getLastContinuation();
		_bodiesCommitted = Cons.empty();
		_status = TransactionStatus.EXECUTING;
		_ghostTransaction = false;

		assert (_tc != null);
	}

	/**
	 * A ghost transaction doesn't show up in the thread context.
	 * This is useful to a put/get values into/from VBoxes when outside
	 * of a transaction. 
	 */
	public void setAsGhostTransaction() {
		_ghostTransaction = true;
	}

	@Override
	public ThreadContext getThreadContext() {
		return _tc;
	}

	@Override
	public Continuation getResumePoint() {
		return _resumePoint;
	}

	@Override
	public TransactionStatus getStatus() {
		return _status;
	}

	@Override
	protected void tryCommit() {
		// FIXME: daqui vem o problem do NullPointerException no bloco finally dos
		// métodos @Atomic pq o Continuation.cancel() dentro do sync() leva ao retorno
		// do método, o que faz com que o bloco finally seja executado.
		// TODO: talvez o mais correcto seja lançar uma excepção nova, tipo
		// SpeculativeCommitException e fazer o Continuation.cancel() aí.
		if (_tc.hasTransactionAborted()) {
			//			abortTx();
			//			sync();
			throw COMMIT_EXCEPTION;
			// execução vai ficar por aqui
		}

		_rs = this.bodiesRead;
		_perTxValues = this.perTxValues;
		// meter no contexto
		if (! _ghostTransaction) _tc.addTransaction(this);

		//if (isWriteTransaction()) {
		COMMIT_LOCK.lock();
		try {
			_bodiesCommitted = performValidCommit();
			// the commit is already done, so create a new ActiveTransactionsRecord
			ActiveTransactionsRecord newRecord = new ActiveTransactionsRecord(getNumber(), _bodiesToGC);
			setMostRecentActiveRecord(newRecord);
			//			_bodiesToGC = Cons.empty();

			// as this transaction changed number, we must
			// update the activeRecords accordingly

			// the correct order is to increment first the
			// new, and only then decrement the old
			newRecord.incrementRunning();
			this.activeTxRecord.decrementRunning();
			this.activeTxRecord = newRecord;

			synchronized (this) {
				// sincronizar para forçar a visibilidade do _status nas outras threads
				_status = TransactionStatus.COMPLETE;	
			}

			// meter na queue para validar
			_validatingThread.enqueue(this);
		} finally {
			COMMIT_LOCK.unlock();
		}
		//}
	}

	@Override
	protected Cons<jvstm.VBoxBody> performValidCommit() {
		int newTxNumber = getMostRecentCommitedNumber() + 1;

		// renumber the TX to the new number
		setNumber(newTxNumber);

		return doCommit(newTxNumber);
	}

	@Override
	protected Cons<jvstm.VBoxBody> doCommit(int newTxNumber) {
		Cons<jvstm.VBoxBody> newBodies = Cons.empty();
		_ws = Cons.empty();

		for (Map.Entry<jvstm.VBox, Object> entry : boxesWritten.entrySet()) {
			VBox vbox = (VBox) entry.getKey();
			Object newValue = entry.getValue();

			jvstm.VBoxBody<?> newBody = vbox.commit((newValue == NULL_VALUE) ? null : newValue, newTxNumber);
			newBodies = newBodies.cons(newBody);

			_ws = _ws.cons(new Pair<VBox, VBoxBody>(vbox, (VBoxBody) newBody));
		}

		return newBodies;
	}

	@Override
	protected boolean validateCommit() {
		synchronized (this) {
			// para ter a certeza que lemos o valor mais recente do _status
			if (_tc.hasTransactionAborted() || _status == TransactionStatus.ABORTED) return false;	
		}

		for (Pair<jvstm.VBox, jvstm.VBoxBody> entry : _rs) {
			VBox box = (VBox) entry.first;
			VBoxBody boxBody = (VBoxBody) entry.second;

			assert (boxBody.status != BodyStatus.COMPLETE);

			if (box.non_speculative_body != boxBody) return false;
		}

		return true;
	}

	@Override
	public synchronized void abortTx() {
		switch (_status) {
		case EXECUTING:
			_status = TransactionStatus.ABORTED;
			super.abortTx();
			break;
		case COMPLETE:
			System.err.println("Aborting speculatively committed transaction number " + this.number);
			COMMIT_LOCK.lock();
			try {
				for (Pair<VBox, VBoxBody> pair : _ws) {
					pair.first.abort(pair.second);
				}
			} finally {
				COMMIT_LOCK.unlock();
			}
			_status = TransactionStatus.ABORTED;
			notifyAll();
			cleanup();
			break;
		case TO_ABORT:
			System.err.println("Aborting speculatively committed transaction number " + this.number);
			_status = TransactionStatus.ABORTED;
			break;
		case COMMITTED:
			throw new Error("Trying to abort a committed transaction.");
		case ABORTED:
			throw new Error("Trying to abort an already aborted transaction.");
		}
	}

	protected void markForAbortion() {
		synchronized (this) {
			if (_status == TransactionStatus.COMPLETE) {

				COMMIT_LOCK.lock();
				try {
					for (Pair<VBox, VBoxBody> pair : _ws) {
						pair.first.abort(pair.second);
					}
				} finally {
					COMMIT_LOCK.unlock();
				}

				_status = TransactionStatus.TO_ABORT;
				if (! _ghostTransaction) _tc.setAbortedTransaction(this);
				notifyAll();
			}
		}

		cleanup();
	}

	protected void definitiveCommit() {
		synchronized (this) {
			assert (_status == TransactionStatus.COMPLETE);

			System.err.println("Committing transaction number " + this.number);

			COMMIT_LOCK.lock();
			try {
				for (Map.Entry<PerTxBox,Object> entry : _perTxValues.entrySet()) {
					entry.getKey().commit(entry.getValue());
				}

				for (Pair<VBox, VBoxBody> pair : _ws) {
					pair.first.commit(pair.second);
				}
			} finally {
				COMMIT_LOCK.unlock();
			}

			_status = TransactionStatus.COMMITTED;
			notifyAll();
		}

		cleanup();

		// GC stuff

		//		Cons cons = _bodiesCommitted;
		//		while (cons.rest() != null) cons = cons.rest();
		//		if (cons != Cons.empty()) {
		//			try {
		//				SpeculaTopLevelTransaction.COMMIT_LOCK.lock();
		//				Cons.class.getField("rest").set(cons, SpeculaTopLevelTransaction._bodiesToGC);
		//				SpeculaTopLevelTransaction._bodiesToGC = _bodiesCommitted;
		//			} catch (Exception e) {
		//				e.printStackTrace();
		//			} finally {
		//				SpeculaTopLevelTransaction.COMMIT_LOCK.unlock();
		//			}
		//		}
	}

	protected void cleanup() {
		// lets help the JVM GC
		//		_rs = null;
		//		_ws = null;
		//		_perTxValues = null;
		//		_bodiesCommitted = null;
	}

	public static void sync() {
		ThreadContext tc = (ThreadContext) Continuation.getContext();
		Iterator<SpeculaTransaction> it = tc.getTransactions().iterator();

		while (it.hasNext()) {
			SpeculaTransaction tx = it.next();
			synchronized (tx) {
				while (tx.getStatus() == TransactionStatus.COMPLETE) {
					try {
						tx.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}

			if (tx.getStatus() == TransactionStatus.TO_ABORT) {
				assert (tc.isTheAbortedTransaction(tx));
				Continuation.cancel();
			}

			it.remove();
		}
	}

}
