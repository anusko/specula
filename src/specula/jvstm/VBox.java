package specula.jvstm;

import org.apache.commons.javaflow.Continuation;

import jvstm.Transaction;

public class VBox<E> extends jvstm.VBox<E> {

	public VBoxBody<E> non_speculative_body;


	public VBox() {
		this((E)null);
	}

	public VBox(E initial) {
		super(initial);
	}

	// used for persistence support
	protected VBox(VBoxBody<E> body) {
		this.body = body;
	}

	@Override
	public E get() {
		SpeculaTopLevelTransaction tx = (SpeculaTopLevelTransaction) Transaction.current();
		if (tx == null) {
			do {
				tx = (SpeculaTopLevelTransaction) Transaction.begin();
				E value = tx.getBoxValue(this);
				Transaction.commit();
				
				synchronized (tx) {
					while (tx._status == TxStatus.COMPLETE) {
						try {
							tx.wait();
						} catch (InterruptedException e) { }
					}
					if (tx._status == TxStatus.COMMITTED) return value;
				}
			} while (true);
		} else {
			return tx.getBoxValue(this);
		}
	}

	@Override
	public void put(E newE) {
		SpeculaTopLevelTransaction tx = (SpeculaTopLevelTransaction) Transaction.current();
		if (tx == null) {
			do {
				tx = (SpeculaTopLevelTransaction) Transaction.begin();
				tx.setBoxValue(this, newE);
				Transaction.commit();
				
				synchronized (tx) {
					while (tx._status == TxStatus.COMPLETE) {
						try {
							tx.wait();
						} catch (InterruptedException e) { }
					}
					if (tx._status == TxStatus.COMMITTED) return;
				}
			} while (true);
		} else {
			tx.setBoxValue(this, newE);
		}
	}

	public void abort(VBoxBody<E> body) {
//		if (this.body == body) {
//			this.body = this.body.next;
//		}
		this.body = non_speculative_body; // TODO: rever isto...
		
		body.abort();	
	}

	public void commit(VBoxBody<E> body) {
//		try {
//		assert (non_speculative_body == body.next);
//		} catch (AssertionError e) {
//			System.out.println(non_speculative_body.value + " / " + non_speculative_body.version +
//					" || " + body.value + " / " + body.version +
//					" || " + body.next.value + " / " + body.next.version);
//			System.exit(-1);
//		}
		
		if (non_speculative_body != body.next) {
			body.setNext(non_speculative_body);
		}
		
		non_speculative_body = body;
		body.commit();
	}

	@Override
	public VBoxBody<?> commit(E newValue, int txNumber) {
		VBoxBody<E> newBody = makeNewBody(newValue, txNumber, this.body);
//		if (newBody.next != null) {
//			((VBoxBody<E>) newBody.next).setPrevious(newBody);
//		}
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
