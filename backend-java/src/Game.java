/**
 * Represents an online game stored in a Worker node's in-memory data structures.
 *
 * Key design decisions:
 *  - betCategory and jackpot are NOT stored in JSON; they are auto-computed.
 *  - totalHouseProfit and rating updates use synchronized methods to ensure
 *    correctness under concurrent bets from multiple players on the same game.
 *  - Removed games (active=false) remain in memory so manager stats still work.
 */
public class Game {

    private final String gameName;
    private final String providerName;
    private double       stars;
    private int          noOfVotes;
    private final String gameLogo;
    private final double minBet;
    private final double maxBet;
    private String       riskLevel;          // low | medium | high
    private final String hashKey;            // shared secret with SRG
    private final String betCategory;        // $  | $$ | $$$ (computed)
    private int          jackpot;            // 10 | 20 | 40   (computed)
    private boolean      active;
    private double       totalHouseProfit;   // sum of (bet - payout); positive = house won

    // -----------------------------------------------------------
    // Risk-level multiplier tables (index = randomNumber % 10)
    // -----------------------------------------------------------
    public static final double[] LOW_MULT    = {0.0,0.0,0.0,0.1,0.5,1.0,1.1,1.3,2.0,2.5};
    public static final double[] MEDIUM_MULT = {0.0,0.0,0.0,0.0,0.0,0.5,1.0,1.5,2.5,3.5};
    public static final double[] HIGH_MULT   = {0.0,0.0,0.0,0.0,0.0,0.0,0.0,1.0,2.0,6.5};

    // -----------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------
    public Game(String gameName, String providerName, double stars, int noOfVotes,
                String gameLogo, double minBet, double maxBet,
                String riskLevel, String hashKey) {
        this.gameName        = gameName;
        this.providerName    = providerName;
        this.stars           = stars;
        this.noOfVotes       = noOfVotes;
        this.gameLogo        = gameLogo;
        this.minBet          = minBet;
        this.maxBet          = maxBet;
        this.riskLevel       = riskLevel.toLowerCase();
        this.hashKey         = hashKey;
        this.active          = true;
        this.totalHouseProfit = 0.0;
        // Auto-computed fields (not in JSON)
        this.betCategory = computeBetCategory(minBet);
        this.jackpot     = computeJackpot(this.riskLevel);
    }

    // -----------------------------------------------------------
    // Static helpers (also used by other components)
    // -----------------------------------------------------------

    /** Bet category based on minimum bet: 0.1 -> $, 1.0 -> $$, 5.0 -> $$$ */
    public static String computeBetCategory(double minBet) {
        if (minBet <= 0.1) return "$";
        if (minBet <= 1.0) return "$$";
        return "$$$";
    }

    /** Jackpot multiplier based on risk level */
    public static int computeJackpot(String risk) {
        switch (risk.toLowerCase()) {
            case "medium": return 20;
            case "high":   return 40;
            default:       return 10; // low
        }
    }

    // -----------------------------------------------------------
    // Synchronized mutation methods (concurrent bet safety)
    // -----------------------------------------------------------

    /**
     * Update risk level and recompute jackpot.
     * Called by manager via UPDATE_RISK command.
     */
    public synchronized void updateRisk(String newRisk) {
        this.riskLevel = newRisk.toLowerCase();
        this.jackpot   = computeJackpot(this.riskLevel);
    }

    /**
     * Record the house profit from one bet.
     * houseProfit = betAmount - payout  (positive = house won money)
     * Must be synchronized: multiple players may bet concurrently.
     */
    public synchronized void addHouseProfit(double houseProfit) {
        this.totalHouseProfit += houseProfit;
    }

    public synchronized double getTotalHouseProfit() {
        return totalHouseProfit;
    }

    /**
     * Add a player rating and recompute the running average (synchronized).
     */
    public synchronized void addRating(int newStars) {
        double total = this.stars * this.noOfVotes + newStars;
        this.noOfVotes++;
        this.stars = total / this.noOfVotes;
    }

    // -----------------------------------------------------------
    // Utility
    // -----------------------------------------------------------

    /** Return the multiplier table for this game's current risk level */
    public double[] getMultipliers() {
        switch (riskLevel) {
            case "medium": return MEDIUM_MULT;
            case "high":   return HIGH_MULT;
            default:       return LOW_MULT;
        }
    }

    /**
     * Serialize to comma-separated string for network transport.
     * Format: name,provider,stars,betCategory,minBet,maxBet,riskLevel,jackpot
     */
    public String toNetwork() {
        return gameName + "," + providerName + "," + String.format("%.2f", stars) + ","
               + betCategory + "," + minBet + "," + maxBet + "," + riskLevel + "," + jackpot;
    }

    /** Human-readable one-liner for console display */
    public String toDisplayString() {
        return String.format("  %-22s | %-14s | Stars: %.1f (%d) | Bet: %-3s | "
               + "Risk: %-6s | %.2f-%.2f FUN | Jackpot: %dx",
               gameName, providerName, stars, noOfVotes,
               betCategory, riskLevel, minBet, maxBet, jackpot);
    }

    // -----------------------------------------------------------
    // Getters
    // -----------------------------------------------------------
    public String  getGameName()      { return gameName; }
    public String  getProviderName()  { return providerName; }
    public synchronized double getStars()    { return stars; }
    public synchronized int    getNoOfVotes(){ return noOfVotes; }
    public double  getMinBet()        { return minBet; }
    public double  getMaxBet()        { return maxBet; }
    public String  getRiskLevel()     { return riskLevel; }
    public String  getHashKey()       { return hashKey; }
    public String  getBetCategory()   { return betCategory; }
    public int     getJackpot()       { return jackpot; }
    public boolean isActive()         { return active; }
    public void    setActive(boolean a){ this.active = a; }
}
