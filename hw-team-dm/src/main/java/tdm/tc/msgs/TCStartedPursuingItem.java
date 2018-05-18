package tdm.tc.msgs;

import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;
import cz.cuni.amis.utils.token.IToken;
import cz.cuni.amis.utils.token.Tokens;

public class TCStartedPursuingItem extends TCMessageData {

	private static final long serialVersionUID = -6083546140135921492L;

	public static final IToken MESSAGE_TYPE = Tokens.get(TCStartedPursuingItem.class.getName());

	private UnrealId who;
	private UnrealId item;

	public TCStartedPursuingItem(UnrealId who, UnrealId item) {
		super(MESSAGE_TYPE);
		this.who = who;
		this.item = item;
	}

	public UnrealId getWho() {
		return who;
	}

	public UnrealId getItem() {
		return item;
	}

	@Override
	public String toString() {
		return "[ITEM STARTED PURSUING] " + item.getStringId();
	}
	
}
