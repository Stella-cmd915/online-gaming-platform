/**
 * Immutable record of a single bet placed by a player on a specific game.
 * Stored in the Worker's in-memory list; used for MapReduce statistics queries.
 *
 * playerProfit = payout - betAmount
 *   > 0 : player won
 *   = 0 : break-even
 *   < 0 : player lost
 */
public class BetRecord {

    public final String playerId;
    public final String gameName;
    public final double betAmount;
    public final double payout;        // amount returned to the player (0 if total loss)
    public final double playerProfit;  // payout - betAmount

    public BetRecord(String playerId, String gameName, double betAmount, double payout) {
        this.playerId     = playerId;
        this.gameName     = gameName;
        this.betAmount    = betAmount;
        this.payout       = payout;
        this.playerProfit = payout - betAmount;
    }
}
