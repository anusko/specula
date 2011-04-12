package specula.jvstm;

import java.util.LinkedList;
import java.util.Queue;

final class ValidatingThread extends Thread {

	private final Queue<SpeculaTopLevelTransaction> _queue;
	private final long _delay;


	public ValidatingThread(long delay) {
		super();

		_queue = new LinkedList<SpeculaTopLevelTransaction>();
		_delay = delay;

		this.setDaemon(true);
	}

	@Override
	public void run() {
		do {
			while (_queue.isEmpty()) {
				try {
					synchronized (_queue) {
						_queue.wait();	
					}
				} catch (InterruptedException e) { e.printStackTrace();	}
			}

			SpeculaTopLevelTransaction tx = _queue.poll();
			tx._tc.lock.lock();
			try {
				if (tx.validateCommit()) {
					tx.markForCommit();
				} else {
					tx.markForAbortion();
				}
			} finally {
				tx._tc.lock.unlock();
			}

			try {
				Thread.sleep(_delay);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} while(true);
	}

	public void enqueue(SpeculaTopLevelTransaction tx) {
		_queue.add(tx);
		synchronized (_queue) {
			_queue.notify();	
		}
	}

}
