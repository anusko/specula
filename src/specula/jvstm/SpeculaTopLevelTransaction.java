package specula.jvstm;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import jvstm.ActiveTransactionsRecord;
import jvstm.VBox;
import jvstm.VBoxBody;
import jvstm.util.Cons;
import jvstm.util.Pair;

import org.apache.commons.javaflow.Continuation;

enum TxStatus {

	EXECUTING, COMPLETE, TO_ABORT, ABORTED, CAN_COMMIT, COMMITTED;

}

public class SpeculaTopLevelTransaction extends jvstm.TopLevelTransaction {

	private static final ReentrantLock COMMIT_LOCK = new ReentrantLock(true);

	static volatile Cons<VBoxBody> _bodiesToGC = Cons.empty();
	static final ValidatingThread _validatingThread = new ValidatingThread(50);

	volatile TxStatus _status;
	Cons<Pair<VBox, VBoxBody>> _rs;
	Cons<Pair<VBox, VBoxBody>> _ws;
	Cons<VBoxBody> _bodiesCommitted;
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
			Continuation.cancel();
			// execução vai ficar por aqui
		}

		_rs = this.bodiesRead;
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
	protected Cons<VBoxBody> doCommit(int newTxNumber) {
		Cons<VBoxBody> newBodies = Cons.empty();
		_ws = Cons.empty();

		for (Map.Entry<VBox,Object> entry : boxesWritten.entrySet()) {
			VBox vbox = entry.getKey();
			Object newValue = entry.getValue();

			VBoxBody<?> newBody = vbox.commit((newValue == NULL_VALUE) ? null : newValue, newTxNumber);
			newBodies = newBodies.cons(newBody);

			_ws = _ws.cons(new Pair<VBox, VBoxBody>(vbox, newBody));
		}

		return newBodies;
	}

	@Override
	protected boolean validateCommit() {
		// tem de ser sincronizado por cima
		if (_tc.hasTxAborted() || _status != TxStatus.COMPLETE) return false;	

		int transactionNumber = this.number;

		for (Pair<VBox, VBoxBody> entry : _rs) {
			VBoxBody boxBody = entry.first.body;
			do {
				if (boxBody.version < transactionNumber) {
					break;
				}
				boxBody = boxBody.next;
			} while (boxBody != null);

			if (boxBody != entry.second) {
				return false;
			}
		}
		return true;
	}

	@Override
	protected void abortTx() {
		switch (_status) {
		case EXECUTING:
			_status = TxStatus.ABORTED;
			super.abortTx();
			break;
		case COMPLETE:
		case TO_ABORT:
		case CAN_COMMIT:
			try {
				COMMIT_LOCK.lock();
				for (Pair<VBox, VBoxBody> pair : _ws) {
					pair.first.body = pair.second.next;
				}
			} finally {
				COMMIT_LOCK.unlock();
			}
			_status = TxStatus.ABORTED;
			super.abortTx();	
			break;
		case COMMITTED:
			throw new Error("Trying to abort a committed transaction.");
		case ABORTED:
			throw new Error("Trying to abort an already aborted transaction.");
		}	
	}

	protected void markForAbortion() {
		// tem de ser sincronizado por cima
		if (_status == TxStatus.COMPLETE) {
			_status = TxStatus.TO_ABORT;
			_tc.setAbortedTx(this);
			notifyAll();
		}
	}

	protected void markForCommit() {
		// tem de ser sincronizado por cima
		if (_status == TxStatus.COMPLETE) {
			_status = TxStatus.CAN_COMMIT;
			notifyAll();
		}
	}

	protected void definitiveCommit() {
		_status = TxStatus.COMMITTED;

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

				if (tx._status == TxStatus.TO_ABORT) {
					System.err.println("Aborting a tx!");
					tc.reset();
					Continuation.cancel();
				} else if (tx._status == TxStatus.CAN_COMMIT) {
					tx.definitiveCommit();
					it.remove();
				}	
			}
		}
	}

}
