package specula.jvstm;

import jvstm.Transaction;

public class VBox<E> extends jvstm.VBox<E> {

	public VBoxBody<E> non_speculative_body;

	
    public VBox() {
        this((E)null);
    }
    
    public VBox(E initial) {
    	super(initial);
    	//commit((VBoxBody<E>) this.body);
    }
	
	// used for persistence support
	protected VBox(VBoxBody<E> body) {
		this.body = body;
	}

	@Override
	public E get() {
        Transaction tx = Transaction.current();
        if (tx == null) {
            // Access the box body without creating a full transaction, while
            // still preserving ordering guarantees by 'piggybacking' on the
            // version from the latest commited transaction.
            // If the box body is GC'd before we can reach it, the process
            // re-starts with a newer transaction.
            while (true) {
                int transactionNumber = Transaction.mostRecentRecord.getTransactionNumber();
                jvstm.VBoxBody<E> boxBody = this.body;
                do {
                    if (boxBody.version <= transactionNumber && ((VBoxBody) boxBody).status != BodyStatus.ABORTED) {
                        return boxBody.value;
                    }
                    boxBody = boxBody.next;
                } while (boxBody != null);
            }
        } else {
            return tx.getBoxValue(this);
        }
	}

	@Override
	public void put(E newE) {
		Transaction tx = Transaction.current();
		if (tx == null) {
//			Transaction.begin();
//			super.put(newE);
//			Transaction.commit();
			throw new Error();
		} else {
			super.put(newE);
		}
	}

//	public void abort() {
//		this.body = non_speculative_body;
//	}

	public void commit(VBoxBody<E> body) {
		body.commit();
		non_speculative_body = body;
	}

	@Override
	public VBoxBody<?> commit(E newValue, int txNumber) {
		VBoxBody<E> newBody = makeNewBody(newValue, txNumber, this.body);
		this.body = newBody;
		return newBody;
	}

	// in the future, if more than one subclass of body exists, we may
	// need a factory here but, for now, it's simpler to have it like
	// this
	public static <T> VBoxBody<T> makeNewBody(T value, int version, jvstm.VBoxBody<T> next) {
		return new VBoxBody<T>(value, version, next);
	}

}
