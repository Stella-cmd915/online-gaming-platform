import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Handles a single TCP connection from the Master to a Worker.
 * Each incoming connection spawns one WorkerRequestHandler thread.
 *
 * Supported commands (pipe-delimited, received from Master):
 *
 *   ADD_GAME    |name|provider|stars|votes|logo|minBet|maxBet|risk|hashKey
 *   REMOVE_GAME |gameName
 *   UPDATE_RISK |gameName|newRisk
 *   MAP_SEARCH  |mapId|reducerHost|reducerPort|minStars|riskLevel|betCategory|totalWorkers
 *   MAP_PROV_STATS |mapId|reducerHost|reducerPort|providerName|totalWorkers
 *   MAP_PLAY_STATS |mapId|reducerHost|reducerPort|playerId|totalWorkers
 *   PLAY        |playerId|gameName|betAmount
 *   RATE_GAME   |gameName|stars
 *
 * MAP results are sent directly to the Reducer (not back to Master).
 * PLAY result is sent back to Master on the same socket.
 */
public class WorkerRequestHandler implements Runnable {

    private final Socket masterSocket;
    private final Worker worker;

    public WorkerRequestHandler(Socket masterSocket, Worker worker) {
        this.masterSocket = masterSocket;
        this.worker       = worker;
    }

    @Override
    public void run() {
        try (BufferedReader in  = new BufferedReader(new InputStreamReader(masterSocket.getInputStream()));
             PrintWriter    out = new PrintWriter(masterSocket.getOutputStream(), true)) {

            String line = in.readLine();
            if (line == null) return;

            String[] parts = line.split("\\|");
            String   cmd   = parts[0];

            switch (cmd) {
                case "ADD_GAME":        handleAddGame(parts, out);     break;
                case "REMOVE_GAME":     handleRemoveGame(parts, out);  break;
                case "UPDATE_RISK":     handleUpdateRisk(parts, out);  break;
                case "MAP_SEARCH":      handleMapSearch(parts, out);   break;
                case "MAP_PROV_STATS":  handleMapProvStats(parts, out);break;
                case "MAP_PLAY_STATS":  handleMapPlayStats(parts, out);break;
                case "PLAY":            handlePlay(parts, out);        break;
                case "RATE_GAME":       handleRateGame(parts, out);    break;
                default:
                    out.println("ERROR|Unknown command: " + cmd);
            }

        } catch (IOException e) {
            System.err.println("[Worker-" + worker.getWorkerId() + "] Request error: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------
    // ADD_GAME|name|provider|stars|votes|logo|minBet|maxBet|risk|hashKey
    // -----------------------------------------------------------
    private void handleAddGame(String[] p, PrintWriter out) {
        Game g = new Game(p[1], p[2],
                Double.parseDouble(p[3]), Integer.parseInt(p[4]),
                p[5], Double.parseDouble(p[6]), Double.parseDouble(p[7]),
                p[8], p[9]);
        worker.addGame(g);
        out.println("OK");
    }

    // -----------------------------------------------------------
    // REMOVE_GAME|gameName
    // -----------------------------------------------------------
    private void handleRemoveGame(String[] p, PrintWriter out) {
        worker.removeGame(p[1]);
        out.println("OK");
    }

    // -----------------------------------------------------------
    // UPDATE_RISK|gameName|newRisk
    // -----------------------------------------------------------
    private void handleUpdateRisk(String[] p, PrintWriter out) {
        worker.updateRisk(p[1], p[2]);
        out.println("OK");
    }

    // -----------------------------------------------------------
    // MAP_SEARCH|mapId|redHost|redPort|minStars|riskLevel|betCategory|totalWorkers
    // MAP phase: filter this worker's games and forward results to Reducer.
    // -----------------------------------------------------------
    private void handleMapSearch(String[] p, PrintWriter out) {
        String mapId      = p[1];
        String redHost    = p[2];
        int    redPort    = Integer.parseInt(p[3]);
        double minStars   = Double.parseDouble(p[4]);
        String riskLevel  = p[5];
        String betCat     = p[6];
        int    totalWorkers = Integer.parseInt(p[7]);

        // Execute the map: filter games on this worker
        List<Game> filtered = worker.filterGames(minStars, riskLevel, betCat);

        // Serialize filtered games as semicolon-separated list of comma-fields
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < filtered.size(); i++) {
            if (i > 0) sb.append(';');
            sb.append(filtered.get(i).toNetwork());
        }

        // Send map result to Reducer (async from master's perspective)
        sendMapResult(redHost, redPort, mapId, "SEARCH", sb.toString());
        out.println("OK");
    }

    // -----------------------------------------------------------
    // MAP_PROV_STATS|mapId|redHost|redPort|providerName|totalWorkers
    // MAP phase: collect provider stats from this worker's games.
    // -----------------------------------------------------------
    private void handleMapProvStats(String[] p, PrintWriter out) {
        String mapId    = p[1];
        String redHost  = p[2];
        int    redPort  = Integer.parseInt(p[3]);
        String provider = p[4];
        // p[5] = totalWorkers (not needed here; already registered with Reducer by Master)

        Map<String, Double> stats = worker.getProviderStats(provider);

        // Serialize: gameName=profit;gameName2=profit2
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Double> e : stats.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append('=').append(String.format("%.4f", e.getValue()));
        }

        sendMapResult(redHost, redPort, mapId, "PROV_STATS", sb.toString());
        out.println("OK");
    }

    // -----------------------------------------------------------
    // MAP_PLAY_STATS|mapId|redHost|redPort|playerId|totalWorkers
    // MAP phase: sum this worker's bets for the given player.
    // -----------------------------------------------------------
    private void handleMapPlayStats(String[] p, PrintWriter out) {
        String mapId   = p[1];
        String redHost = p[2];
        int    redPort = Integer.parseInt(p[3]);
        String pid     = p[4];

        double profit = worker.getPlayerProfitLoss(pid);

        // Single key-value: playerProfit=X
        sendMapResult(redHost, redPort, mapId, "PLAY_STATS",
                     "playerProfit=" + String.format("%.4f", profit));
        out.println("OK");
    }

    // -----------------------------------------------------------
    // PLAY|playerId|gameName|betAmount
    // Process a bet: get random number from SRG, compute win/loss, update stats.
    // -----------------------------------------------------------
    private void handlePlay(String[] p, PrintWriter out) {
        String playerId  = p[1];
        String gameName  = p[2];
        double betAmount = Double.parseDouble(p[3]);

        Game game = worker.getGame(gameName);
        if (game == null || !game.isActive()) {
            out.println("ERROR|Game not found: " + gameName);
            return;
        }

        try {
            // Get a verified random integer from the SRG (via TCP + SHA-256 check)
            int randomNum = worker.getRandomFromSRG(gameName, game.getHashKey());

            // --- Compute win/loss ---
            double payout;
            String resultType;

            if (randomNum % 100 == 0) {
                // Lucky: JACKPOT! Multiply bet by jackpot value.
                payout     = betAmount * game.getJackpot();
                resultType = "JACKPOT";
            } else {
                // Normal outcome: use (randomNum % 10) as index into multiplier table
                int    idx        = randomNum % 10;
                double multiplier = game.getMultipliers()[idx];
                payout     = betAmount * multiplier;
                resultType = multiplier > 1.0 ? "WIN" : (multiplier > 0 ? "PARTIAL" : "LOSS");
            }

            double playerProfit = payout - betAmount;
            double houseProfit  = betAmount - payout; // opposite of player profit

            // Update game stats (synchronized inside Game)
            game.addHouseProfit(houseProfit);

            // Record this bet for later MapReduce stats queries
            worker.addBetRecord(new BetRecord(playerId, gameName, betAmount, payout));

            // Respond to Master: PLAY_RESULT|payout|playerProfit|resultType
            out.println("PLAY_RESULT|" + String.format("%.4f", payout)
                        + "|" + String.format("%.4f", playerProfit) + "|" + resultType);

        } catch (Exception e) {
            System.err.println("[Worker] Play failed: " + e.getMessage());
            out.println("ERROR|" + e.getMessage());
        }
    }

    // -----------------------------------------------------------
    // RATE_GAME|gameName|stars
    // -----------------------------------------------------------
    private void handleRateGame(String[] p, PrintWriter out) {
        Game g = worker.getGame(p[1]);
        if (g == null) {
            out.println("ERROR|Game not found: " + p[1]);
            return;
        }
        g.addRating(Integer.parseInt(p[2]));
        out.println("OK");
    }

    // -----------------------------------------------------------
    // Helper: open a TCP connection to the Reducer and send a MAP_RESULT.
    // Format: MAP_RESULT|mapId|type|data
    // -----------------------------------------------------------
    private void sendMapResult(String host, int port, String mapId, String type, String data) {
        try (Socket s      = new Socket(host, port);
             PrintWriter o = new PrintWriter(s.getOutputStream(), true)) {
            o.println("MAP_RESULT|" + mapId + "|" + type + "|" + data);
        } catch (IOException e) {
            System.err.println("[Worker-" + worker.getWorkerId()
                    + "] Failed to send MAP_RESULT to Reducer: " + e.getMessage());
        }
    }
}
