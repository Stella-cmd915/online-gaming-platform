import java.io.*;
import java.net.Socket;

/**
 * Handles a persistent TCP connection from a client (Manager app or Dummy Player).
 * One ClientHandler thread is created per accepted connection on the Master.
 *
 * The connection stays open so the client can issue multiple commands in sequence.
 * Commands are pipe-delimited single lines followed by \n.
 *
 * Supported commands:
 *
 *   --- Player commands ---
 *   REGISTER_PLAYER|playerId
 *   ADD_BALANCE|playerId|amount
 *   SEARCH|playerId|minStars|riskLevel|betCategory       ("-" = no filter)
 *   PLAY|playerId|gameName|betAmount
 *   RATE_GAME|playerId|gameName|stars
 *
 *   --- Manager commands ---
 *   ADD_GAME|name|provider|stars|votes|logo|minBet|maxBet|risk|hashKey
 *   REMOVE_GAME|gameName
 *   UPDATE_RISK|gameName|newRiskLevel
 *   GET_PROVIDER_STATS|providerName
 *   GET_PLAYER_STATS|playerId
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final Master master;

    public ClientHandler(Socket socket, Master master) {
        this.socket = socket;
        this.master = master;
    }

    @Override
    public void run() {
        try (BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter    out = new PrintWriter(socket.getOutputStream(), true)) {

            String line;
            // Read commands until client disconnects
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) continue;
                process(line, out);
            }

        } catch (IOException e) {
            // Client disconnected — not an error
        }
    }

    private void process(String line, PrintWriter out) {
        String[] parts = line.split("\\|");
        String   cmd   = parts[0];

        switch (cmd) {

            // --------------------------------------------------------
            // REGISTER_PLAYER|playerId
            // --------------------------------------------------------
            case "REGISTER_PLAYER": {
                String pid = parts[1];
                boolean created = master.registerPlayer(pid);
                if (created) {
                    out.println("OK|Registered. Starting balance: "
                            + master.getBalance(pid) + " FUN");
                } else {
                    // Return current balance if already registered (acts as login)
                    out.println("OK|Welcome back " + pid + ". Balance: "
                            + String.format("%.4f", master.getBalance(pid)) + " FUN");
                }
                break;
            }

            // --------------------------------------------------------
            // ADD_BALANCE|playerId|amount
            // --------------------------------------------------------
            case "ADD_BALANCE": {
                String pid    = parts[1];
                double amount = Double.parseDouble(parts[2]);
                if (master.addBalance(pid, amount)) {
                    out.println("OK|New balance: "
                            + String.format("%.4f", master.getBalance(pid)) + " FUN");
                } else {
                    out.println("ERROR|Player not found: " + pid);
                }
                break;
            }

            // --------------------------------------------------------
            // SEARCH|playerId|minStars|riskLevel|betCategory
            // ("-" means no filter for that field)
            // --------------------------------------------------------
            case "SEARCH": {
                double minStars   = parts[2].equals("-") ? 0.0 : Double.parseDouble(parts[2]);
                String riskLevel  = parts[3];
                String betCat     = parts[4];
                String result     = master.mapReduceSearch(minStars, riskLevel, betCat);
                // SEARCH_RESULT|game1;game2;...
                out.println("SEARCH_RESULT|" + (result == null ? "" : result));
                break;
            }

            // --------------------------------------------------------
            // PLAY|playerId|gameName|betAmount
            // --------------------------------------------------------
            case "PLAY": {
                String pid       = parts[1];
                String gameName  = parts[2];
                double betAmount = Double.parseDouble(parts[3]);

                if (!master.playerExists(pid)) {
                    out.println("ERROR|Player not registered");
                    break;
                }
                if (betAmount <= 0) {
                    out.println("ERROR|Bet amount must be positive");
                    break;
                }
                // Deduct bet from balance first (refund on error)
                if (!master.deductBalance(pid, betAmount)) {
                    out.println("ERROR|Insufficient balance (have "
                            + String.format("%.4f", master.getBalance(pid)) + " FUN)");
                    break;
                }

                // Forward play request to the correct Worker
                int    workerIdx = master.getWorkerIndex(gameName);
                String resp      = master.sendToWorker(workerIdx,
                                         "PLAY|" + pid + "|" + gameName + "|" + betAmount);

                if (resp != null && resp.startsWith("PLAY_RESULT")) {
                    // PLAY_RESULT|payout|playerProfit|resultType
                    String[] rp     = resp.split("\\|");
                    double   payout = Double.parseDouble(rp[1]);
                    master.addToBalance(pid, payout); // add winnings
                    out.println(resp + "|Balance:" + String.format("%.4f", master.getBalance(pid)));
                } else {
                    // Something went wrong — refund the bet
                    master.addToBalance(pid, betAmount);
                    out.println(resp != null ? resp : "ERROR|Play failed");
                }
                break;
            }

            // --------------------------------------------------------
            // RATE_GAME|playerId|gameName|stars
            // --------------------------------------------------------
            case "RATE_GAME": {
                String gameName = parts[2];
                int    stars    = Integer.parseInt(parts[3]);
                if (stars < 1 || stars > 5) { out.println("ERROR|Stars must be 1-5"); break; }
                int    wIdx     = master.getWorkerIndex(gameName);
                String resp     = master.sendToWorker(wIdx, "RATE_GAME|" + gameName + "|" + stars);
                out.println(resp != null ? resp : "ERROR|Worker unreachable");
                break;
            }

            // --------------------------------------------------------
            // ADD_GAME|name|provider|stars|votes|logo|minBet|maxBet|risk|hashKey
            // --------------------------------------------------------
            case "ADD_GAME": {
                String gameName = parts[1];
                int    wIdx     = master.getWorkerIndex(gameName);
                // Forward the entire ADD_GAME line to the responsible Worker
                String resp     = master.sendToWorker(wIdx, line);
                out.println(resp != null ? resp : "ERROR|Worker unreachable");
                break;
            }

            // --------------------------------------------------------
            // REMOVE_GAME|gameName
            // --------------------------------------------------------
            case "REMOVE_GAME": {
                int    wIdx = master.getWorkerIndex(parts[1]);
                String resp = master.sendToWorker(wIdx, line);
                out.println(resp != null ? resp : "ERROR|Worker unreachable");
                break;
            }

            // --------------------------------------------------------
            // UPDATE_RISK|gameName|newRiskLevel
            // --------------------------------------------------------
            case "UPDATE_RISK": {
                int    wIdx = master.getWorkerIndex(parts[1]);
                String resp = master.sendToWorker(wIdx, line);
                out.println(resp != null ? resp : "ERROR|Worker unreachable");
                break;
            }

            // --------------------------------------------------------
            // GET_PROVIDER_STATS|providerName
            // (MapReduce across all Workers; Παραδοτέο Β main use)
            // --------------------------------------------------------
            case "GET_PROVIDER_STATS": {
                String providerName = parts[1];
                String result       = master.mapReduceProviderStats(providerName);
                out.println("STATS_RESULT|" + (result == null ? "" : result));
                break;
            }

            // --------------------------------------------------------
            // GET_PLAYER_STATS|playerId
            // (MapReduce across all Workers)
            // --------------------------------------------------------
            case "GET_PLAYER_STATS": {
                String pid    = parts[1];
                String result = master.mapReducePlayerStats(pid);
                out.println("STATS_RESULT|" + (result == null ? "" : result));
                break;
            }

            default:
                out.println("ERROR|Unknown command: " + cmd);
        }
    }
}
