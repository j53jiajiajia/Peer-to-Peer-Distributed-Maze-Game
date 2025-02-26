package src;

import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;
import javax.swing.*;
import java.awt.Font;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Component;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.HashSet;
import java.util.Set;

public class Game extends UnicastRemoteObject implements Player {
    private Player primaryServer;
    private Player backupServer;
    private String IP_address;
    private int port_number;
    // My player id
    private String playerId;
    // All the player ids received
    private ArrayList<String> receivedPlayerIds;
    
    public enum ServerRole {
        PRIMARY,
        BACKUP,
        PLAYER
    }
    // The local player's role
    private ServerRole serverRole;

    // The panel to display the current score of all the players
    private JPanel sidePanel;

    private final Object sidePanelLock = new Object();
    private volatile boolean isUpdatingSidePanel = false;

    // ===== Start of Game state =====
    // Flag to indicate whether the game is initialized to avoid race condition
    private boolean isGameInitialized;
    // All the players in the game
    private ArrayList<Player> playerList;
    // The positions of all the players
    private Map<String, int[]> playerPositions = new ConcurrentHashMap<>();
    // The positions of all the treasures
    private List<int[]> treasurePositions = new CopyOnWriteArrayList<>();
    // The score of all the players
    private Map<String, Integer> playerScores = new ConcurrentHashMap<>();
    private String startTime;
    // ===== End of Game state =====

    private int GRID_SIZE = 15;
    private static JFrame frame;
    private static JPanel gridPanel;

    public static int score;

    public int K;

    // 0 empty
    // 1 player
    // 2 treasure
    public static int[][] arr;

    public Game(String IP_address, int port_number, String playerId)  throws RemoteException {
        this.IP_address = IP_address;
        this.port_number = port_number;
        this.playerId = playerId;

        score = 0;
        // init treasure positions
        playerList = new ArrayList<>();
        playerPositions = new HashMap<>();
        treasurePositions = new ArrayList<>();
        // The player Ids received for server election
        receivedPlayerIds = new ArrayList<>();
        // The score of all the players
        playerScores = new HashMap<>();

        // Register to tracker
        var isRegistered = registerToTracker();
        if (!isRegistered) {
            System.out.println("Player " + playerId + " is not successfully registered. Exiting...");
            System.exit(0);
        }
        
        // This must be done after registerToTracker() to get the GRID_SIZE
        arr = new int[GRID_SIZE][GRID_SIZE];

        // Sync the game state(player positions, treasures positions) from primary server
        if (serverRole == ServerRole.PRIMARY) {
            // This need to be done after registerToTracker() to get the player ID
            if (!isGameInitialized) {
                startTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                initializeGameState();
            }
        } else {
            // Wait for the game to be initialized then proceed
            while (!getPrimaryServer().isGameInitialized()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("Player " + playerId + " is getting game state from primary server");
            // Get playerList, playerPositions, and treasurePositions from primary server
            GameState gameState = getPrimaryServer().getGameState(this);
            playerList = gameState.playerList;
            playerPositions = gameState.playerPositions;
            treasurePositions = gameState.treasurePositions;
            isGameInitialized = gameState.isGameInitialized;
            System.out.println("Player list updated: " + playerList.size());
            for (Player player : playerList) {
                try {
                    System.out.println("Player ID: " + player.getPlayerId());
                } catch (RemoteException e) {
                    System.err.println("Error getting player ID: " + e.getMessage());
                }
            }
        }
        // Init the player position
        // Find a random empty position in the arr to set the player position
        Random random = new Random();
        int newX, newY;
        newX = random.nextInt(GRID_SIZE);
        newY = random.nextInt(GRID_SIZE);
        while (arr[newX][newY] != 0 || isTreasurePosition(newX, newY)) {  // Keep searching until an empty cell is found that's not a treasure
            newX = random.nextInt(GRID_SIZE);
            newY = random.nextInt(GRID_SIZE);
        }
        // Set the player's position
        arr[newX][newY] = 1;  // Mark the position as occupied
        System.out.println("Player " + playerId + " is initialized at position " + newX + " " + newY);
        playerPositions.put(playerId, new int[] {newX, newY});

        initializeGUI();

        // Update the position to primary server to synchronize the position
        // NOTE: This must be done after initializeGUI() to make sure the GUI is initialized
        // Because it is calling renderGUI() in the method,
        // otherwise it will throw error since the GUI is not initializedi
        updatePositionToPrimaryServer(newX, newY);
    }

    private boolean isTreasurePosition(int x, int y) {
        for (int[] treasure : treasurePositions) {
            if (treasure[0] == x && treasure[1] == y) {
                return true;
            }
        }
        return false;
    }

    private TrackerInterface getTracker() {
        try {
            // Get the registry
            // Registry registry = LocateRegistry.getRegistry("localhost", 2001);
            Registry registry = LocateRegistry.getRegistry(IP_address, port_number);

            // Look up the remote object stub
            TrackerInterface stub = (TrackerInterface) registry.lookup("Tracker");
            return stub;
        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
        }
        return null;
    }

    @Override
    public ServerRole getServerRole() {
        return serverRole;
    }

    @Override
    public void clearReceivedPlayerIds() {
        System.out.println("Player " + playerId + " is clearing received player ids");
        receivedPlayerIds.clear();
    }

    @Override
    public void setPlayerList(ArrayList<Player> playerList) {
        System.out.println("Player " + playerId + " is setting player list");
        System.out.println("Player list: ");
        Set<String> activePlayerIds = new HashSet<String>();
        for (Player player : playerList) {
            try {
                System.out.println(player.getPlayerId());
                activePlayerIds.add(player.getPlayerId());
            } catch (RemoteException e) {
                System.err.println("Error getting player ID: " + e.getMessage());
            }
        }
        this.playerList = playerList;

        // Update playerPositions and playerScores by removing players not in the playerList
        playerPositions.keySet().removeIf(pid -> !activePlayerIds.contains(pid));
        playerScores.keySet().removeIf(pid -> !activePlayerIds.contains(pid));
    }

    @Override
    public void broadcastId() {
        System.out.println("Player " + playerId + " is broadcasting id");
        // Broadcast my player id to all other players
        for (Player player : playerList) {
            try {
                if (!player.getPlayerId().equals(playerId)) {
                    System.out.println("Sending id to " + player.getPlayerId());
                    player.receiveId(playerId);
                    System.out.println("Sent id to " + player.getPlayerId());
                }
            } catch (RemoteException e) {
                System.err.println("Error setting player list: " + e.getMessage());
            }
        }
    }

    @Override
    public void receiveId(String otherPlayerId) {
        System.out.println("Player " + playerId + " received id " + otherPlayerId);
        if (!receivedPlayerIds.contains(otherPlayerId)) {
            receivedPlayerIds.add(otherPlayerId);
        }
    }

    public void initiateElection(ArrayList<Player> players) throws RemoteException {
        System.out.println("Player " + getPlayerId() + " initiating election");
        try {
            // Use the player list from the parameter as the new player list
            // Because it could be a new player joinning who is initiating the election
            System.out.println("Current players:" + players.size());
            ArrayList<Player> deadPlayers = new ArrayList<>();
            for (Player player : players) {
                try {
                    System.out.println("Player ID: " + player.getPlayerId());
                } catch (RemoteException e) {
                    System.err.println("Error getting player ID: " + e.getMessage());
                    // Flag the dead player for removal
                    deadPlayers.add(player);
                }
            }
            // Remove dead players after the iteration
            players.removeAll(deadPlayers);

            System.out.println("Total players: " + players.size());

            // Clear the receivedPlayerIds first
            // BUG ALERT: Note this can not be done in following for loop
            // Otherwise, the already received ids will be cleared
            for (Player player : players) {
                player.clearReceivedPlayerIds();
            }
            // All players broadcast their id to all other players
            for (Player player : players) {
                // Clear the receivedPlayerIds first
                // Update the player list to all the players
                player.setPlayerList(players);
                player.broadcastId();
            }
            // After receiving all the ids, the players will decide the primary server
            for (Player player : players) {
                player.electServers();
            }
        } catch (RemoteException e) {
            System.err.println("Error triggering primary server election: " + e.getMessage());
        }
    }

    @Override
    public void electServers() {
        System.out.println("========== Player " + playerId + " is electing the primary server ==========");
        try {
            // Add my id to the receivedPlayerIds
            List<String> receivedPlayerIdsCopy = new ArrayList<>(receivedPlayerIds);

            receivedPlayerIdsCopy.add(playerId);

            // Create a copy of the playerList to avoid concurrent modification
            List<Player> playerListCopy = new ArrayList<>(playerList);

            // Retrieve the current leaders from the player list
            ArrayList<Player> servers = new ArrayList<>();
            for (Player player : playerListCopy) {
                try {
                    ServerRole role = player.getServerRole();
                    if (role == ServerRole.PRIMARY || role == ServerRole.BACKUP) {
                        servers.add(player);
                    }
                } catch (RemoteException e) {
                    System.err.println("Error getting server role: " + e.getMessage());
                }
            }
            System.out.println("Servers number: " + servers.size());

            // Retrive the current game state from the old primary
            if (!servers.isEmpty()) {
                GameState currentGameState = servers.get(0).getGameState(this);
                updateGameState(currentGameState);
            }

            // Check if receivedPlayerIdsCopy is not empty before proceeding
            if (receivedPlayerIdsCopy.isEmpty()) {
                System.out.println("Error: No player IDs received. Cannot proceed with server election.");
                return;
            }

            // The player with the highest id is elected as the primary server
            // Get the highest id from the receivedPlayerIdsCopy
            System.out.println("All received player ids: " + receivedPlayerIdsCopy);
            // Compare player IDs lexicographically (alphabetically)
            receivedPlayerIdsCopy.sort(String.CASE_INSENSITIVE_ORDER);
            System.out.println("All received player ids after sorting: " + receivedPlayerIdsCopy);
            String highestId = receivedPlayerIdsCopy.get(receivedPlayerIdsCopy.size() - 1);
            System.out.println("The highest id is " + highestId);
            String secondHighestId = receivedPlayerIdsCopy.size() > 1 ? receivedPlayerIdsCopy.get(receivedPlayerIdsCopy.size() - 2) : null;
            System.out.println("The second highest id is " + secondHighestId);

            if (playerId.equals(highestId)) {
                // This node becomes the primary server
                serverRole = ServerRole.PRIMARY;
                primaryServer = this;
                System.out.println("I, " + playerId + ", am the new primary server");

                // If there was a previous primary, it becomes the backup
                if (servers.size() > 0) {
                    System.out.println("There is a previous primary server: " + servers.get(0).getPlayerId());
                    Player oldPrimary = servers.get(0);
                    oldPrimary.demoteToBackup();
                    backupServer = oldPrimary;

                    // If there was a previous backup, it becomes a regular player
                    if (servers.size() > 1) {
                        servers.get(1).demoteToPlayer();
                    }
                }
            } else if (playerId.equals(secondHighestId)) {
                // This node becomes the backup server
                serverRole = ServerRole.BACKUP;
                backupServer = this;
                primaryServer = getPlayerById(highestId);
                System.out.println("I, " + playerId + ", am the new backup server");

                // If there was a previous backup, it becomes a regular player
                if (servers.size() > 1) {
                    servers.get(1).demoteToPlayer();
                }
            } else {
                // This node is a regular player
                serverRole = ServerRole.PLAYER;
                System.out.println("I, " + playerId + ", am a regular player");
                primaryServer = getPlayerById(highestId);
                backupServer = secondHighestId != null ? getPlayerById(secondHighestId) : null;
            }
            // Re-construct the playerList from the received Ids
            ArrayList<Player> newPlayerList = new ArrayList<>();
            for (String id : receivedPlayerIdsCopy) {
                Player player = getPlayerById(id);
                if (player != null) {
                    newPlayerList.add(player);
                } else {
                    System.err.println("Warning: Could not find player with ID " + id);
                }
            }

            // Update the playerList
            playerList = new ArrayList<>(newPlayerList);
            System.out.println("Updated player list: " + playerList.size() + " players");

            // Update the primary server's GUI
            if (serverRole == ServerRole.PRIMARY) {
                SwingUtilities.invokeLater(() -> {
                    renderGUI(GRID_SIZE);
                });
            }
        } catch (RemoteException e) {
            System.err.println("Error electing primary server: " + e.getMessage());
        }
    }

    @Override
    public void promoteBackupToPrimary() throws RemoteException {
        System.out.println("Player " + playerId + " is promoting backup to primary");
        if (backupServer != null) {
            primaryServer = backupServer;
            backupServer = null;
        }
    }

    @Override
    public void demoteToBackup() {
        serverRole = ServerRole.BACKUP;
    }

    @Override
    public void demoteToPlayer() {
        serverRole = ServerRole.PLAYER;
    }

    @Override
    public String ping() {
        return "pong";
    }

    private Player getPlayerById(String id) {
        for (Player player : playerList) {
            try {
                if (player.getPlayerId().equals(id)) {
                    return player;
                }
            } catch (RemoteException e) {
                System.err.println("Error getting player ID: " + e.getMessage());
            }
        }
        return null;
    }


    private boolean registerToTracker() {
        try {
            TrackerInterface tracker = getTracker();
            
            // Register the current player to tracker
            playerList = tracker.getPlayerList();
            GRID_SIZE = tracker.getN();
            K = tracker.getK();
            return tracker.registerPlayer(this);
        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
        return false;
    }
    
    public Player getPrimaryServer() {
        if (serverRole == ServerRole.PRIMARY) {
            return this;
        } else {
            return primaryServer;
        }
    }

    private void updatePositionToPrimaryServer(int x, int y) {
        try {
            Player primaryServer = getPrimaryServer();
            GameState gameState = primaryServer.updateGamebyNewMove(this, x, y);
            // Update the local game state
            updateGameState(gameState);
        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }

    // if everything is ok, return value will be NULL;
    // if someone died, return value will be the player reference
    public Player heartBeat() {
        // heartbeat must be done by primary server
        // other thread only return all well
        if (!serverRole.equals(ServerRole.PRIMARY)) {
            return null;
        }
        for (Player player: playerList) {
            try {
                var msg = player.ping();
                assert msg.equals("pong");
            } catch (RemoteException e) {
                System.err.println("Error setting player list: " + e.getMessage());
                return player;
            }
        }
        System.out.println("all normal nodes well");
        return null;
    }

    // watchdog for backup
    // true for live, false for dead
    public boolean serverAlive() {
        // watchdog must be done by backup server
        // other thread only return all things normal
        if (!serverRole.equals(ServerRole.PRIMARY) &&
            !serverRole.equals(ServerRole.BACKUP)) {
            return true;
        }
        try {
            if (serverRole.equals(ServerRole.PRIMARY) && backupServer != null) {
                var msg = backupServer.ping();
                assert msg.equals("pong");
            } else if (serverRole.equals(ServerRole.BACKUP) && primaryServer != null) {
                var msg = primaryServer.ping();
                assert msg.equals("pong");
            }
        } catch (RemoteException e) {
            System.err.println("Error setting player list: " + e.getMessage());
            return false;
        }
        System.out.println("primary nodes well");
        return true;
    }

    // ===== Primary Server methods start =====
    @Override
    public String getPlayerId() {
        return playerId;
    }

    @Override
    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    @Override
    public GameState getGameState(Player requester) {
        System.out.println("Current game state:");
        System.out.println("Player list: " + playerList.size());
        System.out.println("Player positions: " + playerPositions.size());
        System.out.println("Treasure positions: " + treasurePositions.size());
        System.out.println("Is game initialized: " + isGameInitialized);
        System.out.println("Player scores: " + playerScores.size());
        System.out.println("Start time: " + startTime);
        return new GameState(isGameInitialized, playerList, playerPositions,
                             treasurePositions, playerScores, startTime);
    }

    @Override
    public boolean isGameInitialized() {
        return isGameInitialized;
    }

    @Override
    public void updateGameState(GameState gameState) {
        // Update current player's game state from the server
        System.out.println("Updating game state");
        System.out.println("Player list: " + gameState.playerList.size());
        System.out.println("Player positions: " + gameState.playerPositions.size());
        System.out.println("Treasure positions: " + gameState.treasurePositions.size());
        System.out.println("Is game initialized: " + gameState.isGameInitialized);
        System.out.println("Player scores: " + gameState.playerScores.size());
        System.out.println("Start time: " + gameState.startTime);
        playerList = gameState.playerList;
        int[] currentPlayerPosition = playerPositions.get(playerId);
        playerPositions = gameState.playerPositions;
        // There is a chance that the current player position is not updated in the shared player positions
        // So we need to add the current player position to the player positions manually
        if (currentPlayerPosition != null && !playerPositions.containsKey(playerId)) {
            System.out.println("Add the current player position which is not included in the shared player positions");
            playerPositions.put(playerId, currentPlayerPosition);
        }
        treasurePositions = gameState.treasurePositions;
        isGameInitialized = gameState.isGameInitialized;
        playerScores = gameState.playerScores;
        startTime = gameState.startTime;
    }

    @Override
    public GameState updateGamebyNewMove(Player player, int playerX, int playerY) {
        // Primary server ONLY can call this method
        // Update the game state by a new move from a player
        try {
            System.out.println("Received move from player " + player.getPlayerId() + ": " + playerX + " " + playerY);

            var playerId = player.getPlayerId();

            // Check if the new position is already occupied by another player
            for (String otherPlayerId : playerPositions.keySet()) {
                if (!otherPlayerId.equals(playerId)) {
                    int[] otherPlayerPos = playerPositions.get(otherPlayerId);
                    if (otherPlayerPos[0] == playerX && otherPlayerPos[1] == playerY) {
                        System.out.println("Player " + playerId + " cannot move to " + playerX + "," + playerY + " as it's occupied by " + otherPlayerId);
                        return new GameState(isGameInitialized, playerList, playerPositions, treasurePositions, playerScores, startTime);
                    }
                }
            }

            // Update player position
            playerPositions.put(playerId, new int[] {playerX, playerY});
            System.out.println("Player " + playerId + " moved to " + playerX + "," + playerY);

            // Check if player hit a treasure
            for (int[] treasure : treasurePositions) {
                if (treasure[0] == playerX && treasure[1] == playerY) {
                    treasurePositions.remove(treasure);
                    int currentScore = playerScores.getOrDefault(playerId, 0);
                    playerScores.put(playerId, currentScore + 1);
                    System.out.println("Player " + playerId + " collected a treasure at pos " + playerX + "," + playerY + ". New score: " + currentScore + 1);
                    break;
                }
            }
    
            // Renew the treasure positions
            int cnt = treasurePositions.size();
            if (cnt != K && cnt != K - 1) {
                System.out.println("ERROR");
                for (int i = 0; i < 10; i++) {
                    Toolkit.getDefaultToolkit().beep();
                }
            }
    
            if (cnt < K) {
                // Scan the whole grid to find empty positions
                ArrayList<Location> empty = new ArrayList<>();
                for (int i = 0; i < GRID_SIZE; i++) {
                    for (int j = 0; j < GRID_SIZE; j++) {
                        if (arr[i][j] == 0) {
                            empty.add(new Location(i, j));
                        }
                    }
                }
                // Randomly select an empty position to add a new treasure
                Collections.shuffle(empty, new Random());
                while (treasurePositions.size() < K) {
                    var loc = empty.remove(0);
                    var locX = loc.getX();
                    var locY = loc.getY();
                    treasurePositions.add(new int[] {locX, locY});
                    if (empty.isEmpty()) break;
                }
            }

            GameState newGameState = new GameState(isGameInitialized, playerList, playerPositions,
                                                  treasurePositions, playerScores, startTime);

            // Update the game state to backup server
            if (backupServer != null) {
                try {
                    backupServer.updateGameState(newGameState);
                } catch (RemoteException e) {
                    System.err.println("Error updating backup server: " + e.getMessage());
                }
            }

            // Update the GUI on the Event Dispatch Thread
            System.out.println("Render GUI for primary server");
            SwingUtilities.invokeLater(() -> {
                renderGUI(GRID_SIZE);
            });
            return newGameState;
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void removePlayerGameState(Player player) {
        // Remove the player's trait from the game state
        // Remove player position from the player positions
        try {
            playerPositions.remove(player.getPlayerId());
        } catch (RemoteException e) {
            System.err.println("Error removing player game state: " + e.getMessage());
        }
    }

    @Override
    public void updatePlayerList() {
        System.out.println("Updating player list");
        // Create a list to store active players
        List<Player> activePlayers = new ArrayList<>();

        // Loop through all the players to collect the active ones
        for (Player player : playerList) {
            try {
                String pid = player.getPlayerId();
                playerPositions.put(pid, playerPositions.get(pid));
                activePlayers.add(player);
            } catch (RemoteException e) {
                System.err.println("Error getting player ID");
                // The player is dead, skip it
            }
        }

        // Update the player list with only active players
        playerList.clear();
        playerList.addAll(activePlayers);

        // Remove inactive players' positions and scores
        Set<String> activePlayerIds = new HashSet<>();
        for (Player player : activePlayers) {
            try {
                activePlayerIds.add(player.getPlayerId());
            } catch (RemoteException e) {
                System.err.println("Error getting player ID: " + e.getMessage());
            }
        }

        playerPositions.keySet().retainAll(activePlayerIds);
        playerScores.keySet().retainAll(activePlayerIds);

        System.out.println("Updated player list. Current size: " + playerList.size());
    }
    // ===== Primary Server methods end =====

    private synchronized void initializeGameState() {
        // Only the primary server can initialize the game and add treasures
        // Get all the empty locations
        ArrayList<Location> empty = new ArrayList<>();
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                if (arr[i][j] == 0) {
                    empty.add(new Location(i, j));
                }
            }
        }
        // Shuffle the empty locations
        Collections.shuffle(empty, new Random());
        // Add K treasures to the game
        for (int i = 0; i < K; i++) {
            var loc = empty.get(i);
            var locX = loc.getX();
            var locY = loc.getY();
            arr[locX][locY] = 2;
            treasurePositions.add(new int[] {locX, locY});
        }
        isGameInitialized = true;
    }

    private void initializeGUI() {
        frame = new JFrame("Player: " + playerId);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 500);

        // Create main panel
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Create score panel
        sidePanel = new JPanel();
        sidePanel.setLayout(new BoxLayout(sidePanel, BoxLayout.Y_AXIS));
        sidePanel.setBorder(BorderFactory.createTitledBorder("Game Stats"));
        sidePanel.setPreferredSize(new Dimension(100, 0));  // Set preferred width

        gridPanel = new JPanel(new GridLayout(GRID_SIZE, GRID_SIZE));
        for (int i = 0; i < GRID_SIZE * GRID_SIZE; i++) {
            JPanel cell = new JPanel();
            cell.setBorder(BorderFactory.createLineBorder(Color.BLACK));
            gridPanel.add(cell);
        }
        // Add panels to main panel
        mainPanel.add(sidePanel, BorderLayout.WEST);
        mainPanel.add(gridPanel, BorderLayout.CENTER);
        frame.add(mainPanel);

        System.out.println("Player positions: " + playerPositions.size());
        for (String playerId : playerPositions.keySet()) {
            int[] pos = playerPositions.get(playerId);
            System.out.println("Player " + playerId + " is initialized at position " + pos[0] + " " + pos[1]);
        }
        var playerPos = playerPositions.get(playerId);
        var playerX = playerPos[0];
        var playerY = playerPos[1];
        for (int i = 0; i < GRID_SIZE * GRID_SIZE; i++) {
            if (i == playerY * GRID_SIZE + playerX) {
                arr[playerX][playerY] = BlockStatus.PLAYER; // label the position to be occupied
            }
        }

        renderGUI(GRID_SIZE);

        // Handle window close event
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                // Handle the window close event and gracefully shutdown the game
                selfCleanupAndDeregister();
                // Exit the application
                System.exit(0);
            }
        });

        // Disable the default close operation
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        // Display the frame
        frame.setVisible(true);

        // Bring the window to the front
        frame.setAlwaysOnTop(true);
        frame.toFront();
        frame.requestFocus();
        frame.setAlwaysOnTop(false);
    }

    public void selfCleanupAndDeregister() {
        try {
            System.out.println("Shutting down the game for: " + playerId);
            if (serverRole == ServerRole.PRIMARY) {
                // Remove the position of the current player
                // from the backup server who will be promoted to primary
                if (backupServer != null) {
                    backupServer.removePlayerGameState(Game.this);
                }

                // Clean up the player data
                playerPositions.remove(playerId);
                playerScores.remove(playerId);

                // broadcast to all players to promote backup to primary
                for (Player player : playerList) {
                    if (player.getPlayerId() != this.getPlayerId()) {
                        player.promoteBackupToPrimary();
                    }
                }
            }

            // NOTE: According to the requirement, we don NOT contact the tracker at this point
            // Only contact the tracker when 1) a new player is joined; 2) a player is crashed
            // This case is graceful shutdown, no need to contact the tracker
            // TrackerInterface tracker = getTracker();
            // tracker.deregisterPlayer(Game.this);
            // Remove the current player from the list
            playerList.removeIf(player -> {
                try {
                    return player.getPlayerId().equals(this.getPlayerId());
                } catch (RemoteException e) {
                    System.err.println("Error removing player: " + e.getMessage());
                    return false;
                }
            });
            System.out.println("Player " + playerId + " removed from the list. New size: " + playerList.size());
            
            // Trigger election for all remaining players
            if (!playerList.isEmpty()) {
                System.out.println("Trigger election for all remaining players");
                initiateElection(new ArrayList<>(playerList));
            } else {
                System.out.println("No players left to initiate election.");
            }
            System.out.println("Player " + playerId + " deregistered successfully.");
        } catch (Exception e) {
            System.err.println("Error deregistering player: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void serverDied() {
        System.out.println("There is a server died, begin to elect");
        try {
            initiateElection(playerList);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void regularPlayerDied() {
        System.out.println("There is a regular player died");
        try {
            // Update the player list of the primary server
            primaryServer.updatePlayerList();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void updateSidePanel() {
        synchronized (sidePanelLock) {
            if (isUpdatingSidePanel) {
                return;
            }
            isUpdatingSidePanel = true;
        }

        try {
            System.out.println("Player " + playerId + " is update the side panel");
            sidePanel.removeAll();

            try {
                // Display start time
                JLabel startTimeLabel = new JLabel("Start Time:");
                startTimeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                sidePanel.add(startTimeLabel);
                JLabel timeLabel = new JLabel(startTime);
                timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                sidePanel.add(timeLabel);
                sidePanel.add(Box.createVerticalStrut(10));

                // Display primary and backup server information
                JLabel primaryServerLabel = new JLabel("Primary: " + (primaryServer != null ? primaryServer.getPlayerId() : "N/A"));
                JLabel backupServerLabel = new JLabel("Backup: " + (backupServer != null ? backupServer.getPlayerId() : "N/A"));
                primaryServerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                backupServerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                sidePanel.add(primaryServerLabel);
                sidePanel.add(backupServerLabel);
                sidePanel.add(Box.createVerticalStrut(10)); // Add some space
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            // Add a label for "Score:"
            JLabel scoreHeaderLabel = new JLabel("Score:");
            scoreHeaderLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            sidePanel.add(scoreHeaderLabel);
            sidePanel.add(Box.createVerticalStrut(5)); // Add a small space after the header

            // Display player scores
            for (Player player : playerList) {
                try {
                    String pid = player.getPlayerId();
                    int score = playerScores.getOrDefault(pid, 0);
                    JLabel scoreLabel = new JLabel(pid + ": " + score);
                    scoreLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    if (pid.equals(playerId)) {
                        scoreLabel.setForeground(Color.RED);
                        scoreLabel.setFont(scoreLabel.getFont().deriveFont(Font.BOLD));
                    }
                    sidePanel.add(scoreLabel);
                } catch (RemoteException e) {
                    System.err.println("Error getting player ID: " + e.getMessage());
                }
            }
            sidePanel.revalidate();
            sidePanel.repaint();

        } finally {
            synchronized (sidePanelLock) {
                isUpdatingSidePanel = false;
            }
        }
    }

    public static void main(String[] args) {
        // check the num of args
        if (args.length != 3) {
            System.out.println("Error: You must provide exactly 3 arguments.");
            System.out.println("Usage: java Game <IP_address> <port_number> <player_id>");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            Game game = null;
            try {
                String IP_address = args[0];
                int port_number = Integer.parseInt(args[1]);
                String player_id = args[2];
    
                System.out.println("IP address: " + IP_address);
                System.out.println("port number: " + port_number);
                System.out.println("player id: " + player_id);
                game = new Game(IP_address, port_number, player_id);
            } catch (RemoteException e) {
                e.printStackTrace();
                return;
            }

            final Game finalGame = game;
            Thread inputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // handle input line by line
                        finalGame.processInput(line);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            inputThread.start();

            // add a new Timer for 500ms
            Timer timer = new Timer(500, e -> {
                // System.out.println("Timer task executed - " + System.currentTimeMillis());
                if(finalGame.heartBeat() != null) {
                    System.out.println("normal node died, begin to process");
                    finalGame.regularPlayerDied();
                }
                if (!finalGame.serverAlive()) {
                    System.out.println("primary node died, begin to process");
                    finalGame.serverDied();
                }
            });

            // Start the timer
            timer.start();
        });
    }

    public int getPlayerid() {
        return 1;
    }

    private void processInput(String line) {
        System.out.println("Received input: " + line);
        String result = line.replaceAll("[^012349]", "");
        // handle input line char by char
        for (var i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            handleDirectionPressed(c);
        }

    }

    private void handleDirectionPressed(char direction) {
        System.out.println("Received direction: " + direction);
        if (direction != Direction.UP && direction != Direction.DOWN && 
            direction != Direction.LEFT && direction != Direction.RIGHT &
            direction != '0' && direction != '9') {
            return;
        }

        var playerPos = playerPositions.get(playerId);
        for (String playerId : playerPositions.keySet()) {
            int[] pos = playerPositions.get(playerId);
            System.out.println("Player " + playerId + " move from position " + pos[0] + " " + pos[1] + " to direction " + direction);
        }
        var playerX = playerPos[0];
        var playerY = playerPos[1];
        // Check if the move is valid (boundary check)
        if (direction == Direction.UP && playerY == 0) {
            return;
        } else if (direction == Direction.DOWN && playerY == GRID_SIZE - 1) {
            return;
        } else if (direction == Direction.LEFT && playerX == 0) {
            return;
        } else if (direction == Direction.RIGHT && playerX == GRID_SIZE - 1) {
            return;
        } else if (direction == '0') {
            // Refresh the game state
            try {
                GameState currentGameState = getPrimaryServer().getGameState(this);
                updateGameState(currentGameState);
                renderGUI(GRID_SIZE);
                return;
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else if (direction == '9') {
            // Graceful shutdown
            selfCleanupAndDeregister();
            // Exit the application
            System.exit(0);
            return;

        }
        // Note: No need to check if there is other player blocking the path here but on the server side

        // change player's location
        if (direction == Direction.UP) {
            playerY -= 1;
        } else if (direction == Direction.DOWN) {
            playerY += 1;
        } else if (direction == Direction.LEFT) {
            playerX -= 1;
        } else if (direction == Direction.RIGHT) {
            playerX += 1;
        }

        updatePositionToPrimaryServer(playerX, playerY);
        
        renderGUI(GRID_SIZE);
    }

    private void renderGUI(int grid_size) {
        System.out.println("Render GUI");
        if (gridPanel == null) {
            System.err.println("Error: gridPanel is null in renderGUI");
            return;
        }
        // Initialize the arr to all 0
        for (int i = 0; i < grid_size; i++) {
            for (int j = 0; j < grid_size; j++) {
                arr[i][j] = 0;
            }
        }
        // Calculate player positions
        for (String playerId : playerPositions.keySet()) {
            int[] pos = playerPositions.get(playerId);
            int x = pos[0];
            int y = pos[1];
            arr[x][y] = BlockStatus.PLAYER; // Mark player position
        }

        // Calculate treasure positions
        for (int[] pos : treasurePositions) {
            int x = pos[0];
            int y = pos[1];
            arr[x][y] = BlockStatus.TREASURE;
        }

        for (int i = 0; i < grid_size; i++) {
            for (int j = 0; j < grid_size; j++) {
                var idx = GameUtils.getIdx(i, j, grid_size);
                var cell = gridPanel.getComponent(idx);
                if (arr[i][j] == 1) {
                    cell.setBackground(Color.MAGENTA);
                } else if (arr[i][j] == 2) {
                    cell.setBackground(Color.BLUE);
                }
            }

        } 
        // Render the GUI
        renderColor(grid_size);

        updateSidePanel();

        frame.repaint();
    }

    private void renderColor(int grid_size) {
        for (int i = 0; i < grid_size; i++) {
            for (int j = 0; j < grid_size; j++) {
                var idx = GameUtils.getIdx(i, j, grid_size);
                if (arr[i][j] == BlockStatus.PLAYER) {
                    // The player is here
                    var cell = (JPanel) gridPanel.getComponent(idx);
                    cell.setBackground(Color.MAGENTA);
                    cell.removeAll(); // Clear any existing components
                    JLabel label = new JLabel();
                    for (String playerId : playerPositions.keySet()) {
                        int[] position = playerPositions.get(playerId);
                        if (position[0] == i && position[1] == j) {
                            label.setText(playerId);
                            if (playerId.equals(this.playerId)) {
                                cell.setBackground(Color.RED);
                            }
                            break;
                        }
                    }
                    label.setForeground(Color.WHITE);
                    label.setFont(new Font(label.getFont().getName(), Font.BOLD, 16));
                    label.setHorizontalAlignment(JLabel.CENTER);
                    cell.add(label);
                    cell.revalidate();
                } else if (arr[i][j] == BlockStatus.TREASURE) {
                    // The treasure is here
                    var cell = (JPanel) gridPanel.getComponent(idx);
                    cell.setBackground(Color.BLUE);
                    cell.removeAll(); // Clear any existing components
                    JLabel label = new JLabel("*");
                    label.setForeground(Color.WHITE);
                    label.setFont(new Font(label.getFont().getName(), Font.BOLD, 23));
                    label.setHorizontalAlignment(JLabel.CENTER);
                    cell.add(label);
                    cell.revalidate();
                } else if (arr[i][j] == BlockStatus.EMPTY) {
                    // The cell is empty
                    var cell = gridPanel.getComponent(idx);
                    cell.setBackground(Color.WHITE);
                }
            }
        }
    }
}