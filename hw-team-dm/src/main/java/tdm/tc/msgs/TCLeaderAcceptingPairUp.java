package tdm.tc.msgs;

import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;
import cz.cuni.amis.utils.token.IToken;
import cz.cuni.amis.utils.token.Tokens;

public class TCLeaderAcceptingPairUp extends TCMessageData {

	private static final long serialVersionUID = 1862323542562032L;

	public static final IToken MESSAGE_TYPE = Tokens.get("TCLeaderAcceptingPairUp");

	private final UnrealId leader;

	public TCLeaderAcceptingPairUp(UnrealId leader) {
		super(MESSAGE_TYPE);
		this.leader = leader;
	}

	public UnrealId getLeader() {
		return leader;
	}

}
