package specula.jvstm;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import jvstm.ActiveTransactionsRecord;
import jvstm.util.Cons;
import jvstm.util.Pair;

import org.apache.commons.javaflow.Continuation;

enum TxStatus {

	EXECUTING, COMPLETE, TO_ABORT, ABORTED, CAN_COMMIT, COMMITTED;

}

public class SpeculaTopLevelTransaction extends jvstm.TopLevelTransaction {

	public static final ReentrantLock COMMIT_LOCK = new ReentrantLock(true);

	static volatile Cons<jvstm.VBoxBody> _bodiesToGC = Cons.empty();
	static final ValidatingThread _validatingThread = new ValidatingThread(5);

	volatile TxStatus _status;
	Cons<Pair<jvstm.VBox, jvstm.VBoxBody>> _rs;
	Cons<Pair<VBox, VBoxBody>> _ws;
	Cons<jvstm.VBoxBody> _bodiesCommitted;
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

		int transactionNumber = this.number;
		if (transactionNumber % 5 == 0) return false;

		for (Pair<jvstm.VBox, jvstm.VBoxBody> entry : _rs) {
			VBox box = (VBox) entry.first;
			VBoxBody boxBody = (VBoxBody) entry.second;

			synchronized (boxBody) {
				while (boxBody.status == BodyStatus.COMPLETE) {
					try {
						boxBody.wait();
					} catch (InterruptedException e) {	}
				}	
			}

			if (box.non_speculative_body != boxBody || boxBody.status == BodyStatus.ABORTED) return false;
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
			for (Pair<VBox, VBoxBody> pair : _ws) {
				pair.second.abort();
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
			synchronized (this) {
				notifyAll();
			}
		}
	}

	protected void markForCommit() {
		// tem de ser sincronizado por cima
		if (_status == TxStatus.COMPLETE) {
			_status = TxStatus.CAN_COMMIT;
			synchronized (this) {
				notifyAll();	
			}
		}
	}

	protected void definitiveCommit() {
		assert (_status == TxStatus.CAN_COMMIT);

		_status = TxStatus.COMMITTED;
		for (Pair<VBox, VBoxBody> pair : _ws) {
			pair.first.commit(pair.second);
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
				tc.lock.lock();
				try {
					tc.reset();
				} finally {
					tc.lock.unlock();
				}
				assert (tx._tc.hasTxAborted());
				Continuation.cancel();
			} else if (tx._status == TxStatus.CAN_COMMIT) {
				tx.definitiveCommit();
				it.remove();
			}
		}
	}

}
