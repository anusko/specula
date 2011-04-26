package specula.jvstm;

import jvstm.ActiveTransactionsRecord;
import jvstm.Transaction;

public class SpeculaTransactionFactory implements jvstm.TransactionFactory {
	
	public Transaction makeTopLevelTransaction(ActiveTransactionsRecord record) {
		return new TopLevelTransaction(record);
	}

	public Transaction makeReadOnlyTopLevelTransaction(ActiveTransactionsRecord record) {
		throw new Error("Read only transactions should not be used");
	}

}
