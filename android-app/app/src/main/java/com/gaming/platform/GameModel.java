package com.gaming.platform;

/**
 * Local model for a game entry received from the Master (SEARCH_RESULT).
 *
 * Network format (comma-separated):
 *   name,provider,stars,betCategory,minBet,maxBet,riskLevel,jackpot
 */
public class GameModel {

    public final String name;
    public final String provider;
    public final double stars;
    public final String betCategory;   // $, $$, $$$
    public final double minBet;
    public final double maxBet;
    public final String riskLevel;     // low, medium, high
    public final int    jackpot;       // multiplier

    public GameModel(String name, String provider, double stars, String betCategory,
                     double minBet, double maxBet, String riskLevel, int jackpot) {
        this.name        = name;
        this.provider    = provider;
        this.stars       = stars;
        this.betCategory = betCategory;
        this.minBet      = minBet;
        this.maxBet      = maxBet;
        this.riskLevel   = riskLevel;
        this.jackpot     = jackpot;
    }

    /**
     * Parse one game entry from the network format.
     * Returns null if format is invalid.
     */
    public static GameModel fromNetwork(String data) {
        try {
            String[] f = data.split(",");
            if (f.length < 8) return null;
            return new GameModel(
                f[0].trim(),
                f[1].trim(),
                Double.parseDouble(f[2].trim()),
                f[3].trim(),
                Double.parseDouble(f[4].trim()),
                Double.parseDouble(f[5].trim()),
                f[6].trim(),
                Integer.parseInt(f[7].trim())
            );
        } catch (Exception e) {
            return null;
        }
    }

    /** Display text for the game list card */
    public String starsDisplay() {
        StringBuilder sb = new StringBuilder();
        int full = (int) stars;
        for (int i = 0; i < full; i++) sb.append("★");
        for (int i = full; i < 5; i++) sb.append("☆");
        return sb.toString() + String.format(" %.1f", stars);
    }
}
