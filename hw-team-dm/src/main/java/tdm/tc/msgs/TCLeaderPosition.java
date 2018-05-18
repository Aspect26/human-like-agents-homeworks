package tdm.tc.msgs;

import cz.cuni.amis.pogamut.base3d.worldview.object.ILocated;
import cz.cuni.amis.pogamut.base3d.worldview.object.Location;
import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;
import cz.cuni.amis.utils.token.IToken;
import cz.cuni.amis.utils.token.Tokens;

public class TCLeaderPosition extends TCMessageData {

	private static final long serialVersionUID = 7124823425562032L;

	public static final IToken MESSAGE_TYPE = Tokens.get("TCLeaderPosition");

	private final UnrealId leader;
	private final Location location;

	public TCLeaderPosition(UnrealId leader, Location location) {
		super(MESSAGE_TYPE);
		this.leader = leader;
		this.location = location;
	}

	public UnrealId getLeader() {
		return leader;
	}

	public ILocated getLocation() {
	    return this.location;
    }
}
