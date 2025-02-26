package src;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
interface TrackerInterface extends Remote {
    boolean registerPlayer(Player player) throws RemoteException;
    void deregisterPlayer(Player player) throws RemoteException;
    // NOTE: Tracker should have no idea about the primary server or backup server
    int getN() throws RemoteException;
    int getK() throws RemoteException;
    ArrayList<Player> getPlayerList() throws RemoteException;
}

public class Tracker implements TrackerInterface {
    private ArrayList<Player> playerList;
    private int port_number;
    private int n_grid;
    private int k_treasure;
    
    public int getPortNumber() {
        return port_number;
    }
    public void setPortNumber(int port_number) {
        this.port_number = port_number;
    }

    @Override
    public int getN() throws RemoteException {
        return n_grid;
    }
    public void setN(int n_grid) {
        this.n_grid = n_grid;
    }

    @Override
    public int getK() throws RemoteException {
        return k_treasure;
    }
    public void setK(int k_treasure) {
        this.k_treasure = k_treasure;
    }

    public Tracker() {
        playerList = new ArrayList<>();
    }

    @Override
    public ArrayList<Player> getPlayerList() throws RemoteException {
        return playerList;
    }

    @Override
    public synchronized boolean registerPlayer(Player player) throws RemoteException {
        try {
            System.out.println("New player is trying to register: " + player.getPlayerId());
            System.out.println("Current number of players: " + (playerList.size()));
            // Check if the player ID already exists
            String newPlayerId = player.getPlayerId();
            for (Player p : playerList) {
                // There is a chance the player is already killed
                try {
                    System.out.println("Player in the playerList: " + p.getPlayerId());
                } catch (RemoteException e) {
                    // System.out.println("Player is no longer available");
                    continue;
                }
                if (p.getPlayerId().equals(newPlayerId)) {
                    System.out.println("Player with ID " + newPlayerId + " already exists. Ignoring registration.");
                    return false;
                }
            }
            playerList.add(player);
            System.out.println("New player registered: " + player.getPlayerId());
            System.out.println("Current number of players: " + (playerList.size()));
            
            // Trigger server election by the new player
            player.initiateElection(playerList);
            return true;
        } catch (RemoteException e) {
            System.err.println("Error registering player: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void deregisterPlayer(Player player) throws RemoteException {
        System.out.println("Player deregistered: " + player.getPlayerId());
        playerList.remove(player);
    }

    public void regTracker(){
        try {
            TrackerInterface stub = (TrackerInterface) UnicastRemoteObject.exportObject(this, 0);
            Registry registry = LocateRegistry.createRegistry(port_number);
            registry.bind("Tracker", stub);   
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
 
    public static void main(String[] args) {
        // check the num of args
        if (args.length != 3) {
            System.out.println("Error: You must provide exactly 3 arguments.");
            System.out.println("Usage: java Tracker <port_number> <N> <K>");
            return;
        }
        try {
            Tracker tracker = new Tracker();
        
            int port_number = Integer.parseInt(args[0]);
            int N = Integer.parseInt(args[1]);
            int K = Integer.parseInt(args[2]);

            System.out.println("Port Number: " + port_number);
            System.out.println("N: " + N);
            System.out.println("K: " + K);

            tracker.setPortNumber(port_number);
            tracker.setN(N);
            tracker.setK(K);

            tracker.regTracker();
            System.out.println("Tracker ready");
        } catch (NumberFormatException e) {
            System.out.println("Error: All arguments must be valid integers.");
        }        
    }
}
