package com.gaming.platform;

/**
 * Simple in-memory session store (singleton).
 * Holds the logged-in player's ID and last known balance.
 */
public class SessionManager {

    private static SessionManager instance;

    private String playerId;
    private double balance;

    private SessionManager() {}

    public static SessionManager get() {
        if (instance == null) instance = new SessionManager();
        return instance;
    }

    public void   setPlayerId(String id) { this.playerId = id; }
    public String getPlayerId()           { return playerId; }

    public void   setBalance(double b)   { this.balance = b; }
    public double getBalance()            { return balance; }

    /** Parse balance from server response strings like "Balance:123.45" or "100.0000 FUN" */
    public void parseAndSetBalance(String raw) {
        try {
            String s = raw.replaceAll("[^0-9.]", "");
            if (!s.isEmpty()) balance = Double.parseDouble(s);
        } catch (NumberFormatException ignored) {}
    }

    public void clear() {
        playerId = null;
        balance  = 0;
    }
}
