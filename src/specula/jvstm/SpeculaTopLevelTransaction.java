package specula.jvstm;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import jvstm.ActiveTransactionsRecord;
import jvstm.VBox;

import org.apache.commons.javaflow.Continuation;

import specula.runtime.ThreadContext;

public class SpeculaTopLevelTransaction extends jvstm.TopLevelTransaction {
	
	private static final Set<VBox> EMPTY_SET = Collections.emptySet();
	
	private final Continuation _resumeAt;
	private Set<VBox> _localBoxesWritten = EMPTY_SET;
	
	
	public SpeculaTopLevelTransaction(ActiveTransactionsRecord activeRecord) {
		super(activeRecord);
		
		ThreadContext tc = (ThreadContext) Continuation.getContext();
		_resumeAt = tc.getLastContinuation();
		
		assert (_resumeAt != null);
	}

	public Continuation getResumeAt() {
		return _resumeAt;
	}
	
	// TODO: verificar se é necessário
	@Override
	protected boolean isWriteTransaction() {
		return true;
	}
	
	/*
	 * (non-Javadoc)
	 * @see jvstm.ReadWriteTransaction#setBoxValue(jvstm.VBox, java.lang.Object)
	 * 
	 * TODO
	 * É capaz de ser impossível fazer escritas write-through porque o ID da
	 * trasacção, mesmo que especulativo, só é definito na altura do commit.
	 */
	@Override
	public <T> void setBoxValue(VBox<T> vbox, T value) {
		if (vbox instanceof xtramy.stm.speculation.VBox) {
			super.setBoxValue(vbox, value);
		} else {
			// write-through
			if (_localBoxesWritten == EMPTY_SET) {
				_localBoxesWritten = new HashSet<VBox>();
			}
			// TODO: meter o valor
			_localBoxesWritten.add(vbox);
		}
	}
	
}
