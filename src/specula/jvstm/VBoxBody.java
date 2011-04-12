package specula.jvstm;

enum BodyStatus {

	COMPLETE, ABORTED, COMMITTED;

}

public class VBoxBody<E> extends jvstm.VBoxBody<E> {

	public volatile BodyStatus status = BodyStatus.COMPLETE;


	public VBoxBody(E value, int version, jvstm.VBoxBody<E> next) {
		super(value, version, next);
	}


	@Override
	public VBoxBody<E> getBody(int maxVersion) {
		boolean skip = (version > maxVersion) || status == BodyStatus.ABORTED;
		
		return skip ? (VBoxBody<E>) next.getBody(maxVersion) : this;
	}

	void commit() {
		assert (status == BodyStatus.COMPLETE);

		status = BodyStatus.COMMITTED;
		synchronized (this) {
			notifyAll();
		}
	}

	void abort() {
		assert (status == BodyStatus.COMPLETE);

		status = BodyStatus.ABORTED;
		synchronized (this) {
			notifyAll();
		}
	}

}
