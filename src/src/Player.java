package src;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.io.Serializable;
import src.Game.ServerRole;
import java.util.Map;
import java.util.List;

public interface Player extends Remote {
    String getPlayerId() throws RemoteException;
    void setPlayerId(String playerId) throws RemoteException;
    Player getPrimaryServer() throws RemoteException;
    void clearReceivedPlayerIds() throws RemoteException;
    void setPlayerList(ArrayList<Player> playerList) throws RemoteException;
    void broadcastId() throws RemoteException;
    String ping() throws RemoteException;
    void receiveId(String playerId) throws RemoteException;
    void electServers() throws RemoteException;
    void promoteBackupToPrimary() throws RemoteException;
    ServerRole getServerRole() throws RemoteException;
    void selfCleanupAndDeregister() throws RemoteException;
    void initiateElection(ArrayList<Player> players) throws RemoteException;
    
    // ===== Start of Primary server methods =====
    GameState updateGamebyNewMove(Player player, int x, int y) throws RemoteException;
    // Request the game state from primary server by other players
    GameState getGameState(Player requester) throws RemoteException;
    boolean isGameInitialized() throws RemoteException;
    // Update the game state to new primary server from old primary server
    void updateGameState(GameState gameState) throws RemoteException;
    void demoteToBackup() throws RemoteException;
    void demoteToPlayer() throws RemoteException;
    void removePlayerGameState(Player player) throws RemoteException;
    void updatePlayerList() throws RemoteException;
    // ===== End of Primary server methods =====

    public static class GameState implements Serializable{
        // Flag to indicate whether the game is initialized
        public boolean isGameInitialized;
        public ArrayList<Player> playerList;
        public Map<String, int[]> playerPositions;
        public List<int[]> treasurePositions;
        public Map<String, Integer> playerScores;
        public String startTime;
        GameState(boolean isGameInitialized,
                  ArrayList<Player> playerList,
                  Map<String, int[]> playerPositions,
                  List<int[]> treasurePositions,
                  Map<String, Integer> playerScores,
                  String startTime) {
            this.isGameInitialized = isGameInitialized;
            this.playerList = playerList;
            this.playerPositions = playerPositions;
            this.treasurePositions = treasurePositions;
            this.playerScores = playerScores;
            this.startTime = startTime;
        }
    }
}
