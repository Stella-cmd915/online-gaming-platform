import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Worker Node - stores games in memory and processes requests from Master.
 *
 * Each Worker is a multithreaded TCP server. Multiple Worker instances are
 * started as separate JVM processes (one per node), identified by their workerId.
 *
 * Responsibilities:
 *  - Store the subset of games assigned to it (via H(gameName) % numWorkers).
 *  - Handle ADD/REMOVE/UPDATE game commands from manager (via Master).
 *  - Execute the MAP phase for search and statistics queries.
 *  - Process PLAY requests: get random number from SRG, compute result, update stats.
 *  - Register each game with the SRG server on arrival.
 *
 * In-memory data structures (no disk, no database):
 *  - games:      HashMap<gameName, Game>
 *  - betRecords: ArrayList<BetRecord>  (for MapReduce player stats)
 *
 * Usage:
 *   java Worker <workerId>
 *   e.g.  java Worker 0   (reads worker.0.host / worker.0.port from config)
 */
public class Worker {

    private final int    workerId;
    private final int    port;
    private final String srgHost;
    private final int    srgPort;
    private final String reducerHost;
    private final int    reducerPort;

    // ------- In-memory stores -------

    // Primary game store: gameName -> Game object
    // synchronized access via addGame / removeGame / getGame / filterGames
    private final Map<String, Game> games = new HashMap<>();

    // Bet history for MapReduce player-profit queries
    // Wrapped in synchronized (betRecords) for concurrent write safety
    private final List<BetRecord> betRecords = new ArrayList<>();

    // -----------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Worker <workerId>");
            System.exit(1);
        }
        int id           = Integer.parseInt(args[0]);
        Properties cfg   = ConfigLoader.load();
        new Worker(id, cfg).start();
    }

    public Worker(int workerId, Properties cfg) {
        this.workerId    = workerId;
        this.port        = Integer.parseInt(cfg.getProperty("worker." + workerId + ".port"));
        this.srgHost     = cfg.getProperty("randomgenerator.host", "localhost");
        this.srgPort     = Integer.parseInt(cfg.getProperty("randomgenerator.port", "5005"));
        this.reducerHost = cfg.getProperty("reducer.host", "localhost");
        this.reducerPort = Integer.parseInt(cfg.getProperty("reducer.port", "5004"));
    }

    // -----------------------------------------------------------
    // Main server loop
    // -----------------------------------------------------------
    public void start() throws IOException {
        ServerSocket server = new ServerSocket(port);
        System.out.println("[Worker " + workerId + "] Listening on port " + port);
        while (true) {
            Socket masterConn = server.accept();
            Thread t = new Thread(new WorkerRequestHandler(masterConn, this));
            t.setDaemon(true);
            t.start();
        }
    }

    // -----------------------------------------------------------
    // Game management (synchronized to protect the games map)
    // -----------------------------------------------------------

    /** Add a new game to memory and register it with the SRG. */
    public synchronized void addGame(Game game) {
        games.put(game.getGameName(), game);
        System.out.println("[Worker " + workerId + "] Added game: " + game.getGameName());
        // Register with SRG so its producer thread starts filling the buffer
        registerWithSRG(game.getGameName(), game.getHashKey());
    }

    /** Mark a game as inactive (stats preserved; game hidden from players). */
    public synchronized void removeGame(String gameName) {
        Game g = games.get(gameName);
        if (g != null) {
            g.setActive(false);
            System.out.println("[Worker " + workerId + "] Removed game: " + gameName);
        }
    }

    /** Update the risk level (and thereby jackpot) of an existing game. */
    public synchronized void updateRisk(String gameName, String newRisk) {
        Game g = games.get(gameName);
        if (g != null) {
            g.updateRisk(newRisk);
            System.out.println("[Worker " + workerId + "] Updated risk for " + gameName + " -> " + newRisk);
        }
    }

    /** Retrieve a game by name (may be inactive). */
    public synchronized Game getGame(String gameName) {
        return games.get(gameName);
    }

    /**
     * MAP phase for SEARCH:
     * Returns all ACTIVE games that satisfy the given filter criteria.
     * Unset filters are passed as "-" (no filter applied).
     */
    public synchronized List<Game> filterGames(double minStars, String riskLevel, String betCategory) {
        List<Game> result = new ArrayList<>();
        for (Game g : games.values()) {
            if (!g.isActive()) continue;
            if (minStars > 0 && g.getStars() < minStars) continue;
            if (!riskLevel.equals("-")   && !g.getRiskLevel().equalsIgnoreCase(riskLevel))   continue;
            if (!betCategory.equals("-") && !g.getBetCategory().equalsIgnoreCase(betCategory)) continue;
            result.add(g);
        }
        return result;
    }

    /**
     * MAP phase for PROVIDER STATS:
     * Returns gameName -> playerProfit (= -houseProfit) for all games of that provider.
     * Inactive games are included (stats are always visible to manager).
     */
    public synchronized Map<String, Double> getProviderStats(String providerName) {
        Map<String, Double> stats = new HashMap<>();
        for (Game g : games.values()) {
            if (g.getProviderName().equals(providerName)) {
                // From the player perspective: positive = player won (house lost)
                stats.put(g.getGameName(), -g.getTotalHouseProfit());
            }
        }
        return stats;
    }

    /**
     * MAP phase for PLAYER STATS:
     * Returns the total profit/loss for a specific player across all games on this worker.
     */
    public double getPlayerProfitLoss(String playerId) {
        double total = 0.0;
        synchronized (betRecords) {
            for (BetRecord r : betRecords) {
                if (r.playerId.equals(playerId)) {
                    total += r.playerProfit;
                }
            }
        }
        return total;
    }

    /** Append a bet record (synchronized on betRecords list). */
    public void addBetRecord(BetRecord record) {
        synchronized (betRecords) {
            betRecords.add(record);
        }
    }

    // -----------------------------------------------------------
    // SRG communication
    // -----------------------------------------------------------

    /** Register a game with the SRG so its producer thread is created. */
    private void registerWithSRG(String gameId, String hashKey) {
        try (Socket s      = new Socket(srgHost, srgPort);
             PrintWriter out = new PrintWriter(s.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {

            out.println("REGISTER_GAME|" + gameId + "|" + hashKey);
            String resp = in.readLine(); // should be "OK"
            if (!"OK".equals(resp)) {
                System.err.println("[Worker " + workerId + "] SRG registration failed for " + gameId + ": " + resp);
            }
        } catch (IOException e) {
            System.err.println("[Worker " + workerId + "] Cannot reach SRG: " + e.getMessage());
        }
    }

    /**
     * Request one random integer from the SRG for a specific game.
     * Verifies the sha256(number + secret) integrity hash.
     *
     * @return the verified random integer
     * @throws Exception if communication fails or hash verification fails
     */
    public int getRandomFromSRG(String gameName, String secret) throws Exception {
        try (Socket s       = new Socket(srgHost, srgPort);
             PrintWriter out = new PrintWriter(s.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {

            out.println("GET_NUMBER|" + gameName);
            String response = in.readLine();

            if (response == null || !response.startsWith("NUMBER|")) {
                throw new Exception("Unexpected SRG response: " + response);
            }
            String[] parts = response.split("\\|");
            int    number       = Integer.parseInt(parts[1]);
            String receivedHash = parts[2];

            // Verify integrity: recompute sha256(number + secret) and compare
            String expectedHash = SRGServer.sha256(number + secret);
            if (!expectedHash.equals(receivedHash)) {
                throw new Exception("SRG hash verification FAILED for game: " + gameName);
            }
            return number;
        }
    }

    // -----------------------------------------------------------
    // Accessors used by WorkerRequestHandler
    // -----------------------------------------------------------
    public int    getWorkerId()    { return workerId; }
    public String getReducerHost() { return reducerHost; }
    public int    getReducerPort() { return reducerPort; }
}
