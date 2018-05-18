package tdm.tc.msgs;

import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;
import cz.cuni.amis.utils.token.IToken;
import cz.cuni.amis.utils.token.Tokens;

public class TCCancelPairUp extends TCMessageData {

	private static final long serialVersionUID = 7866128540002032L;

	public static final IToken MESSAGE_TYPE = Tokens.get("TcCancelPairUp");

	private final UnrealId canceledBy;

	public TCCancelPairUp(UnrealId canceledBy) {
		super(MESSAGE_TYPE);
		this.canceledBy = canceledBy;
	}

	public UnrealId getCanceledBy() {
		return canceledBy;
	}

}
