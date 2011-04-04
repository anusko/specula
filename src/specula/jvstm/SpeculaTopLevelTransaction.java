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
	
	private final Continuation resumeAt;
	private Set<VBox> localBoxesWritten = EMPTY_SET;
	
	
	public SpeculaTopLevelTransaction(ActiveTransactionsRecord activeRecord) {
		super(activeRecord);
		
		ThreadContext tc = (ThreadContext) Continuation.getContext();
		this.resumeAt = tc.getLastContinuation();
		
		assert (resumeAt != null);
	}

	public Continuation getResumeAt() {
		return this.resumeAt;
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
			if (this.localBoxesWritten == EMPTY_SET) {
				this.localBoxesWritten = new HashSet<VBox>();
			}
			// TODO: meter o valor
			this.localBoxesWritten.add(vbox);
		}
	}
	
}
