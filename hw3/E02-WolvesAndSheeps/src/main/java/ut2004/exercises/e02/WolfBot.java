package ut2004.exercises.e02;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.EventListener;
import cz.cuni.amis.pogamut.base.utils.guice.AgentScoped;
import cz.cuni.amis.pogamut.base3d.worldview.object.Location;
import cz.cuni.amis.pogamut.ut2004.agent.module.sensor.AgentInfo;
import cz.cuni.amis.pogamut.ut2004.agent.module.utils.UT2004Skins;
import cz.cuni.amis.pogamut.ut2004.bot.command.AdvancedLocomotion;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004BotModuleController;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Initialize;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.*;
import cz.cuni.amis.pogamut.ut2004.utils.UT2004BotRunner;
import cz.cuni.amis.utils.exception.PogamutException;

/**
 * EXERCISE 02
 * -----------
 * 
 * Implement a WolfBot(s) that will be able to catch all the sheeps as fast as possible!
 * 
 * No shooting allowed, no speed reconfiguration allowed.
 * 
 * Just use {@link AdvancedLocomotion#moveTo(cz.cuni.amis.pogamut.base3d.worldview.object.ILocated)}, 
 * {@link AdvancedLocomotion#strafeTo(cz.cuni.amis.pogamut.base3d.worldview.object.ILocated, cz.cuni.amis.pogamut.base3d.worldview.object.ILocated)}
 * {@link AdvancedLocomotion#jump()} and {@link AdvancedLocomotion#dodge(cz.cuni.amis.pogamut.base3d.worldview.object.Location, boolean)} 
 * and alikes to move your bot!
 * 
 * To start scenario:
 * 1. blend directory ut2004 with you UT2004 installation
 * 2. start DM-TagMap using startGamebotsDMServer-DM-TagMap.bat
 * 3. start SheepBot
 * -- it will launch 12 agents (Sheeps) into the game
 * 4. start WolfBot
 * 5. one of your WolfBot has to say "start" to start the match or "restart" to re/start the match
 * 
 * Behavior tips:
 * 1. in this exercise, you can implement the communication using statics, i.e., both your Wolfs are running
 *    within the same JVM - make use of that - and watch out for race-conditions (synchronized(MUTEX){ ... } your critical stuff)
 * 2. first, you have to check that both your wolfs are kicking and you should issue "start" message
 * 3. do not start playing before that ;) ... check {@link Utils#gameRunning} whether the game is running
 * 4. you catch the sheep by bumping to it (getting near to it...)
 * 5. count how many sheeps are still alive (via implementing PlayerKilled listener correctly) to know when to restart the match!
 *    -- how fast can you take them out all?
 * 
 * 
 * @author Jakub Gemrot aka Jimmy aka Kefik
 */
@AgentScoped
public class WolfBot extends UT2004BotModuleController {
    
	private static AtomicInteger INSTANCE = new AtomicInteger(1);
	
	private static Object MUTEX = new Object();

	private static int ALPHA = 1;
	private static int BETA = 2;
	private static AgentInfo AlphaInstance;

	private int instance = 0;
	
    private int logicIterationNumber;

	/**
     * Here we can modify initializing command for our bot, e.g., sets its name or skin.
     *
     * @return instance of {@link Initialize}
     */
    @Override
    public Initialize getInitializeCommand() {  
    	instance = INSTANCE.getAndIncrement();
    	return new Initialize().setName("WolfBot-" + instance).setSkin(UT2004Skins.getSkin());
    }

    /**
     * Bot is ready to be spawned into the game; configure last minute stuff in here
     *
     * @param gameInfo information about the game type
     * @param config information about configuration
     * @param init information about configuration
     */
    @Override
    public void botInitialized(GameInfo gameInfo, ConfigChange currentConfig, InitedMessage init) {
    	// ignore any Yylex whining...
    	bot.getLogger().getCategory("Yylex").setLevel(Level.OFF);
    }
    
    /**
     * This method is called only once, right before actual logic() method is called for the first time.
     */
    @Override
    public void beforeFirstLogic() {
        if (this.instance == ALPHA)  {
            AlphaInstance = this.info;
        }
    }
    
    /**
     * Say something through the global channel + log it into the console...    
     * @param msg
     */
    private void sayGlobal(String msg) {
    	// Simple way to send msg into the UT2004 chat
    	body.getCommunication().sendGlobalTextMessage(msg);
    	// And user log as well
    	log.info(msg);
    }
    
    @EventListener(eventClass=GlobalChat.class)
    public void chatReceived(GlobalChat msg) {
        Utils.handleMessage(msg);
    }
    
    /**
     * Some other player has been killed.
     * @param event
     */
    @EventListener(eventClass=PlayerKilled.class)
    public void playerKilled(PlayerKilled event) {

    }
    
    private Player chasedSheep;

    @Override
    public void logic() throws PogamutException {
        if (this.instance == ALPHA) {
            this.logicAlpha();
        } else {
            this.logicBeta();
        }
    }

    private void logicAlpha() {
        /*
        if (chasedSheep == null) {
            log.info("[Alpha] No chased sheep.");
        } else {
            log.info("[Alpha] Chased sheep: " + chasedSheep.getId());
            log.info("[Alpha] Chased sheep is visible: " + chasedSheep.isVisible());
        }
        */
        if (chasedSheep == null || !chasedSheep.isVisible()) {
            this.acquireTarget();
        } else {
            this.move.strafeTo(chasedSheep, chasedSheep);
        }
        tryDodgeSomeone();
    }

    private void logicBeta() {
        followAlpha();
        tryDodgeSomeone();
    }

    private void acquireTarget() {
        Player nearestSheep = getNearestVisibleSheep();
        if (nearestSheep == null) {
            this.move.turnHorizontal(30);
        } else {
            chasedSheep = nearestSheep;
        }
    }

    private void followAlpha() {
        if (AlphaInstance != null) {
            this.move.moveTo(getLocationRightTo(AlphaInstance.getLocation()));
            this.move.turnHorizontal(30);
        }
    }

    private void tryDodgeSomeone() {
        Player nearestSheep = getNearestVisibleSheep();
        if (nearestSheep == null) {
            return;
        }

        Location nearestSheepLocation = nearestSheep.getLocation();
        Location myLocationn = this.info.getLocation();
        double nearestSheepDistance = Location.getDistance(nearestSheepLocation, myLocationn);
        log.info("[Nearest Sheep] " + nearestSheepDistance);
        if (nearestSheepDistance < 250) {
            this.move.dodge(nearestSheepLocation.sub(myLocationn), true);
        }
    }

    private Player getNearestVisibleSheep() {
        if (this.players.getVisibleEnemies().isEmpty()) {
            return null;
        }

        Player nearestVisibleSheep = null;
        for (Player player : this.players.getVisibleEnemies().values()) {
            if (player.getName().contains("Sheep")) {
                if (nearestVisibleSheep == null) {
                    nearestVisibleSheep = player;
                } else {
                    if (Location.getDistance(this.info.getLocation(), player.getLocation()) < Location.getDistance(this.info.getLocation(), nearestVisibleSheep.getLocation())) {
                        nearestVisibleSheep = player;
                    }
                }
            }
        }

        return nearestVisibleSheep;
    }

    private Location getLocationRightTo(Location location) {
        return new Location(location.x, location.y + 380, location.z);
    }

    /**
     * This method is called when the bot is started either from IDE or from command line.
     *
     * @param args
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
	public static void main(String args[]) throws PogamutException {
        new UT2004BotRunner(      // class that wrapps logic for bots executions, suitable to run single bot in single JVM
                WolfBot.class,  // which UT2004BotController it should instantiate
                "WolfBot"       // what name the runner should be using
        ).setMain(true)           // tells runner that is is executed inside MAIN method, thus it may block the thread and watch whether agent/s are correctly executed
         .startAgents(2);         // tells the runner to start 1 agent
    }
}
