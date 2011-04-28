package specula.core;

/**
 * EXECUTING -> The transaction is currently executing;
 * COMPLETE -> The transaction has finished its executing, has
 * 			   speculatively committed and is waiting for the
 * 			   its destiny (abort/commit).
 * TO_ABORT -> The speculatively committed transaction should
 * 			   be aborted ASAP;
 * ABORTED -> The transaction has been aborted;
 * COMMITTED -> The transaction has been committed.
 * 
 * State machine:
 * 
 * 		 --------------------------> ABORTED
 * 		/							  ^ ^
 * 		|							 /	|
 * 		|				 ------------	|
 * 		|				/				|
 * EXECUTING ------> COMPLETE ------> TO_ABORT
 *						|  \
 *						|   ------------
 *						|               \
 *						v				 v
 *					COMMITTED <------ TO_COMMIT	
 */

public enum TransactionStatus {

	EXECUTING, COMPLETE, TO_ABORT, ABORTED, TO_COMMIT, COMMITTED;

}