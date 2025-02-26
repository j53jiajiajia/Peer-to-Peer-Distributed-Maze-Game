import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class PlayersFightTreasureTest {
    static String ip = null;
    static String port = null;
    static String gameProg = null;
    static int idtick = 0;
    static boolean shutdownInProgress = false;

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java PlayersFightTreasureTest [IP-address] [port-number] [your Game program]");
            System.exit(0);
        }
        ip = args[0];
        port = args[1];
        gameProg = args[2];

        Vector<TestPlayer> allPlayers = new Vector<TestPlayer>();

        // Create players
        int numPlayers = 3;
        for (int i = 0; i < numPlayers; i++) {
            createPlayer(allPlayers);
        }

        Scanner scan = new Scanner(System.in);
        Random random = new Random();

        // Wait for user input
        System.out.println("Press Enter to start moving players...");
        scan.nextLine();

        // Move each player one step
        int numRounds = 8;
        int sleepTime = 1;
        for (int round = 1; round <= numRounds; round++) {
            System.out.println("Round " + round);
            for (TestPlayer player : allPlayers) {
                int direction = random.nextInt(4) + 1;
                System.out.println("Moving player " + player.playerid + " in direction " + direction);
                player.useraction.println("" + direction);
            }
            // Add a small delay between rounds
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Wait for user input again
        System.out.println("Press Enter to kill all players and end the test...");
        scan.nextLine();

        // Kill all players
        killAllPlayers(allPlayers);

        System.out.println("Test completed.");
        System.exit(0);
    }

    private static String createPlayerid() {
        idtick++;
        char first = (char) (97 + idtick / 26);
        char second = (char) (97 + idtick % 26);
        return Character.toString(first) + Character.toString(second);
    }

    private static void createPlayer(Vector<TestPlayer> l) {
        String id = createPlayerid();
        String command = gameProg + " " + ip + " " + port + " " + id;
        TestPlayer player = new TestPlayer(command, id);
        boolean success = false;
        synchronized(l) {
            if (!shutdownInProgress) {
                System.out.println("Creating player using command: " + command);
                success = player.myStart();
                if (success) l.add(player);
            }
        }
        if (!success) System.exit(0);
    }

    private static void killAllPlayers(Vector<TestPlayer> l) {
        synchronized(l) {
            shutdownInProgress = true;
            for (TestPlayer player : l) {
                System.out.println("Killing player " + player.playerid);
                player.killed = true;
                player.extprocess.destroyForcibly();
            }
        }
    }
}

class TestPlayer extends Thread {
    String command = null;
    String playerid = null;
    Process extprocess = null;
    PrintStream useraction = null;
    Boolean killed = false;

    public TestPlayer(String c, String id) {
        command = c;
        playerid = id;
    }

    public void run() {
        try {
            ProcessBuilder pb = new ProcessBuilder(command.split(" "));
            pb.redirectErrorStream(true);
            String filename = "PlayersFightTreasureTest_" + playerid + ".log";
            pb.redirectOutput(new File(filename));
            extprocess = pb.start();
            useraction = new PrintStream(extprocess.getOutputStream(), true);
            extprocess.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Unable to start Game program for player " + playerid);
            System.exit(0);
        }

        if (!killed) {
            System.out.println("Game program for player " + playerid + " exited unexpectedly.");
            System.exit(0);
        }
    }

    public boolean myStart() {
        this.start();
        int counter = 0;
        while (this.extprocess == null || this.useraction == null) {
            counter++;
            if (counter > 50) {
                System.out.println("Unable to start Game program for player " + playerid + " in 5 seconds.");
                return false;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.out.println("Starting Game program for player " + playerid + " was interrupted.");
                return false;
            }
        }
        return true;
    }
}