package tdm.tc.msgs;

import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;
import cz.cuni.amis.utils.token.IToken;
import cz.cuni.amis.utils.token.Tokens;

public class TCInitiatorAcceptingPairUp extends TCMessageData {

	private static final long serialVersionUID = 7866128542562032L;

	public static final IToken MESSAGE_TYPE = Tokens.get("TCInitiatorAcceptingPairUp");

	private final UnrealId initiator;

	public TCInitiatorAcceptingPairUp(UnrealId initiator) {
		super(MESSAGE_TYPE);
		this.initiator = initiator;
	}

	public UnrealId getInitiator() {
		return initiator;
	}

}
