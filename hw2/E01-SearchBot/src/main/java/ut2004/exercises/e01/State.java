package ut2004.exercises.e01;

import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Player;

/**
 * Veeeery simple state for search bot
 */
public class State {

    public static class InitialState extends State { }

    public static class NavigatingToPlayerState extends State {
        public final Player navigatingTo;

        public NavigatingToPlayerState(Player navigatingTo) {
            this.navigatingTo = navigatingTo;
        }
    }

    public static class GreetedCheckerbotState extends State {
        public final Player checkerBot;

        public GreetedCheckerbotState(Player checkerBot) {
            this.checkerBot = checkerBot;
        }
    }

    public static class RevealedMyselfState extends State {
        public final Player checkerBot;

        public RevealedMyselfState(Player checkerBot) {
            this.checkerBot = checkerBot;
        }
    }

    public static class WillShootAtCheckerbot extends State {
        public final Player checkerBot;
        public int delay = 2;

        public WillShootAtCheckerbot(Player checkerBot) {
            this.checkerBot = checkerBot;
        }
    }

    public static class ShootedAtCheckerbotState extends State { }

    public static class CheckerbotDamagedState extends State { }

    public static class FinishedState extends State { }

    public static class WaitingToResetState extends State {
        public int waitTime = 10;
    }
}
