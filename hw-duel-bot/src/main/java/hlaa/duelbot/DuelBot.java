package hlaa.duelbot;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.EventListener;
import cz.cuni.amis.pogamut.base.utils.guice.AgentScoped;
import cz.cuni.amis.pogamut.base.utils.math.DistanceUtils;
import cz.cuni.amis.pogamut.base3d.worldview.object.ILocated;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.NavigationState;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004BotModuleController;
import cz.cuni.amis.pogamut.ut2004.communication.messages.ItemType;
import cz.cuni.amis.pogamut.ut2004.communication.messages.UT2004ItemType;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Initialize;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.*;
import cz.cuni.amis.pogamut.ut2004.utils.UT2004BotRunner;
import cz.cuni.amis.utils.exception.PogamutException;
import cz.cuni.amis.utils.flag.FlagListener;

@AgentScoped
public class DuelBot extends UT2004BotModuleController {

    private static final int MAX_PURSUE_COUNT = 35;
    private static final int LOW_HEALTH = 40;

	private long lastLogicTime = -1;
    private long logicIterationNumber = 0;
    private Player currentCombatEnemy = null;
    private int pursueCount = 0;

    /**
     * Here we can modify initializing command for our bot, e.g., sets its name or skin.
     *
     * @return instance of {@link Initialize}
     */
    @Override
    public Initialize getInitializeCommand() {  
    	return new Initialize().setName("DuelBot").setDesiredSkill(6);
    }

    @Override
    public void botInitialized(GameInfo gameInfo, ConfigChange currentConfig, InitedMessage init) {
    	bot.getLogger().getCategory("Yylex").setLevel(Level.OFF);
    }
    
    @Override
    public void botFirstSpawn(GameInfo gameInfo, ConfigChange config, InitedMessage init, Self self) {
        navigation.addStrongNavigationListener(new FlagListener<NavigationState>() {
			@Override
			public void flagChanged(NavigationState changedValue) {
				navigationStateChanged(changedValue);
			}
        });
    }
    
    private void navigationStateChanged(NavigationState changedValue) {
    	switch(changedValue) {
    	case TARGET_REACHED:
    		break;
		case PATH_COMPUTATION_FAILED:
			break;
		case STUCK:
			break;
		}
    }
    
    @Override
    public void beforeFirstLogic() {
        this.setWeaponPrefs();
    }
        
    @Override
    public void logic() throws PogamutException {
        if (lastLogicTime < 0) {
            lastLogicTime = System.currentTimeMillis();
            return;
        }

        log.info("---LOGIC: " + (++logicIterationNumber) + " / D=" + (System.currentTimeMillis() - lastLogicTime) + "ms ---");
        lastLogicTime = System.currentTimeMillis();

        // THE BOT'S LOGIC
        if (this.combatBehavior()) {
            return;
        } else if (this.lowHealthBehavior()) {
            return;
        } else if (this.gotHitBehavior()) {
            return;
        } else if (this.pursueBehavior()) {
            return;
        }
        tryFireAtEnemyBehavior();
        collectItemsBehavior();
    }

    private boolean combatBehavior() {
        if (currentCombatEnemy != null) {
            this.move.turnTo(currentCombatEnemy);
        }
        Player nearestEnemy = players.getNearestVisiblePlayer();
        if (nearestEnemy == null) {
            this.shoot.stopShooting();
            return false;
        }
        log.info("[STATE] Combat!");

        if (currentCombatEnemy == null && this.info.getHealth() < LOW_HEALTH) {
            return false;
        }

        // TODO: also change behavior when the currentCombatEnemy is dead
        if (currentCombatEnemy == null || !currentCombatEnemy.isVisible()) {
            currentCombatEnemy = nearestEnemy;
        }

        if (currentCombatEnemy.isVisible()) {
            // TODO: move around so we won't be easy target (straffing maybe)
            this.shoot.shoot(this.weaponPrefs, currentCombatEnemy);
            this.navigation.navigate(currentCombatEnemy);
        }

        return true;
    }

    private boolean lowHealthBehavior() {
        if (this.info.getHealth() < LOW_HEALTH) {
            if (this.players.getNearestVisibleEnemy() != null && this.senses.isBeingDamaged()) {
                this.weaponry.changeWeapon(UT2004ItemType.SHIELD_GUN);
                this.shoot.shootSecondary(this.players.getNearestVisibleEnemy());
            }
        }

        return false;
    }

    private boolean gotHitBehavior() {
        if (senses.isBeingDamaged()) {
            log.info("[STATE] Got hit");
            this.move.turnHorizontal(60);
            return true;
        }

        return false;
    }

    private boolean pursueBehavior() {
        this.shoot.stopShooting();
        if (this.currentCombatEnemy != null && this.weaponry.hasLoadedWeapon()) {
            if (this.pursueCount > MAX_PURSUE_COUNT) {
                this.reset();
                return false;
            } else {
                log.info("[STATE] Pursuing " + currentCombatEnemy.getName());
                ++this.pursueCount;
                return true;
            }
        }
        return false;
    }

    private void tryFireAtEnemyBehavior() {
        Player player = players.getNearestVisibleEnemy();
        if (player != null) {
            this.shoot.shoot(this.weaponPrefs, player);
        }
    }

    private void collectItemsBehavior() {
        log.info("[STATE] Collecting items");

        if (!this.navigation.isNavigating()) {
            Collection<Item> pickableItems = items.getSpawnedItems().values().stream().filter(
                    item -> items.isPickable(item) && isInterestingItemToPursue(item)
            ).collect(Collectors.toList());

            Item nearestPickableItem = DistanceUtils.getNearest(pickableItems, info.getLocation(), new DistanceUtils.IGetDistance<Item>() {
                @Override
                public double getDistance(Item item, ILocated target) {
                    return navMeshModule.getAStarPathPlanner().getDistance(item, target);
                }
            });

            this.navigation.navigate(nearestPickableItem);
            this.move.turnHorizontal(60);
        }
    }

    private void reset() {
        this.pursueCount = 0;
        this.currentCombatEnemy = null;
    }

    private boolean isInterestingItemToPursue(Item item) {
        ItemType.Category itemCategory = item.getType().getCategory();

        if (this.info.getHealth() < LOW_HEALTH) {
            return itemCategory == ItemType.Category.SHIELD || itemCategory == ItemType.Category.ARMOR || itemCategory == ItemType.Category.HEALTH;
        } else {
            return itemCategory == ItemType.Category.WEAPON || itemCategory == ItemType.Category.SHIELD
                    || itemCategory == ItemType.Category.ARMOR;
        }
    }

    // ==============
    // EVENT HANDLERS
    // ==============
    
    @EventListener(eventClass=ItemPickedUp.class)
    public void itemPickedUp(ItemPickedUp event) {
    	if (info.getSelf() == null) {
    	    return; // ignore the first equipment...
        }
    }
    
    @EventListener(eventClass=BotDamaged.class)
    public void botDamaged(BotDamaged event) {
    }

    @Override
    public void botKilled(BotKilled event) {
        sayGlobal("I was KILLED!");
    }

    private static final int EXTRA_CLOSE = 80;
    private static final int CLOSE_RANGE = 400;
    private static final int MEDIUM_RANGE = 1150;
    private static final int MAX_RANGE = 20000000; // random large enough (hopefully) constant

    private void setWeaponPrefs() {
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

    // =========
    // UTILITIES
    // =========
    
    private void sayGlobal(String msg) {
    	// Simple way to send msg into the UT2004 chat
    	body.getCommunication().sendGlobalTextMessage(msg);
    	// And user log as well
    	log.info(msg);
    }
    
    // ===========
    // MAIN METHOD
    // ===========
    
    public static void main(String args[]) throws PogamutException {
        new UT2004BotRunner(     // class that wrapps logic for bots executions, suitable to run single bot in single JVM
                DuelBot.class,   // which UT2004BotController it should instantiate
                "DuelBot"        // what name the runner should be using
        ).setMain(true)          // tells runner that is is executed inside MAIN method, thus it may block the thread and watch whether agent/s are correctly executed
         .startAgents(1);        // tells the runner to start 2 agent
    }
}
