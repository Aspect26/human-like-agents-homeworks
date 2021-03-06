package tdm.tc.msgs;

import cz.cuni.amis.pogamut.base3d.worldview.object.ILocated;
import cz.cuni.amis.pogamut.base3d.worldview.object.Location;
import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;
import cz.cuni.amis.utils.token.IToken;
import cz.cuni.amis.utils.token.Tokens;

public class TCFollowerPosition extends TCMessageData {

	private static final long serialVersionUID = 7124823225562032L;

	public static final IToken MESSAGE_TYPE = Tokens.get("TCLeaderPosition");

	private final UnrealId follower;
	private final Location location;

	public TCFollowerPosition(UnrealId follower, Location location) {
		super(MESSAGE_TYPE);
		this.follower = follower;
		this.location = location;
	}

	public UnrealId getFollower() {
		return follower;
	}

	public ILocated getLocation() {
	    return this.location;
    }
}
