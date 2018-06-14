package ut2004.exercises.e03.comm;

import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;
import cz.cuni.amis.utils.token.IToken;
import cz.cuni.amis.utils.token.Tokens;

import java.util.List;

public class TCRequestPursueItems extends TCMessageData {

	private static final long serialVersionUID = -6083546165135821402L;

	public static final IToken MESSAGE_TYPE = Tokens.get(TCRequestPursueItems.class.getName());

	private UnrealId who;
	private List<UnrealId> items;

	public TCRequestPursueItems(UnrealId who, List<UnrealId> items) {
		super(MESSAGE_TYPE);
		this.who = who;
		this.items = items;
	}

	public UnrealId getWho() {
		return who;
	}

	public List<UnrealId> getItems() {
		return items;
	}

	@Override
	public String toString() {
		return "[REQ_PURSUE_ITEMS] ";
	}
	
}
