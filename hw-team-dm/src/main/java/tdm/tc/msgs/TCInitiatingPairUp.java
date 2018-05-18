package tdm.tc.msgs;

import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;
import cz.cuni.amis.utils.token.IToken;
import cz.cuni.amis.utils.token.Tokens;

public class TCInitiatingPairUp extends TCMessageData {

	private static final long serialVersionUID = 7101323423462032L;

	public static final IToken MESSAGE_TYPE = Tokens.get("TCInitiatingPairUp");

	private final UnrealId initiator;

	public TCInitiatingPairUp(UnrealId me) {
		super(MESSAGE_TYPE);
		this.initiator = me;
	}

	public UnrealId getInitiator() {
		return initiator;
	}

}
