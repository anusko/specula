package specula.jvstm;

import java.lang.reflect.Field;

enum BodyStatus {

	COMPLETE, ABORTED, COMMITTED;

}

public class VBoxBody<E> extends jvstm.VBoxBody<E> {
	// this static field is used to change the non-static final field "next"
	// see the comments on the clearPrevious method
	private static final Field NEXT_FIELD;

	static {
		try {
			NEXT_FIELD = jvstm.VBoxBody.class.getDeclaredField("next");
			NEXT_FIELD.setAccessible(true);
		} catch (NoSuchFieldException nsfe) {
			throw new Error("JVSTM error: couldn't get access to the VBoxBody.next field");
		}
	}

	public BodyStatus status = BodyStatus.COMPLETE;
	//public VBoxBody<E> previous;


	public VBoxBody(E value, int version, jvstm.VBoxBody<E> next) {
		super(value, version, next);
	}

	void commit() {
		synchronized (this) {
			assert (status == BodyStatus.COMPLETE);

			status = BodyStatus.COMMITTED;
			notifyAll();
		}
	}

	void abort() {
		synchronized (this) {
			assert (status == BodyStatus.COMPLETE);

			status = BodyStatus.ABORTED;
//			if (this.next != null) {
//				((VBoxBody<E>) this.next).setPrevious(this.previous);
//			}
//			if (this.previous != null) {
//				this.previous.setNext(this.next);
//			}
			notifyAll();
		}
	}

//	public void setPrevious(VBoxBody<E> body) {
//		this.previous = body;
//	}

	public void setNext(jvstm.VBoxBody<E> body) {
		// see the comments in clearPrevious()
		try {
			NEXT_FIELD.set(this, body);
		} catch (IllegalAccessException iae) {
			throw new Error("JVSTM error: cannot set the next field");
		}
	}

}
