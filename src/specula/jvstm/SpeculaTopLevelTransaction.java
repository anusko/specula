package specula.jvstm;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import jvstm.ActiveTransactionsRecord;
import jvstm.PerTxBox;
import jvstm.util.Cons;
import jvstm.util.Pair;

import org.apache.commons.javaflow.Continuation;

enum TxStatus {

	EXECUTING, COMPLETE, TO_ABORT, ABORTED, COMMITTED;

}

public class SpeculaTopLevelTransaction extends jvstm.TopLevelTransaction {

	/*
	 * TODO list
	 * 
	 * - Abortar transacções write-only ou pensar em solução melhor
	 * - Um get ou set fora duma transacção é bloqueante por causa
	 *   de possíveis inicializações no clinit
	 */

	public static final ReentrantLock COMMIT_LOCK = new ReentrantLock(true);

	static Cons<jvstm.VBoxBody> _bodiesToGC = Cons.empty();
	static final ValidatingThread _validatingThread = new ValidatingThread(10);

	TxStatus _status;
	public Cons<Pair<jvstm.VBox, jvstm.VBoxBody>> _rs;
	Cons<Pair<VBox, VBoxBody>> _ws;
	Cons<jvstm.VBoxBody> _bodiesCommitted;
	Map<PerTxBox, Object> _perTxValues;
	
	final Continuation _resumeAt;
	final ThreadContext _tc;

	static {
		_validatingThread.start();
	}


	public SpeculaTopLevelTransaction(ActiveTransactionsRecord activeRecord) {
		super(activeRecord);

		_ws = Cons.empty();
		_tc = (ThreadContext) Continuation.getContext();
		_resumeAt = _tc.getLastContinuation();
		_bodiesCommitted = Cons.empty();
		_status = TxStatus.EXECUTING;

		assert (_tc != null);
	}

	public Continuation getResumeAt() {
		return _resumeAt;
	}

	@Override
	protected void tryCommit() {
		if (_tc.hasTxAborted()) {
			abortTx();
			sync();
			// execução vai ficar por aqui
		}

		_rs = this.bodiesRead;
		_perTxValues = this.perTxValues;
		// meter no contexto
		_tc.addTransaction(this);

		//if (isWriteTransaction()) {
		COMMIT_LOCK.lock();
		try {
			_bodiesCommitted = performValidCommit();
			// the commit is already done, so create a new ActiveTransactionsRecord
			ActiveTransactionsRecord newRecord = new ActiveTransactionsRecord(getNumber(), _bodiesToGC);
			setMostRecentActiveRecord(newRecord);
			_bodiesToGC = Cons.empty();

			// as this transaction changed number, we must
			// update the activeRecords accordingly

			// the correct order is to increment first the
			// new, and only then decrement the old
			newRecord.incrementRunning();
			this.activeTxRecord.decrementRunning();
			this.activeTxRecord = newRecord;

			_status = TxStatus.COMPLETE;

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
		// tem de ser sincronizado por cima
		if (_tc.hasTxAborted() || _status == TxStatus.ABORTED) return false;	

		for (Pair<jvstm.VBox, jvstm.VBoxBody> entry : _rs) {
			VBox box = (VBox) entry.first;
			VBoxBody boxBody = (VBoxBody) entry.second;

			if (boxBody.status == BodyStatus.COMPLETE) {
				throw new Error();
			}

			if (box.non_speculative_body != boxBody) return false;
		}
		return true;
	}

	@Override
	protected void abortTx() {
		synchronized (this) {
			switch (_status) {
			case EXECUTING:
				_status = TxStatus.ABORTED;
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
				_status = TxStatus.ABORTED;
				notifyAll();
				break;
			case TO_ABORT:
				System.err.println("Aborting speculatively committed transaction number " + this.number);
				_status = TxStatus.ABORTED;
				break;
			case COMMITTED:
				throw new Error("Trying to abort a committed transaction.");
			case ABORTED:
				throw new Error("Trying to abort an already aborted transaction.");
			}		
		}
	}

	protected void markForAbortion() {
		synchronized (this) {
			if (_status == TxStatus.COMPLETE) {

				COMMIT_LOCK.lock();
				try {
					for (Pair<VBox, VBoxBody> pair : _ws) {
						pair.first.abort(pair.second);
					}
				} finally {
					COMMIT_LOCK.unlock();
				}

				_status = TxStatus.TO_ABORT;
				_tc.setAbortedTx(this);
				notifyAll();
			}
		}
	}

	protected void definitiveCommit() {
		synchronized (this) {
			assert (_status == TxStatus.COMPLETE);

			System.err.println("Committing transaction number " + this.number);

			COMMIT_LOCK.lock();
			try {
				for (Map.Entry<PerTxBox,Object> entry : perTxValues.entrySet()) {
					entry.getKey().commit(entry.getValue());
				}
			
				for (Pair<VBox, VBoxBody> pair : _ws) {
					pair.first.commit(pair.second);
				}
			} finally {
				COMMIT_LOCK.unlock();
			}

			_status = TxStatus.COMMITTED;
			notifyAll();
		}

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

	public static void sync() {
		ThreadContext tc = (ThreadContext) Continuation.getContext();
		Iterator<SpeculaTopLevelTransaction> it = tc.getTransactions().iterator();

		while (it.hasNext()) {
			SpeculaTopLevelTransaction tx = it.next();
			synchronized (tx) {
				while (tx._status == TxStatus.COMPLETE) {
					try {
						tx.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}

			if (tx._status == TxStatus.TO_ABORT) {
				assert (tc.isTheAbortedTx(tx));
				Continuation.cancel();
			}

			it.remove();
		}
	}

}
