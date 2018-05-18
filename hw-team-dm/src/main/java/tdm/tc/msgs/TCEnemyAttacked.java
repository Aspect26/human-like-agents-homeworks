package tdm.tc.msgs;

import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;
import cz.cuni.amis.utils.token.IToken;
import cz.cuni.amis.utils.token.Tokens;

public class TCEnemyAttacked extends TCMessageData {

	private static final long serialVersionUID = 7452323423010232L;

	public static final IToken MESSAGE_TYPE = Tokens.get("TCEnemyAttacked");

	private final UnrealId ally;
	private final UnrealId enemy;

	public TCEnemyAttacked(UnrealId ally, UnrealId enemy) {
		super(MESSAGE_TYPE);
		this.ally = ally;
		this.enemy = enemy;
	}

	public UnrealId getAlly() {
		return ally;
	}

	public UnrealId getEnemy() {
		return enemy;
	}

	@Override
	public String toString() {
		return "[ITEM PICKED] " + enemy.getStringId();
	}
	
}
