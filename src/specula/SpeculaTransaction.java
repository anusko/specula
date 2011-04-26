package specula;

import org.apache.commons.javaflow.Continuation;


public interface SpeculaTransaction {
	
	ThreadContext getThreadContext();
	
	Continuation getResumePoint();
	
	TransactionStatus getStatus();
	
}
