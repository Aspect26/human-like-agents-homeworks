package tdm;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;

import cz.cuni.amis.pathfinding.alg.astar.AStarResult;
import cz.cuni.amis.pathfinding.map.IPFMapView;
import cz.cuni.amis.pogamut.base.agent.navigation.IPathFuture;
import cz.cuni.amis.pogamut.base.agent.navigation.impl.PrecomputedPathFuture;
import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.EventListener;
import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.ObjectClassEventListener;
import cz.cuni.amis.pogamut.base.communication.worldview.object.event.WorldObjectUpdatedEvent;
import cz.cuni.amis.pogamut.base.utils.guice.AgentScoped;
import cz.cuni.amis.pogamut.base3d.worldview.object.ILocated;
import cz.cuni.amis.pogamut.base3d.worldview.object.Rotation;
import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.agent.module.sensor.AgentInfo;
import cz.cuni.amis.pogamut.ut2004.agent.module.sensor.WeaponPref;
import cz.cuni.amis.pogamut.ut2004.agent.module.utils.UT2004Skins;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.levelGeometry.RayCastResult;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.navmesh.NavMeshClearanceComputer.ClearanceLimit;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.navmesh.pathfollowing.NavMeshNavigation;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004Bot;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004BotModuleController;
import cz.cuni.amis.pogamut.ut2004.communication.messages.ItemType;
import cz.cuni.amis.pogamut.ut2004.communication.messages.UT2004ItemType;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Initialize;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.BotDamaged;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.BotKilled;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.ConfigChange;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.GameInfo;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.HearNoise;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.IncomingProjectile;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.InitedMessage;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Item;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.ItemPickedUp;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.NavPoint;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Player;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.PlayerDamaged;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.PlayerKilled;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Self;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.TeamScore;
import cz.cuni.amis.pogamut.ut2004.teamcomm.bot.UT2004BotTCController;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;
import cz.cuni.amis.pogamut.ut2004.utils.UT2004BotRunner;
import cz.cuni.amis.utils.ExceptionToString;
import cz.cuni.amis.utils.IFilter;
import cz.cuni.amis.utils.collections.MyCollections;
import cz.cuni.amis.utils.exception.PogamutException;
import math.geom2d.Vector2D;
import tdm.tc.TDMCommItems;
import tdm.tc.TDMCommObjectUpdates;
import tdm.tc.msgs.*;

/**
 * TDM BOT TEMPLATE CLASS
 * Version: 0.0.1
 */
@AgentScoped
public class TDMBot extends UT2004BotTCController<UT2004Bot> {

    private class PursuedItem {
        public final int stoppedBeingPursued;
        public final UnrealId itemId;

        private PursuedItem(int stoppedBeingPursued, UnrealId itemId) {
            this.stoppedBeingPursued = stoppedBeingPursued;
            this.itemId = itemId;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof PursuedItem) {
                return ((PursuedItem) o).itemId.equals(this.itemId);
            }

            return false;
        }
    }

    private static final int LOW_HEALTH = 40;
    private static final int PURSUING_ITEM_TIME = 20;
    private static final double LEADING_DISTANCE_THRESHOLD = 500d;
    private static final double FOLLOWER_DISTANCE_THRESHOLD = 50d;

	private static Object CLASS_MUTEX = new Object();

    private UnrealId navigatingToItem = null;
    private Player currentCombatEnemy = null;
    private UnrealId leading = null;
    private UnrealId leadBy = null;
    private ILocated leaderLocation;
    private ILocated followerLocation;
    private int time = 0;

	/**
	 * TRUE => draws navmesh and terminates
	 */
	public static final boolean DRAW_NAVMESH = true;
	private static boolean navmeshDrawn = false;
	
	/**
	 * TRUE => rebinds NAVMESH+NAVIGATION GRAPH; useful when you add new map tweak into {@link MapTweaks}.
	 */
	public static final boolean UPDATE_NAVMESH = true;
	
	/**
	 * Whether to draw navigation path; works only if you are running 1 bot...
	 */
	public static final boolean DRAW_NAVIGATION_PATH = false;
	private boolean navigationPathDrawn = false;
	
	/**
	 * If true, all bots will enter RED team... 
	 */
	public static final boolean START_BOTS_IN_SINGLE_TEAM = false;
		
	/**
	 * How many bots we have started so far; used to split bots into teams.
	 */
	private static AtomicInteger BOT_COUNT = new AtomicInteger(0);
	/**
	 * How many bots have entered RED team.
	 */
	private static AtomicInteger BOT_COUNT_RED_TEAM = new AtomicInteger(0);
	/**
	 * How many bots have entered BLUE team.
	 */
	private static AtomicInteger BOT_COUNT_BLUE_TEAM = new AtomicInteger(0);
	
	/**
	 * 0-based; note that during the tournament all your bots will have botInstance == 0!
	 */
	private int botInstance = 0;
	
	/**
	 * 0-based; note that during the tournament all your bots will have botTeamInstance == 0!
	 */
	private int botTeamInstance = 0;
	
	private TDMCommItems<TDMBot> commItems;
	private TDMCommObjectUpdates<TDMBot> commObjectUpdates;
	
    // =============
    // BOT LIFECYCLE
    // =============
    
    /**
     * Bot's preparation - called before the bot is connected to GB2004 and launched into UT2004.
     */
    @Override
    public void prepareBot(UT2004Bot bot) {       	
        // DEFINE WEAPON PREFERENCES
        initWeaponPreferences();
        
        // INITIALIZATION OF COMM MODULES
        commItems = new TDMCommItems<>(this);
        commObjectUpdates = new TDMCommObjectUpdates<>(this);
    }
    
    /**
     * This is a place where you should use map tweaks, i.e., patch original Navigation Graph that comes from UT2004.
     */
    @Override
    public void mapInfoObtained() {
    	// See {@link MapTweaks} for details; add tweaks in there if required.
    	MapTweaks.tweak(navBuilder);    	
    	if (botInstance == 0) {
    		navMeshModule.setReloadNavMesh(UPDATE_NAVMESH);
		}
    }

	private static final int EXTRA_CLOSE = 80;
	private static final int CLOSE_RANGE = 400;
	private static final int MEDIUM_RANGE = 1150;
	private static final int MAX_RANGE = 20000000; // random large enough (hopefully) constant
    /**
     * Define your weapon preferences here (if you are going to use weaponPrefs).
     * 
     * For more info, see slides (page 8): http://diana.ms.mff.cuni.cz/pogamut_files/lectures/2010-2011/Pogamut3_Lecture_03.pdf
     */
    private void initWeaponPreferences() {
		this.weaponPrefs.addGeneralPref(UT2004ItemType.ASSAULT_RIFLE, true);

		this.weaponPrefs.newPrefsRange(EXTRA_CLOSE)
				.add(UT2004ItemType.SHIELD_GUN, true);
		this.weaponPrefs.newPrefsRange(CLOSE_RANGE)
				.add(UT2004ItemType.FLAK_CANNON, true)
				.add(UT2004ItemType.MINIGUN, true)
				.add(UT2004ItemType.SHOCK_RIFLE, true)
				.add(UT2004ItemType.LINK_GUN, false);
		this.weaponPrefs.newPrefsRange(MEDIUM_RANGE)
				.add(UT2004ItemType.FLAK_CANNON, true)
				.add(UT2004ItemType.MINIGUN, false)
				.add(UT2004ItemType.LINK_GUN, true);
		this.weaponPrefs.newPrefsRange(MAX_RANGE)
				.add(UT2004ItemType.LIGHTNING_GUN, true)
				.add(UT2004ItemType.MINIGUN, false);
	}

	@Override
    public Initialize getInitializeCommand() {
    	// IT IS FORBIDDEN BY COMPETITION RULES TO CHANGE DESIRED SKILL TO DIFFERENT NUMBER THAN 6
    	// IT IS FORBIDDEN BY COMPETITION RULES TO ALTER ANYTHING EXCEPT NAME & SKIN VIA INITIALIZE COMMAND
		// Jakub Gemrot -> targetName = "JakubGemrot"
		String targetName = "MyBot";
		botInstance = BOT_COUNT.getAndIncrement();
		
		int targetTeam = AgentInfo.TEAM_RED;
		if (!START_BOTS_IN_SINGLE_TEAM) {
			targetTeam = botInstance % 2 == 0 ? AgentInfo.TEAM_RED : AgentInfo.TEAM_BLUE;
		}
		switch (targetTeam) {
		case AgentInfo.TEAM_RED: 
			botTeamInstance = BOT_COUNT_RED_TEAM.getAndIncrement();  
			targetName += "-RED-" + botTeamInstance; 
			break;
		case AgentInfo.TEAM_BLUE: 
			botTeamInstance = BOT_COUNT_BLUE_TEAM.getAndIncrement(); 
			targetName += "-BLUE-" + botTeamInstance;
			break;
		}		
        return new Initialize().setName(targetName).setSkin(targetTeam == AgentInfo.TEAM_RED ? UT2004Skins.SKINS[0] : UT2004Skins.SKINS[UT2004Skins.SKINS.length-1]).setTeam(targetTeam).setDesiredSkill(6);
    }

    /**
     * Bot has been initialized inside GameBots2004 (Unreal Tournament 2004) and is about to enter the play
     * (it does not have the body materialized yet).
     *  
     * @param gameInfo
     * @param currentConfig
     * @param init
     */
    @Override
    public void botInitialized(GameInfo gameInfo, ConfigChange currentConfig, InitedMessage init) {
    	// INITIALIZE TABOO SETS, if you have them, HERE
    }

    // ==========================
    // EVENT LISTENERS / HANDLERS
    // ==========================
	
    /**
     * {@link PlayerDamaged} listener that senses that "some other bot was hurt".
     *
     * @param event
     */
    @EventListener(eventClass = PlayerDamaged.class)
    public void playerDamaged(PlayerDamaged event) {
    	final UnrealId botHurtId = event.getId();
    	if (botHurtId == null) {
    	    return;
        }
    	
    	final int damage = event.getDamage();
        final Player botHurt = (Player)world.get(botHurtId); // MAY BE NULL!
    	
    	log.info("OTHER HURT: " + damage + " DMG to " + botHurtId.getStringId() + " [type=" + event.getDamageType() + ", weapon=" + event.getWeaponName() + "]");
    }
    
    /**
     * {@link BotDamaged} listener that senses that "I was hurt".
     *
     * @param event
     */
    @EventListener(eventClass = BotDamaged.class)
    public void botDamaged(BotDamaged event) {
    	int damage = event.getDamage();
    	
    	if (event.getInstigator() == null) {
    		log.info("HURT: " + damage + " DMG done to ME [type=" + event.getDamageType() + ", weapon=" + event.getWeaponName() + "] by UNKNOWN");
    	} else {
    		UnrealId whoCauseDmgId = event.getInstigator();
    		Player player = (Player) world.get(whoCauseDmgId); // MAY BE NULL!
    		log.info("HURT: " + damage + " DMG done to ME [type=" + event.getDamageType() + ", weapon=" + event.getWeaponName() + "] by " + whoCauseDmgId.getStringId());
    	}
    }
    
    /**
     * {@link PlayerKilled} listener that senses that "some other bot has died".
     *
     * @param event
     */
    @EventListener(eventClass = PlayerKilled.class)
    public void playerKilled(PlayerKilled event) {
    	UnrealId botDiedId = event.getId();
    	if (botDiedId == null) {
    	    return;
        }

        if (leadBy != null && leadBy.equals(botDiedId)) {
    	    //leadBy = null;
        }
        if (leading != null && leading.equals(botDiedId)) {
    	    //leading = null;
        }
        if (this.info.getId().equals(botDiedId)) {
    	    //leading = null;
    	    //leadBy = null;
        }

    	Player botDied = (Player) world.get(botDiedId);
    	
    	if (event.getKiller() == null) {
    		log.info("OTHER DIED: " + botDiedId.getStringId() + ", UNKNOWN killer");
    	} else {
    		UnrealId killerId = event.getKiller();
    		if (killerId.equals(info.getId())) {
    			log.info("OTHER KILLED: " + botDiedId.getStringId() + " by ME");
    		} else {
    			Player killer = (Player) world.get(killerId);
    			if (botDiedId.equals(killerId)) {
    				log.info("OTHER WAS KILLED: " + botDiedId.getStringId() + " comitted suicide");
    			} else {
    				log.info("OTHER WAS KILLED: " + botDiedId.getStringId() + " by " + killerId.getStringId());
    			}
    		}
    	}
    }
    
    /**
     * {@link BotKilled} listener that senses that "your bot has died".
     */
	@Override
	public void botKilled(BotKilled event) {
		if (event.getKiller() == null) {
			log.info("DEAD");
		} else {
			UnrealId killerId = event.getKiller();
			Player killer = (Player) world.get(killerId);
			log.info("KILLED by" + killerId.getStringId());
		} 
		reset();
	}
	
    /**
     * {@link HearNoise} listener that senses that "some noise was heard by the bot".
     *
     * @param event
     */
    @EventListener(eventClass = HearNoise.class)
    public void hearNoise(HearNoise event) {
    	double noiseDistance = event.getDistance();   // 100 ~ 1 meter
    	Rotation faceRotation = event.getRotation();  // rotate bot to this if you want to face the location of the noise
    }
    
    /**
     * {@link ItemPickedUp} listener that senses that "your bot has picked up some item".
     * 
     * See sources for {@link ItemType} for details about item types / categories / groups.
     *
     * @param event
     */
    @EventListener(eventClass = ItemPickedUp.class)
    public void itemPickedUp(ItemPickedUp event) {
        Item item = items.getItem(event.getId());
        if (item == null) {
            return;
        }

        if (item.isDropped()) {
            // Item was dropped by some bot
            return;
        }

        sendTeamMessage(new TCItemPicked(info.getId(), event.getId()));
        if (item.getId().equals(this.navigatingToItem)) {
            this.navigatingToItem = null;
        }
    }

    /**
     * Someone else picked an item!
     * @param event
     */
    @EventListener(eventClass = TCItemPicked.class)
    public void tcItemPicked(TCItemPicked event) {
    }

    @EventListener(eventClass = TCRequestPursueItems.class)
    public void tcRequestPursueItem(TCRequestPursueItems event) {
        if (this.navigatingToItem == null) {
            return;
        }

        for (UnrealId itemID : event.getItems()) {
            if (itemID.getStringId().equals(this.navigatingToItem.getStringId())) {
                this.sendMessageTo(new TCRefusePursueItem(this.info.getId(), itemID), event.getWho());
            }
        }
    }

    @EventListener(eventClass = TCRefusePursueItem.class)
    public void tcRefusePursueItem(TCRefusePursueItem event) {
        for (int i = 0; i < this.itemsProposedForNavigation.size(); ++i) {
            Item item = this.itemsProposedForNavigation.get(i);
            if (item.getId().getStringId().equals(event.getItem().getStringId())) {
                // TODO: may cause race conditions
                this.itemsProposedForNavigation.remove(i);
                if (this.itemsProposedForNavigation.size() == 0) {
                    this.log.warning("REFUSED ALL ITEMS");
                }
                return;
            }
        }
    }

    @EventListener(eventClass = TCStartedPursuingItem.class)
    public void tcRefusePursueItem(TCStartedPursuingItem event) {
        if (this.navigatingToItem == event.getItem()) {
            this.navigatingToItem = null;
            this.navigation.stopNavigation();
        }
    }

    @EventListener(eventClass = TCEnemyAttacked.class)
    public void tcEnemyAttacked(TCEnemyAttacked event) {
        this.currentCombatEnemy = this.players.getEnemies().get(event.getEnemy());
    }

    /**
     * PAIR UP MECHANISM:
     * Agent A sends TCInitiatingPairUp to others
     * Every non paired agent responds with TCLeaderAcceptingPairUp
     * Agent A chooses the first bot that responded (agent B) and sends TCInitiatorAcceptingPairUp
     * Only agent B receives TCInitiatorAcceptingPairUp
     * @param event
     */
    @EventListener(eventClass = TCInitiatingPairUp.class)
    public void tcInitiatingPairUp(TCInitiatingPairUp event) {
        if (this.leadBy == null && this.leading == null) {
            this.tcClient.sendToBot(event.getInitiator(), new TCLeaderAcceptingPairUp(this.info.getId()));
        }
    }

    @EventListener(eventClass = TCLeaderAcceptingPairUp.class)
    public void tcLeaderAcceptingPair(TCLeaderAcceptingPairUp event) {
        if (this.leadBy == null && this.leading == null) {
            this.leadBy = event.getLeader();
            this.tcClient.sendToBot(event.getLeader(), new TCInitiatorAcceptingPairUp(this.info.getId()));
        }
    }

    @EventListener(eventClass = TCInitiatorAcceptingPairUp.class)
    public void tcInitiatorAcceptingPairUp(TCInitiatorAcceptingPairUp event) {
        if (this.leading != null || this.leadBy != null) {
            sayGlobal(String.format("%s rejected to be lead by %s", this.info.getId(), event.getInitiator()));
            this.tcClient.sendToBot(event.getInitiator(), new TCCancelPairUp(this.info.getId()));
        } else {
            this.leading = event.getInitiator();
        }
    }

    @EventListener(eventClass = TCCancelPairUp.class)
    public void tcCancelledPairUp(TCCancelPairUp event) {
        if (this.leadBy.equals(event.getCanceledBy())) {
            this.leadBy = null;
        }
    }

    @EventListener(eventClass = TCLeaderPosition.class)
    public void tcLeaderPosition(TCLeaderPosition event) {
        if (this.leadBy.equals(event.getLeader())) {
            this.leaderLocation = event.getLocation();
        }
    }

    @EventListener(eventClass = TCFollowerPosition.class)
    public void tcFollowerPosition(TCFollowerPosition event) {
        if (this.leading.equals(event.getFollower())) {
            this.followerLocation = event.getLocation();
        }
    }

    /**
     * {@link IncomingProjectile} listener that senses that "some projectile has appeared OR moved OR disappeared".
     *
     * @param event
     */
    @ObjectClassEventListener(objectClass = IncomingProjectile.class, eventClass = WorldObjectUpdatedEvent.class)
    public void incomingProjectileUpdated(WorldObjectUpdatedEvent<IncomingProjectile> event) {
    	IncomingProjectile projectile = event.getObject();
    	// DO NOT SPAM... uncomment for debug
    	//log.info("PROJECTILE UPDATED: " + projectile);
    }
    
    /**
     * {@link Player} listener that senses that "some other bot has appeared OR moved OR disappeared"
     *
     * WARNING: this method will also be called during handshaking GB2004.
     *
     * @param event
     */
    @ObjectClassEventListener(objectClass = Player.class, eventClass = WorldObjectUpdatedEvent.class)
    public void playerUpdated(WorldObjectUpdatedEvent<Player> event) {
    	if (info.getLocation() == null) {
    		// HANDSHAKING GB2004
    		return;
    	}
    	Player player = event.getObject();    	
    	// DO NOT SPAM... uncomment for debug
    	//log.info("PLAYER UPDATED: " + player.getId().getStringId());
    }
        
    
    /**
     * {@link TeamScore} listener that senses changes within scoring.
     *
     * @param event
     */
    @ObjectClassEventListener(objectClass = TeamScore.class, eventClass = WorldObjectUpdatedEvent.class)
    public void teamScoreUpdated(WorldObjectUpdatedEvent<TeamScore> event) {
    	switch (event.getObject().getTeam()) {
    	case AgentInfo.TEAM_RED: 
    		log.info("RED TEAM SCORE UPDATED: " + event.getObject());
    		break;
    	case AgentInfo.TEAM_BLUE:
    		log.info("BLUE TEAM SCORE UPDATED: " + event.getObject());
    		break;
    	}
    }
    
    
    private long selfLastUpdateStartMillis = 0;
    private long selfTimeDelta = 0;
    
    /**
     * {@link Self} object has been updated. This update is received about every 50ms. You can use this update
     * to fine-time some of your behavior like "weapon switching". I.e. SELF is updated every 50ms while LOGIC is invoked every 250ms.
     * 
     * Note that during "SELF UPDATE" only information about your bot location/rotation ({@link Self}) is updated. All other visibilities 
     * remains the same as during last {@link #logic()}.
     * 
     * Note that new {@link NavMeshNavigation} is using SELF UPDATES to fine-control the bot's navigation.
     * 
     * @param event
     */
    @ObjectClassEventListener(objectClass = Self.class, eventClass = WorldObjectUpdatedEvent.class)
    public void selfUpdated(WorldObjectUpdatedEvent<Self> event) {
    	if (lastLogicStartMillis == 0) {
    		// IGNORE ... logic has not been executed yet...
    		return;
    	}
    	if (selfLastUpdateStartMillis == 0) {
    		selfLastUpdateStartMillis = System.currentTimeMillis();
    		return;
    	}
    	long selfUpdateStartMillis = System.currentTimeMillis(); 
    	selfTimeDelta = selfUpdateStartMillis  - selfLastUpdateStartMillis;
    	selfLastUpdateStartMillis = selfUpdateStartMillis;

    	try {
    		
    		// YOUR CODE HERE
    		
    	} catch (Exception e) {
    		// MAKE SURE THAT YOUR BOT WON'T FAIL!
    		log.info(ExceptionToString.process(e));
    	} finally {
    		//log.info("---[ SELF UPDATE END ]---");
    	}
    	
    }

    // ==============
    // MAIN BOT LOGIC
    // ==============
    
    /**
     * Method that is executed only once before the first {@link TDMBot#logic()} 
     */
    @Override
    public void beforeFirstLogic() {
    	lastLogicStartMillis = System.currentTimeMillis();
    	if (DRAW_NAVMESH && botInstance == 0) {
    		boolean drawNavmesh = false;
    		synchronized(CLASS_MUTEX) {
    			if (!navmeshDrawn) {
    				drawNavmesh = true;
    				navmeshDrawn = true;
    			}
    		}
    		if (drawNavmesh) {
    			log.warning("!!! DRAWING NAVMESH !!!");
    			navMeshModule.getNavMeshDraw().draw(true, true);
    			navmeshDrawn  = true;
    			log.warning("NavMesh drawn, waiting a bit to finish the drawing...");
    		}    		
    	}
    }
    
    private long lastLogicStartMillis = 0;
    private long lastLogicEndMillis = 0;
    private long timeDelta = 0;
    
    /**
     * Main method that controls the bot - makes decisions what to do next. It
     * is called iteratively by Pogamut engine every time a synchronous batch
     * from the environment is received. This is usually 4 times per second.
     * 
     * This is a typical place from where you start coding your bot. Even though bot
     * can be completely EVENT-DRIVEN, the reactive aproach via "ticking" logic()
     * method is more simple / straight-forward.
     */
    @Override
    public void logic() {
    	long logicStartTime = System.currentTimeMillis();
    	try {
	    	// LOG VARIOUS INTERESTING VALUES
    		logLogicStart();
	    	logMind();
	    	
	    	// UPDATE TEAM COMM
	    	commItems.update();
	    	commObjectUpdates.update();
	    	
	    	// MAIN BOT LOGIC
	    	botLogic();
	    	
    	} catch (Exception e) {
    		// MAKE SURE THAT YOUR BOT WON'T FAIL!
    		log.info(ExceptionToString.process(e));
    		// At this point, it is a good idea to reset all state variables you have...
    		reset();
    	} finally {
    		// MAKE SURE THAT YOUR LOGIC DOES NOT TAKE MORE THAN 250 MS (Honestly, we have never seen anybody reaching even 150 ms per logic cycle...)
    		// Note that it is perfectly OK, for instance, to count all path-distances between you and all possible pickup-points / items in the game
    		// sort it and do some inference based on that.
    		long timeSpentInLogic = System.currentTimeMillis() - logicStartTime;
    		if (timeSpentInLogic >= 245) {
    			log.warning("!!! LOGIC TOO DEMANDING !!!");
    		}
    		lastLogicEndMillis = System.currentTimeMillis();
    	}    	
    }
    
    public void botLogic() {
        --waitingForItemPursueAcceptance;
        ++time;
        leadingBehavior();

        if (this.combatBehavior()) {
            return;
        }
        if (this.leadByBehavior()) {
            return;
        }
        if (this.lowHealthBehavior()) {
            return;
        }

        checkPairingBehavior();
        pursueItemsBehavior();
    }

    private boolean combatBehavior() {
        if (currentCombatEnemy != null) {
            this.move.turnTo(currentCombatEnemy);
        }
        Player nearestEnemy = players.getNearestVisibleEnemy();
        if (nearestEnemy == null) {
            this.shoot.stopShooting();
            return false;
        }

        if (currentCombatEnemy == null && this.info.getHealth() < LOW_HEALTH) {
            return false;
        }

        // TODO: also change behavior when the currentCombatEnemy is dead
        if (currentCombatEnemy == null || !currentCombatEnemy.isVisible()) {
            currentCombatEnemy = nearestEnemy;
        }

        if (currentCombatEnemy.isVisible()) {
            // TODO: move around so we won't be easy target (straffing maybe)
            WeaponPref currentWeaponPref = this.weaponPrefs.getWeaponPreference(currentCombatEnemy);
            if (currentWeaponPref.getWeapon() == UT2004ItemType.ROCKET_LAUNCHER) {
                this.shoot.shoot(this.weaponPrefs, currentCombatEnemy.getLocation());
                // TODO: how to find out whether I hit my teammate
            } else {
                this.shoot.shoot(this.weaponPrefs, currentCombatEnemy);
            }

            this.navigation.navigate(currentCombatEnemy);
            this.navigation.setFocus(currentCombatEnemy);
            // this.sendTeamOthersMessage(new TCEnemyAttacked(this.info.getId(), currentCombatEnemy.getId()));
        }

        return true;
    }

    private boolean lowHealthBehavior() {
        if (this.leading != null || this.leadBy != null) {
            return false;
        }

        if (this.info.getHealth() < LOW_HEALTH) {
            if (this.players.getNearestVisibleEnemy() != null && this.senses.isBeingDamaged()) {
                this.weaponry.changeWeapon(UT2004ItemType.SHIELD_GUN);
                this.shoot.shootSecondary(this.players.getNearestVisibleEnemy());
            }
        }

        return false;
    }

    private int updateCounter = 0;
    private static final int UPDATE_EVERY = 1;

    private void leadingBehavior() {
        if (this.leading != null) {
            if (updateCounter == 0) {
                this.tcClient.sendToBot(this.leading, new TCLeaderPosition(this.info.getId(), this.info.getLocation()));
            }
            updateCounter = (updateCounter + 1) % UPDATE_EVERY;
        }
    }

    private boolean leadByBehavior() {
        if (this.leadBy != null) {
            if (updateCounter == 0) {
                this.tcClient.sendToBot(this.leadBy, new TCFollowerPosition(this.info.getId(), this.info.getLocation()));
            }
            updateCounter = (updateCounter + 1) % UPDATE_EVERY;
            if (this.leaderLocation == null) {
                return false;
            }
            if (this.leaderLocation.getLocation().getDistance(this.info.getLocation()) > FOLLOWER_DISTANCE_THRESHOLD) {
                this.navigation.navigate(this.leaderLocation);
                return true;
            } else {
                this.navigation.stopNavigation();
                return true;
            }
        }

        return false;
    }

    private void checkPairingBehavior() {
        if (shouldPairUp()) {
            sayGlobal(String.format("%s asking for pair up", this.info.getId()));
            sendTeamOthersMessage(new TCInitiatingPairUp(this.info.getId()));
        }
    }

    private int waitingForItemPursueAcceptance = 0;
    private List<Item> itemsProposedForNavigation = new ArrayList<>();

    private void pursueItemsBehavior() {
        this.move.turnHorizontal(60);

        if (this.leading != null) {
            if (this.followerLocation == null || this.followerLocation.getLocation().getDistance(this.info.getLocation()) > LEADING_DISTANCE_THRESHOLD) {
                this.navigation.stopNavigation();
                return;
            }
        }

        if (!navigation.isNavigating() || !this.stillWantsToPursueItem()) {
            if (waitingForItemPursueAcceptance <= 0) {
                if (itemsProposedForNavigation.size() > 0) {
                    startNavigationTo(this.getFirstNonNull(itemsProposedForNavigation));
                    this.itemsProposedForNavigation.clear();
                } else {
                    itemsProposedForNavigation = this.getInterestingItems();
                    waitingForItemPursueAcceptance = 1;
                    sendTeamOthersMessage(new TCRequestPursueItems(this.info.getId(), itemsProposedForNavigation.stream().map(Item::getId).collect(Collectors.toList())));
                }
            }
        }
    }

    private boolean stillWantsToPursueItem() {
        return this.items.getSpawnedItems().containsValue(this.items.getItem(this.navigatingToItem));
    }

    private void startNavigationTo(Item item) {
        this.navigation.navigate(item.getLocation());
        if (this.navigatingToItem == null || this.navigatingToItem != item.getId()) {
            navigatingToItem = item.getId();
        }
        this.sendTeamOthersMessage(new TCStartedPursuingItem(this.info.getId(), this.navigatingToItem));
    }

    private boolean shouldPairUp() {
		if (this.leadBy != null || this.leading != null) {
			return false;
		}
        Set<ItemType> loadedWeapons = new HashSet<>();
		loadedWeapons.addAll(this.weaponry.getLoadedWeapons().keySet());
		loadedWeapons.removeAll(this.getWeakWeapons());
        return loadedWeapons.size() == 0;
    }

    private List<Item> getInterestingItems() {
        // items.getSpawnedItems() <- BASED ON OUR BELIEF!!!!!
        Collection<Item> interesting = MyCollections.getFiltered(items.getSpawnedItems().values(), new IFilter<Item>() {
            @Override
            public boolean isAccepted(Item item) {
                if (item == null) {
                    return false;
                }
                ItemType type = item.getType();
                return (type.getCategory() == ItemType.Category.ARMOR || type.getCategory() == ItemType.Category.SHIELD || type.getCategory() == ItemType.Category.WEAPON);
            }
        });

        if (interesting.size() == 0) {
            return new ArrayList<>();
        }

        List<Item> sortedItems = new ArrayList<>(interesting);
        sortedItems.sort(new Comparator<Item>() {
            @Override
            public int compare(Item o1, Item o2) {
                double o1Distance = navMeshModule.getAStarPathPlanner().getDistance(o1, info.getLocation());
                double o2Distance = navMeshModule.getAStarPathPlanner().getDistance(o2, info.getLocation());
                return (int)((o1Distance - o2Distance) * 1000);
            }
        });

        List<Item> firstFour = new ArrayList<>();
        for (int i = 0; i < Math.min(sortedItems.size(), 4); ++i) {
            firstFour.add(sortedItems.get(i));
        }

        return firstFour;
    }

    private Set<ItemType> getWeakWeapons() {
        return new HashSet<ItemType>() {{
            add(UT2004ItemType.SHIELD_GUN);
            add(UT2004ItemType.ASSAULT_RIFLE);
            add(UT2004ItemType.BIO_RIFLE);
            add(UT2004ItemType.LINK_GUN);
        }};
    }

    private Item getFirstNonNull(List<Item> items) {
        for (int i = 0; i < items.size(); ++i) {
            if (this.items.getItem(items.get(i).getId()) != null) {
                return this.items.getItem(items.get(i).getId());
            }
        }

        return null;
    }


    public void reset() {
    	navigationPathDrawn = false;
    }

    private void sendTeamMessage(TCMessageData message) {
        // log.info("[MESSAGE SENT] " + message.toString());
        tcClient.sendToTeam(message);
    }

    private void sendTeamOthersMessage(TCMessageData message) {
        // log.info("[MESSAGE SENT OTHERS] " + message.toString());
        tcClient.sendToTeamOthers(message);
    }

    private void sendMessageTo(TCMessageData message, UnrealId id) {
        tcClient.sendToBot(id, message);
    }

    private void sayGlobal(String msg) {
        // Simple way to send msg into the UT2004 chat
        body.getCommunication().sendGlobalTextMessage(msg);
        // And user log as well
        log.info("[SAY] " + msg);
    }
    
    // ===========
    // MIND LOGGER
    // ===========
    
    /**
     * It is good to log that the logic has started so you can then retrospectively check the batches.
     */
    public void logLogicStart() {
    	long logicStartTime = System.currentTimeMillis();
    	timeDelta = logicStartTime - lastLogicStartMillis;
    	lastLogicStartMillis = logicStartTime;
    }
    
    /**
     * It is good in-general to periodically log anything that relates to your's {@link TDMBot#logic()} decision making.
     * 
     * You might consider exporting these values to some custom Swing window (you crete for yourself) that will be more readable.
     */
    public void logMind() {
    }
    
    // =====================================
    // UT2004 DEATH-MATCH INTERESTING GETTERS
    // ======================================
    
    /**
     * Returns path-nearest {@link NavPoint} that is covered from 'enemy'. Uses {@link UT2004BotModuleController#getVisibility()}.
     * @param enemy
     * @return
     */
    public NavPoint getNearestCoverPoint(Player enemy) {
    	if (!visibility.isInitialized()) {
    		log.warning("VISIBILITY NOT INITIALIZED: returning random navpoint");    		
    		return MyCollections.getRandom(navPoints.getNavPoints().values());
    	}
    	List<NavPoint> coverPoints = new ArrayList<NavPoint>(visibility.getCoverNavPointsFrom(enemy.getLocation()));
    	return fwMap.getNearestNavPoint(coverPoints, info.getNearestNavPoint());
    }
    
    /**
     * Returns whether 'item' is possibly spawned (to your current knowledge).
     * @param item
     * @return
     */
    public boolean isPossiblySpawned(Item item) {
    	return items.isPickupSpawned(item);
    }
    
    /**
     * Returns whether you can actually pick this 'item', based on "isSpawned" and "isPickable" in your current state and knowledge.
     */
    public boolean isCurrentlyPickable(Item item) {
    	return isPossiblySpawned(item) && items.isPickable(item);
    }
        
    // ==========
    // RAYCASTING
    // ==========
    
    /**
     * Performs a client-side raycast against UT2004 map geometry.
     * 
     * It is not sensible to perform more than 1000 raycasts per logic() per bot.
     *  
     * NOTE THAT IN ORDER TO USE THIS, you have to rename "map_" folder into "map" ... so it would load the level geometry.
     * Note that loading a level geometry up takes quite a lot of time (>60MB large BSP tree...). 
     *  
     * @param from
     * @param to
     * @return
     */
    public RayCastResult raycast(ILocated from, ILocated to) {
    	if (!levelGeometryModule.isInitialized()) {
    		throw new RuntimeException("Level Geometry not initialized! Cannot RAYCAST!");
    	}
    	return levelGeometryModule.getLevelGeometry().rayCast(from.getLocation(), to.getLocation());
    }
    
    /**
     * Performs a client-side raycast against NavMesh in 'direction'. Returns distance of the edge in given 'direction' sending the ray 'from'.
     * @param from
     * @param direction
     * @return
     */
    public double raycastNavMesh(ILocated from, Vector2D direction) {
    	if (!navMeshModule.isInitialized()) {
    		log.severe("NavMesh not initialized! Cannot RAYCAST-NAVMESH!");
    		return 0;
    	}
    	ClearanceLimit limit = navMeshModule.getClearanceComputer().findEdge(from.getLocation(), direction);
    	if (limit == null) return Double.POSITIVE_INFINITY;
    	return from.getLocation().getDistance(limit.getLocation());
    }
    
    // =======
    // DRAWING
    // =======
    
    public void drawNavigationPath(boolean clearAll) {
    	if (clearAll) {
    		draw.clearAll();
    	}
    	List<ILocated> path = navigation.getCurrentPathCopy();
    	for (int i = 1; i < path.size(); ++i) {
    		draw.drawLine(path.get(i-1), path.get(i));
    	}
    }
    
    public void drawPath(IPathFuture<? extends ILocated> pathFuture, boolean clearAll) {
    	if (clearAll) {
    		draw.clearAll();
    	}
    	List<? extends ILocated> path = pathFuture.get();
    	for (int i = 1; i < path.size(); ++i) {
    		draw.drawLine(path.get(i-1), path.get(i));
    	}
    }
    
    public void drawPath(IPathFuture<? extends ILocated> pathFuture, Color color, boolean clearAll) {
    	if (clearAll) {
    		draw.clearAll();
    	}
    	if (color == null) color = Color.WHITE;
    	List<? extends ILocated> path = pathFuture.get();
    	for (int i = 1; i < path.size(); ++i) {
    		draw.drawLine(color, path.get(i-1), path.get(i));
    	}
    }
    
    // =====
    // AStar
    // =====
    
    private NavPoint lastAStarTarget = null;
    
    public boolean navigateAStarPath(NavPoint targetNavPoint) {
        if (lastAStarTarget == targetNavPoint) {
            if (navigation.isNavigating()) return true;
        }
        PrecomputedPathFuture<ILocated> path = getAStarPath(targetNavPoint);
        if (path == null) {
            navigation.stopNavigation();
            return false;
        }
        lastAStarTarget = targetNavPoint;
        navigation.navigate(path);
        return true;
    }
    
    private IPFMapView<NavPoint> mapView = new IPFMapView<NavPoint>() {

        @Override
        public Collection<NavPoint> getExtraNeighbors(NavPoint node, Collection<NavPoint> mapNeighbors) {
            return null;
        }

        @Override
        public int getNodeExtraCost(NavPoint node, int mapCost) {
            return 0;
        }

        @Override
        public int getArcExtraCost(NavPoint nodeFrom, NavPoint nodeTo, int mapCost) {
            return 0;
        }

        @Override
        public boolean isNodeOpened(NavPoint node) {
            return true;
        }

        @Override
        public boolean isArcOpened(NavPoint nodeFrom, NavPoint nodeTo) {
            return true;
        }
    };
    
    private PrecomputedPathFuture<ILocated> getAStarPath(NavPoint targetNavPoint) {
        NavPoint startNavPoint = info.getNearestNavPoint();
        AStarResult<NavPoint> result = aStar.findPath(startNavPoint, targetNavPoint, mapView);
        if (result == null || !result.isSuccess()) return null;
        PrecomputedPathFuture path = new PrecomputedPathFuture(startNavPoint, targetNavPoint, result.getPath());
        return path;
    }
    
    // ===========
    // MAIN METHOD
    // ===========
    
    /**
     * Main execute method of the program.
     * 
     * @param args
     * @throws PogamutException
     */
    public static void main(String args[]) throws PogamutException {
    	// Starts N agents of the same type at once
    	// WHEN YOU WILL BE SUBMITTING YOUR CODE, MAKE SURE THAT YOU RESET NUMBER OF STARTED AGENTS TO '1' !!!
    	// => during the development, please use {@link Starter_Bots} instead to ensure you will leave "1" in here
    	new UT2004BotRunner(TDMBot.class, "TDMBot").setMain(true).setLogLevel(Level.INFO).startAgents(1);
    }
    
}
