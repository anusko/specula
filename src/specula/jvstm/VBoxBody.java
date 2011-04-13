package specula.jvstm;

enum BodyStatus {

	COMPLETE, ABORTED, COMMITTED;

}

public class VBoxBody<E> extends jvstm.VBoxBody<E> {

	public volatile BodyStatus status = BodyStatus.COMPLETE;


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
			notifyAll();
		}
	}

}
