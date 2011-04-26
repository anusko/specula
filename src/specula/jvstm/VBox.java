package specula.jvstm;

import jvstm.CommitException;
import jvstm.Transaction;

import org.apache.commons.javaflow.Continuation;

import specula.TransactionStatus;

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
		// FIXME: talvez o melhor seja proibir get e puts fora das txs pq isto continua mal
		// já que o abort duma tx resulta no abort de todas as q estão para a frente.
		// Um hack possível é caso a tx seja abortada, remove-la do contexto... (é a última)
		// ghostTransaction é hack para resolver isto...
		
		TopLevelTransaction tx = (TopLevelTransaction) Transaction.current();
		if (tx == null) {
			// NOTA: fazer get e put fora duma transacção força à sincronização pq
			// a acção pode estar a ser feita dentro do <clinit> e o javaflow
			// não permite continuações aí.
			// A maneira "inteligente" de fazer isto será analizar com o ASM os <clinit>
			// e chamar um get/put/construtor especial.
			// O construtor é um caso especial pq o commit nunca vai falhar, por isso talvez
			// seja escusado ver se a tx falha, pq supostamente (?) isso nunca vai acontecer.
			TopLevelTransaction.sync();
			do {
				tx = (TopLevelTransaction) Transaction.begin();
				tx.setAsGhostTransaction();
				
				E value = tx.getBoxValue(this);
				try {
					Transaction.commit();
				} catch (CommitException e) {
					Transaction.abort();
					Continuation.cancel();
				}

				synchronized (tx) {
					while (tx._status == TransactionStatus.COMPLETE) {
						try {
							tx.wait();
						} catch (InterruptedException e) { }
					}
				}
				if (tx._status == TransactionStatus.COMMITTED) return value;
			} while (true);

		} else {
			return tx.getBoxValue(this);
		}
	}


	@Override	
	public void put(E newE) {
		TopLevelTransaction tx = (TopLevelTransaction) Transaction.current();
		if (tx == null) {
			// NOTA: fazer get e put fora duma transacção força à sincronização pq
			// a acção pode estar a ser feita dentro do <clinit> e o javaflow
			// não permite continuações aí.
			// A maneira "inteligente" de fazer isto será analizar com o ASM os <clinit>
			// e chamar um get/put/construtor especial.
			// O construtor é um caso especial pq o commit nunca vai falhar, por isso talvez
			// seja escusado ver se a tx falha, pq supostamente (?) isso nunca vai acontecer.
			TopLevelTransaction.sync();
			do {
				tx = (TopLevelTransaction) Transaction.begin();
				tx.setAsGhostTransaction();
				
				tx.setBoxValue(this, newE);
				try {
					Transaction.commit();
				} catch (CommitException e) {
					Transaction.abort();
					Continuation.cancel();
				}

				synchronized (tx) {
					while (tx._status == TransactionStatus.COMPLETE) {
						try {
							tx.wait();
						} catch (InterruptedException e) { }
					}
				}
				if (tx._status == TransactionStatus.COMMITTED) return;
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
