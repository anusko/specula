package specula.jvstm;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import jvstm.ActiveTransactionsRecord;
import jvstm.CommitException;
import jvstm.VBox;
import jvstm.VBoxBody;
import jvstm.util.Cons;
import jvstm.util.Pair;

import org.apache.commons.javaflow.Continuation;

public class SpeculaTopLevelTransaction extends jvstm.TopLevelTransaction {

	public static final ReentrantLock COMMIT_LOCK = new ReentrantLock(true);

	static volatile Cons<jvstm.VBoxBody> _bodiesToGC = Cons.empty();
	static final ValidatingThread _validatingThread = new ValidatingThread(5);

	Cons<Pair<jvstm.VBox, jvstm.VBoxBody>> _rs;
	Cons<Pair<VBox, VBoxBody>> _ws;
	Cons<jvstm.VBoxBody> _bodiesCommitted;
	final Continuation _resumeAt;
	final ThreadContext _tc;


	public SpeculaTopLevelTransaction(ActiveTransactionsRecord activeRecord) {
		super(activeRecord);

		_ws = Cons.empty();
		_tc = (ThreadContext) Continuation.getContext();
		_resumeAt = _tc.getLastContinuation();
		_bodiesCommitted = Cons.empty();

		assert (_tc != null);
	}

	public Continuation getResumeAt() {
		return _resumeAt;
	}
	
	@Override
    protected void tryCommit() {
        if (isWriteTransaction()) {
            //Thread currentThread = Thread.currentThread();
            //int origPriority = currentThread.getPriority();
            //currentThread.setPriority(Thread.MAX_PRIORITY);
            COMMIT_LOCK.lock();
            try {
		if (validateCommit()) {
                    Cons<VBoxBody> bodiesCommitted = performValidCommit();
                    bodiesCommitted = Cons.empty();
                    // the commit is already done, so create a new ActiveTransactionsRecord
                    ActiveTransactionsRecord newRecord = new ActiveTransactionsRecord(getNumber(), bodiesCommitted);
                    setMostRecentActiveRecord(newRecord);

                    // as this transaction changed number, we must
                    // update the activeRecords accordingly

                    // the correct order is to increment first the
                    // new, and only then decrement the old
                    newRecord.incrementRunning();
                    this.activeTxRecord.decrementRunning();
                    this.activeTxRecord = newRecord;
		} else {
		    throw new CommitException();
//		    throw COMMIT_EXCEPTION;
		}
            } finally {
                COMMIT_LOCK.unlock();
                //currentThread.setPriority(origPriority);
            }
        }
    }

	@Override
	protected Cons<VBoxBody> doCommit(int newTxNumber) {
		Cons<VBoxBody> newBodies = Cons.empty();

		for (Map.Entry<VBox,Object> entry : boxesWritten.entrySet()) {
			VBox vbox = entry.getKey();
			Object newValue = entry.getValue();

			VBoxBody<?> newBody = vbox.commit((newValue == NULL_VALUE) ? null : newValue, newTxNumber);
			newBodies = newBodies.cons(newBody);
			
			_ws = _ws.cons(new Pair<VBox, VBoxBody>(vbox, (VBoxBody) newBody));
		}

		return newBodies;
	}

}