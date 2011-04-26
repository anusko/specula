package specula.jvstm;

import specula.ThreadContext;
import specula.ThreadContextFactory;

public class JVSTMThreadContextFactory implements ThreadContextFactory {

	@Override
	public ThreadContext makeNew() {
		return new specula.jvstm.ThreadContext();
	}

}
