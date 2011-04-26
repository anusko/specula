package specula.jvstm;

import java.util.LinkedList;
import java.util.Queue;

final class ValidatingThread extends Thread {

	private final Queue<TopLevelTransaction> _queue;
	private final long _delay;


	public ValidatingThread(final long delay) {
		super();

		_queue = new LinkedList<TopLevelTransaction>();
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

			TopLevelTransaction tx = _queue.poll();
			if (tx.validateCommit()) {
				tx.definitiveCommit();
			} else {
				tx.markForAbortion();
			}

			try {
				Thread.sleep(_delay);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} while(true);
	}

	public void enqueue(final TopLevelTransaction tx) {
		_queue.add(tx);
		synchronized (_queue) {
			_queue.notify();	
		}
	}

}
