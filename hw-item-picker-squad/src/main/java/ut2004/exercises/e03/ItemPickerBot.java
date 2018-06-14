package ut2004.exercises.e03;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;

import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.EventListener;
import cz.cuni.amis.pogamut.base.utils.guice.AgentScoped;
import cz.cuni.amis.pogamut.base.utils.math.DistanceUtils;
import cz.cuni.amis.pogamut.base3d.worldview.object.ILocated;
import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.agent.module.sensor.Items;
import cz.cuni.amis.pogamut.ut2004.agent.module.utils.UT2004Skins;
import cz.cuni.amis.pogamut.ut2004.communication.messages.ItemType;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Initialize;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.ConfigChange;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.GameInfo;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.GlobalChat;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.InitedMessage;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Item;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.ItemPickedUp;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Self;
import cz.cuni.amis.pogamut.ut2004.teamcomm.bot.UT2004BotTCController;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;
import cz.cuni.amis.pogamut.ut2004.utils.UT2004BotRunner;
import cz.cuni.amis.utils.IFilter;
import cz.cuni.amis.utils.collections.MyCollections;
import cz.cuni.amis.utils.exception.PogamutException;
import ut2004.exercises.e03.comm.TCItemPicked;
import ut2004.exercises.e03.comm.TCRefusePursueItem;
import ut2004.exercises.e03.comm.TCRequestPursueItems;

/**
 * EXERCISE 03
 * -----------
 * 
 * Your task is to pick all interesting items.
 * 
 * Interesting items are:
 * -- weapons
 * -- shields
 * -- armors
 * 
 * Target maps, where to test your squad are:
 * -- DM-1on1-Albatross
 * -- DM-1on1-Roughinery-FPS
 * -- DM-Rankin-FE
 * 
 * To start scenario:
 * 1. start either of startGamebotsTDMServer-DM-1on1-Albatross.bat, startGamebotsTDMServer-DM-1on1-Roughinery-FPS.bat, startGamebotsTDMServer-DM-Rankin-FE.bat
 * 2. start team communication view running {@link TCServerStarter#main(String[])}.
 * 3. start your squad
 * 4. use ItemPickerChecker methods to query the state of your run
 * 
 * Behavior tips:
 * 1. be sure not to repick item you have already picked
 * 2. be sure not to repick item some other bot has already picked (use {@link #tcClient} for that)
 * 3. do not try to pick items you are unable to, check by {@link Items#isPickable(Item)}
 * 4. be sure not to start before {@link ItemPickerChecker#isRunning()}
 * 5. you may terminate your bot as soon as {@link ItemPickerChecker#isVictory()}.
 * 
 * WATCH OUT!
 * 1. All your bots must be run from the same JVM, but they must not communicate via STATICs!
 * 
 * @author Jakub Gemrot aka Jimmy aka Kefik
 */
@AgentScoped
public class ItemPickerBot extends UT2004BotTCController {
    
	private static AtomicInteger INSTANCE = new AtomicInteger(1);
	
	private static Object MUTEX = new Object();
	
	private int instance = 0;

    private int logicIterationNumber;

    private long lastLogicTime = -1;

    private Set<UnrealId> pickedItems = new HashSet<UnrealId>();
    private UnrealId navigatingToItem = null;

	/**
     * Here we can modify initializing command for our bot, e.g., sets its name or skin.
     *
     * @return instance of {@link Initialize}
     */
    @Override
    public Initialize getInitializeCommand() {
    	instance = INSTANCE.getAndIncrement();
    	return new Initialize().setName("PickerBot-" + instance).setSkin(UT2004Skins.getSkin());
    }

    /**
     * Bot is ready to be spawned into the game; configure last minute stuff in here
     *
     * @param gameInfo information about the game type
     * @param currentConfig information about configuration
     * @param init information about configuration
     */
    @Override
    public void botInitialized(GameInfo gameInfo, ConfigChange currentConfig, InitedMessage init) {
    	// ignore any Yylex whining...
    	bot.getLogger().getCategory("Yylex").setLevel(Level.OFF);
    }

    /**
     * This method is called only once, right before actual logic() method is called for the first time.
     * At this point you have {@link Self} i.e., this.info fully initialized.
     */
    @Override
    public void beforeFirstLogic() {
    	// REGISTER TO ITEM PICKER CHECKER
    	ItemPickerChecker.register(info.getId());
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
    }

    /**
     * THIS BOT has picked an item!
     * @param event
     */
    @EventListener(eventClass=ItemPickedUp.class)
    public void itemPickedUp(ItemPickedUp event) {
        Item item = items.getItem(event.getId());
        if (item == null) {
            return;
        }

        if (item.isDropped()) {
            // Item was dropped by some bot
            return;
        }

        tcClient.sendToTeam(new TCItemPicked(info.getId(), event.getId()));

    	if (ItemPickerChecker.itemPicked(info.getId(), items.getItem(event.getId()))) {
	    	// AN ITEM HAD BEEN PICKED + ACKNOWLEDGED BY ItemPickerChecker
    	} else {
    		// should not happen... but if you encounter this, just wait with the bot a cycle and report item picked again
    		log.severe("SHOULD NOT BE HAPPNEINING! ItemPickerChecker refused our item!");
    	}
    }

    /**
     * Someone else picked an item!
     * @param event
     */
    @EventListener(eventClass = TCItemPicked.class)
    public void tcItemPicked(TCItemPicked event) {
        log.info("Item picked: " + event.getWhat());
        pickedItems.add(event.getWhat());
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

    /**
     * Main method called 4 times / second. Do your action-selection here.
     */
    @Override
    public void logic() throws PogamutException {
    	// if (lastLogicTime > 0) log.info("   DELTA: " + (System.currentTimeMillis()-lastLogicTime + "ms"));
    	lastLogicTime = System.currentTimeMillis();  
    	
    	if (!tcClient.isConnected()) {
    		log.warning("TeamComm not running!");
    		return;
    	}

        if (!ItemPickerChecker.isRunning() || ItemPickerChecker.isVictory()) {
            return;
        }

    	// BOT LOGIC
        --waitingForItemPursueAcceptance;
        pursueItemsBehavior();
    }

    private int waitingForItemPursueAcceptance = 0;
    private List<Item> itemsProposedForNavigation = new ArrayList<>();

    private void pursueItemsBehavior() {
        String message = "Logic";
        if (!navigation.isNavigating() || !this.stillWantToPursueAnItem()) {
            message += ", Not navigating or dont want to";
            if (waitingForItemPursueAcceptance <= 0) {
                message += "Tired of waiting!!";
                if (itemsProposedForNavigation.size() > 0) {
                    Item itemToPursue = this.getFirstNonNull(itemsProposedForNavigation);
                    message += ", Can pursue: " + itemToPursue.getId();
                    this.startNavigationTo(itemToPursue);
                    this.itemsProposedForNavigation.clear();
                } else {
                    message += "Cant pursue anything :(, acquiring new target";
                    itemsProposedForNavigation = this.getInterestingItems();
                    waitingForItemPursueAcceptance = 2;
                    sendTeamOthersMessage(new TCRequestPursueItems(this.info.getId(), itemsProposedForNavigation.stream().map(Item::getId).collect(Collectors.toList())));
                }
            }
        }
        this.print(message);
    }

    private boolean stillWantToPursueAnItem() {
        return navigation.getCurrentTargetItem() != null && this.items.getSpawnedItems().containsValue(this.items.getItem(this.navigatingToItem));
    }

    private void startNavigationTo(Item item) {
        this.navigation.navigate(item.getLocation());
        if (this.navigatingToItem != item.getId()) {
            navigatingToItem = item.getId();
        }
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
                return !pickedItems.contains(item.getId()) && (type.getCategory() == ItemType.Category.ARMOR || type.getCategory() == ItemType.Category.SHIELD || type.getCategory() == ItemType.Category.WEAPON);
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

        List<Item> firstThree = new ArrayList<>();
        for (int i = 0; i < Math.min(sortedItems.size(), 3); ++i) {
            firstThree.add(sortedItems.get(i));
        }

        return firstThree;
    }

    private Item getFirstNonNull(List<Item> items) {
        for (int i = 0; i < items.size(); ++i) {
            if (this.items.getItem(items.get(i).getId()) != null) {
                return this.items.getItem(items.get(i).getId());
            }
        }

        return null;
    }

    private void sendTeamOthersMessage(TCMessageData message) {
        // log.info("[MESSAGE SENT OTHERS] " + message.toString());
        tcClient.sendToTeamOthers(message);
    }

    private void sendMessageTo(TCMessageData message, UnrealId id) {
        tcClient.sendToBot(id, message);
    }

    private void print(String message) {
        System.out.println(this.instance + ": " + message);
    }

    /**
     * This method is called when the bot is started either from IDE or from command line.
     *
     * @param args
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
	public static void main(String args[]) throws PogamutException {
        new UT2004BotRunner(      // class that wrapps logic for bots executions, suitable to run single bot in single JVM
                ItemPickerBot.class,  // which UT2004BotController it should instantiate
                "PickerBot"       // what name the runner should be using
        ).setMain(true)           // tells runner that is is executed inside MAIN method, thus it may block the thread and watch whether agent/s are correctly executed
         .startAgents(ItemPickerChecker.BOTS_COUNT); // tells the runner to start N agent
    }
}
