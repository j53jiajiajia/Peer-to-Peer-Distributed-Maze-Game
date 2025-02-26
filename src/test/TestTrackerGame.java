package test;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.rmi.RemoteException;
import java.util.ArrayList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.After;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Arrays;
import src.Game;
import src.Tracker;
import src.Player;
import src.Player.GameState;
import src.Game.ServerRole;

public class TestTrackerGame {
 
    private static Tracker tracker;
    private static ArrayList<Player> testPlayerList;
 
    @BeforeClass
    public static void setUp() {
        tracker = new Tracker();
        tracker.setPortNumber(6789);
        tracker.setN(15);
        tracker.setK(10);
        tracker.regTracker();
        testPlayerList = new ArrayList<>();
    }
 
    @Test
    public void testRegisterPlayer() throws RemoteException,InterruptedException {
        Game game = new Game("127.0.0.1", 6789, "ab");
        var currentPlayerList = tracker.getPlayerList();
        assertEquals(1, currentPlayerList.size());
        testPlayerList.add(game);
    }
 
 
    @Test
    public void testGamePlayer() throws AWTException,RemoteException,InterruptedException{
        // Create a new game player
        String playerId = "test";
        Game player = new Game("127.0.0.1", 6789, playerId);
        testPlayerList.add(player);

        // Record the player's initial position
        Player.GameState initialState = player.getGameState(player);
        int[] initialPosition = initialState.playerPositions.get(playerId);
        System.out.println("Initial position: " + initialPosition[0] + "," + initialPosition[1]);

        Thread.sleep(100);

        var newPosX = initialPosition[0] + 1;
        var newPosY = initialPosition[1];
        player.updateGamebyNewMove(player, newPosX, newPosY);
        
        Thread.sleep(100);

        // Verify the move is successful
        Player.GameState finalState = player.getGameState(player);
        int[] finalPosition = finalState.playerPositions.get(playerId);
        System.out.println("Final position: " + finalPosition[0] + "," + finalPosition[1]);
        assertEquals(newPosX, finalPosition[0]);
    }

    @Test
    public void testPrimaryServerSwitching() throws RemoteException, InterruptedException {
        // Create three players with incrementing IDs
        Game player1 = new Game("127.0.0.1", 6789, "player1");
        testPlayerList.add(player1);
        
        // Wait for the first player to be registered and become primary
        Thread.sleep(100);
        
        // Check if the first player is primary
        assertEquals(ServerRole.PRIMARY, player1.getServerRole());
        
        // Create and add the second player
        Game player2 = new Game("127.0.0.1", 6789, "player2");
        testPlayerList.add(player2);
        
        // Wait for the second player to be registered and become primary
        Thread.sleep(100);
        
        // Check if roles have switched
        assertEquals(ServerRole.BACKUP, player1.getServerRole());
        assertEquals(ServerRole.PRIMARY, player2.getServerRole());
        
        // Create and add the third player
        Game player3 = new Game("127.0.0.1", 6789, "player3");
        testPlayerList.add(player3);
        
        // Wait for the third player to be registered and become primary
        Thread.sleep(100);
        
        // Check if roles have switched again
        assertEquals(ServerRole.PLAYER, player1.getServerRole());
        assertEquals(ServerRole.BACKUP, player2.getServerRole());
        assertEquals(ServerRole.PRIMARY, player3.getServerRole());

        System.out.println("Player 1 primary server: " + player1.getPrimaryServer().getPlayerId());
        System.out.println("Player 2 primary server: " + player2.getPrimaryServer().getPlayerId());
        System.out.println("Player 3 primary server: " + player3.getPrimaryServer().getPlayerId());
        
        // Verify the primary server for all players
        assertEquals(player3.getPlayerId(), player1.getPrimaryServer().getPlayerId());
        assertEquals(player3.getPlayerId(), player2.getPrimaryServer().getPlayerId());
        assertEquals(player3.getPlayerId(), player3.getPrimaryServer().getPlayerId());

        // player3 exits
        player3.selfCleanupAndDeregister();
        
        // Wait for the roles to be reassigned
        Thread.sleep(100);
        
        // Check if roles have switched back
        System.out.println("Player 1 role after player3 removal: " + player1.getServerRole());
        System.out.println("Player 2 role after player3 removal: " + player2.getServerRole());
        
        // Verify the primary server for remaining players
        assertEquals(player2.getPlayerId(), player1.getPrimaryServer().getPlayerId());
        assertEquals(player2.getPlayerId(), player2.getPrimaryServer().getPlayerId());
    }

    @Test
    public void testPlayersMove() throws AWTException,RemoteException,InterruptedException{
        // Create a new game player
        String playerId1 = "test";
        Game player1 = new Game("127.0.0.1", 6789, playerId1);
        testPlayerList.add(player1);

        // Create another game player
        String playerId2 = "test2";
        Game player2 = new Game("127.0.0.1", 6789, playerId2);
        testPlayerList.add(player2);

        Thread.sleep(100);

        // Record the initial positions of both players
        Player.GameState initialState1 = player1.getGameState(player1);
        int[] initialPosition1 = initialState1.playerPositions.get(playerId1);
        Player.GameState initialState2 = player2.getGameState(player2);
        int[] initialPosition2 = initialState2.playerPositions.get(playerId2);

        // Move two players at the same time
        var primary = player1.getPrimaryServer();
        // Move player1 one step to the right
        primary.updateGamebyNewMove(player1, initialPosition1[0] + 1, initialPosition1[1]);
        // Move player2 one step down
        primary.updateGamebyNewMove(player2, initialPosition2[0], initialPosition2[1] + 1);
        
        // Give some time for the moves to be processed
        Thread.sleep(100);

        // Verify the moves are successful
        var finalState = primary.getGameState(player1);

        int[] finalPosition1 = finalState.playerPositions.get(playerId1);
        int[] finalPosition2 = finalState.playerPositions.get(playerId2);

        System.out.println("Player 1 initial position: " + initialPosition1[0] + "," + initialPosition1[1]);
        System.out.println("Player 2 initial position: " + initialPosition2[0] + "," + initialPosition2[1]);
        System.out.println("Player 1 final position: " + finalPosition1[0] + "," + finalPosition1[1]);
        System.out.println("Player 2 final position: " + finalPosition2[0] + "," + finalPosition2[1]);

        // Assert that the both moves were successful
        assertEquals(initialPosition1[0] + 1, finalPosition1[0]);
        assertEquals(initialPosition1[1], finalPosition1[1]);
        assertEquals(initialPosition2[0], finalPosition2[0]);
        assertEquals(initialPosition2[1] + 1, finalPosition2[1]);
    }

    @Test
    public void testPlayerFightTreasure() throws Exception {
        // Create a new game player
        String playerId1 = "test1";
        Game player1 = new Game("127.0.0.1", 6789, playerId1);
        testPlayerList.add(player1);

        // Create another game player
        String playerId2 = "test2";
        Game player2 = new Game("127.0.0.1", 6789, playerId2);
        testPlayerList.add(player2);

        var primary = player1.getPrimaryServer();

        // Set a treasure at (5, 5)
        var gameState = primary.getGameState(player1);
        gameState.treasurePositions.add(new int[]{5, 5});
        primary.updateGameState(gameState);

        // Move both players to (5, 5) to fight for the treasure
        // So we use a CyclicBarrier to make sure both players move at the same time
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicReference<GameState> result1 = new AtomicReference<>();
        AtomicReference<GameState> result2 = new AtomicReference<>();

        Future<?> future1 = executor.submit(() -> {
            try {
                barrier.await(); // Wait for the other thread
                result1.set(primary.updateGamebyNewMove(player1, 5, 5));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Future<?> future2 = executor.submit(() -> {
            try {
                barrier.await(); // Wait for the other thread
                result2.set(primary.updateGamebyNewMove(player2, 5, 5));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Wait for both tasks to complete
        future1.get();
        future2.get();

        executor.shutdown();

        GameState gameState1 = result1.get();
        GameState gameState2 = result2.get();

        // Give some time for the moves to be processed
        Thread.sleep(100);

        // Verify the moves are successful by checking the score of the players
        var finalState = primary.getGameState(player1);
        System.out.println("Final state: " + finalState);
        System.out.println("Player positions: " + finalState.playerPositions);
        System.out.println("Player scores: " + finalState.playerScores);
        System.out.println("Treasure positions: " + finalState.treasurePositions);

        int player1Score = finalState.playerScores.getOrDefault(playerId1, 0);
        int player2Score = finalState.playerScores.getOrDefault(playerId2, 0);
        System.out.println("Player 1 score: " + player1Score);
        System.out.println("Player 2 score: " + player2Score);
    
        // Assert that only one player got the score
        assertTrue("Only one player should have scored", (player1Score == 1 && player2Score == 0) || (player1Score == 0 && player2Score == 1));
        assertEquals("Total score should be 1", 1, player1Score + player2Score);

        // Assert that only one player moved successfully to (5,5)
        int[] player1Position = finalState.playerPositions.get(playerId1);
        int[] player2Position = finalState.playerPositions.get(playerId2);
        
        assertTrue("Only one player should be at (5,5)",
            (player1Position[0] == 5 && player1Position[1] == 5 && (player2Position[0] != 5 || player2Position[1] != 5)) ||
            (player2Position[0] == 5 && player2Position[1] == 5 && (player1Position[0] != 5 || player1Position[1] != 5))
        );
        
        // Verify that the positions are different
        assertNotEquals("Players should not be in the same position",
            Arrays.equals(player1Position, player2Position)
        );

        // Assert that the treasure has been removed
        assertTrue("Treasure at (5,5) should be removed", finalState.treasurePositions.stream().noneMatch(t -> t[0] == 5 && t[1] == 5));
    }

    @Test
    public void testTwoPlayersCollectDifferentTreasures() throws Exception {
        // Create two game players
        String playerId1 = "test1";
        Game player1 = new Game("127.0.0.1", 6789, playerId1);
        testPlayerList.add(player1);

        String playerId2 = "test2";
        Game player2 = new Game("127.0.0.1", 6789, playerId2);
        testPlayerList.add(player2);

        var primary = player1.getPrimaryServer();

        // Set two treasures at different locations
        var gameState = primary.getGameState(player1);
        gameState.treasurePositions.add(new int[]{5, 5});
        gameState.treasurePositions.add(new int[]{10, 10});
        primary.updateGameState(gameState);

        // Move both players to their respective treasure locations simultaneously
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicReference<GameState> result1 = new AtomicReference<>();
        AtomicReference<GameState> result2 = new AtomicReference<>();

        Future<?> future1 = executor.submit(() -> {
            try {
                barrier.await();
                result1.set(primary.updateGamebyNewMove(player1, 5, 5));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Future<?> future2 = executor.submit(() -> {
            try {
                barrier.await();
                result2.set(primary.updateGamebyNewMove(player2, 10, 10));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Wait for both tasks to complete
        future1.get();
        future2.get();

        executor.shutdown();

        // Give some time for the moves to be processed
        Thread.sleep(100);

        // Verify the moves are successful
        var finalState = primary.getGameState(player1);
        System.out.println("Final state: " + finalState);
        System.out.println("Player positions: " + finalState.playerPositions);
        System.out.println("Player scores: " + finalState.playerScores);
        System.out.println("Treasure positions: " + finalState.treasurePositions);

        int player1Score = finalState.playerScores.getOrDefault(playerId1, 0);
        int player2Score = finalState.playerScores.getOrDefault(playerId2, 0);
        System.out.println("Player 1 score: " + player1Score);
        System.out.println("Player 2 score: " + player2Score);

        // Assert that both players got a score
        assertEquals("Player 1 should have scored 1 point", 1, player1Score);
        assertEquals("Player 2 should have scored 1 point", 1, player2Score);

        // Assert that both players moved successfully to their respective positions
        int[] player1Position = finalState.playerPositions.get(playerId1);
        int[] player2Position = finalState.playerPositions.get(playerId2);
        
        assertTrue("Player 1 should be at (5,5)",
            player1Position[0] == 5 && player1Position[1] == 5);
        assertTrue("Player 2 should be at (10,10)",
            player2Position[0] == 10 && player2Position[1] == 10);

        // Assert that both treasures have been removed
        assertTrue("Treasure at (5,5) should be removed", 
            finalState.treasurePositions.stream().noneMatch(t -> t[0] == 5 && t[1] == 5));
        assertTrue("Treasure at (10,10) should be removed", 
            finalState.treasurePositions.stream().noneMatch(t -> t[0] == 10 && t[1] == 10));
    }

    @After
    public void tearDown() throws RemoteException {
        // Deregister all players
        if (testPlayerList != null) {
            for (Player player : testPlayerList) {
                player.selfCleanupAndDeregister();
            }
            testPlayerList.clear();
        }
    }
 
}