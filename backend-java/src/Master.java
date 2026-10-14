import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Master Node - central orchestrator of the distributed gaming system.
 *
 * Responsibilities:
 *  1. Accept TCP connections from clients (Manager console, Dummy Player, later Android).
 *  2. Route ADD_GAME / REMOVE_GAME / UPDATE_RISK to the correct Worker via
 *     the hash function: workerIndex = |hashCode(gameName)| % numWorkers.
 *  3. Orchestrate MapReduce for SEARCH and statistics queries:
 *       a. Register the job with the Reducer.
 *       b. Fan out MAP commands to all Workers in parallel.
 *       c. Wait for Reducer to send back the aggregated result.
 *  4. Manage player accounts (balance) in memory.
 *
 * Ports:
 *  - master.port         : main client-facing port (5000)
 *  - master.reducer.callback.port : receives REDUCE_RESULT from Reducer (5006)
 *
 * Workers and Reducer are assumed to be running before clients connect.
 *
 * Usage:
 *   java Master
 */
public class Master {

    private final int           port;
    private final int           callbackPort;
    private final String        myHost;
    private final String        reducerHost;
    private final int           reducerPort;
    private final int           numWorkers;
    private final List<String[]> workers;          // [host, port] per worker
    private final long          maprTimeout;

    // Player accounts: playerId -> balance (FUN tokens)
    // Must be synchronized — multiple ClientHandler threads access concurrently
    private final Map<String, Double> players = new HashMap<>();

    // Holds mapId -> result; ClientHandler threads wait here for MapReduce results
    private final PendingResultsMap pendingResults;

    // -----------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------
    public static void main(String[] args) throws Exception {
        Properties cfg = ConfigLoader.load();
        new Master(cfg).start();
    }

    public Master(Properties cfg) {
        this.port         = Integer.parseInt(cfg.getProperty("master.port",                   "5000"));
        this.callbackPort = Integer.parseInt(cfg.getProperty("master.reducer.callback.port",  "5006"));
        this.myHost       = cfg.getProperty("master.host",    "localhost");
        this.reducerHost  = cfg.getProperty("reducer.host",   "localhost");
        this.reducerPort  = Integer.parseInt(cfg.getProperty("reducer.port",                  "5004"));
        this.numWorkers   = Integer.parseInt(cfg.getProperty("worker.count",                  "3"));
        this.maprTimeout  = Long.parseLong(  cfg.getProperty("mapreduce.timeout.ms",          "30000"));

        this.pendingResults = new PendingResultsMap(maprTimeout);

        // Load worker addresses from config
        this.workers = new ArrayList<>();
        for (int i = 0; i < numWorkers; i++) {
            String h = cfg.getProperty("worker." + i + ".host", "localhost");
            String p = cfg.getProperty("worker." + i + ".port");
            workers.add(new String[]{h, p});
        }
    }

    // -----------------------------------------------------------
    // Startup
    // -----------------------------------------------------------
    public void start() throws IOException {
        // Start the Reducer callback listener in a background daemon thread
        Thread callbackThread = new Thread(
                new ReducerCallbackServer(callbackPort, pendingResults));
        callbackThread.setDaemon(true);
        callbackThread.start();

        // Main client-facing server loop
        ServerSocket server = new ServerSocket(port);
        System.out.println("[Master] Online Gaming System Master — port " + port);
        System.out.println("[Master] Workers: " + numWorkers
                + " | Reducer: " + reducerHost + ":" + reducerPort);

        while (true) {
            Socket client = server.accept();
            Thread t = new Thread(new ClientHandler(client, this));
            t.setDaemon(true);
            t.start();
        }
    }

    // -----------------------------------------------------------
    // Worker routing
    // -----------------------------------------------------------

    /** Hash game name to worker index: |hashCode(gameName)| % numWorkers */
    public int getWorkerIndex(String gameName) {
        return Math.abs(gameName.hashCode()) % numWorkers;
    }

    /**
     * Open a TCP connection to the given worker, send command, read one-line response.
     * Returns the response string, or an ERROR string if the worker is unreachable.
     */
    public String sendToWorker(int workerIndex, String command) {
        String[] info = workers.get(workerIndex);
        try (Socket s      = new Socket(info[0], Integer.parseInt(info[1]));
             PrintWriter out = new PrintWriter(s.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
            out.println(command);
            return in.readLine();
        } catch (IOException e) {
            System.err.println("[Master] Worker " + workerIndex + " unreachable: " + e.getMessage());
            return "ERROR|Worker " + workerIndex + " unreachable";
        }
    }

    // -----------------------------------------------------------
    // MapReduce orchestration
    // -----------------------------------------------------------

    /**
     * Register a MapReduce job with the Reducer.
     * Must be called before sending MAP commands to Workers.
     */
    private boolean registerJobWithReducer(String mapId, String type, int totalWorkers) {
        try (Socket s      = new Socket(reducerHost, reducerPort);
             PrintWriter out = new PrintWriter(s.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
            // REGISTER_JOB|mapId|type|totalWorkers|masterCallbackHost|masterCallbackPort
            out.println("REGISTER_JOB|" + mapId + "|" + type + "|"
                        + totalWorkers + "|" + myHost + "|" + callbackPort);
            String resp = in.readLine();
            return "OK".equals(resp);
        } catch (IOException e) {
            System.err.println("[Master] Cannot reach Reducer for job registration: " + e.getMessage());
            return false;
        }
    }

    /**
     * Execute MapReduce search across all Workers.
     * Returns semicolon-separated game entries, or empty string on timeout.
     */
    public String mapReduceSearch(double minStars, String riskLevel, String betCategory) {
        String mapId = UUID.randomUUID().toString();
        pendingResults.register(mapId);
        registerJobWithReducer(mapId, "SEARCH", numWorkers);

        // Fan out MAP_SEARCH to all workers in parallel
        for (int i = 0; i < numWorkers; i++) {
            final int idx = i;
            // MAP_SEARCH|mapId|redHost|redPort|minStars|riskLevel|betCategory|totalWorkers
            String cmd = "MAP_SEARCH|" + mapId + "|" + reducerHost + "|" + reducerPort
                         + "|" + minStars + "|" + riskLevel + "|" + betCategory + "|" + numWorkers;
            Thread t = new Thread(() -> sendToWorker(idx, cmd));
            t.setDaemon(true);
            t.start();
        }

        return waitForResult(mapId);
    }

    /**
     * Execute MapReduce provider stats across all Workers.
     * Returns semicolon-separated "gameName=profit" pairs plus "Total=X".
     */
    public String mapReduceProviderStats(String providerName) {
        String mapId = UUID.randomUUID().toString();
        pendingResults.register(mapId);
        registerJobWithReducer(mapId, "PROV_STATS", numWorkers);

        for (int i = 0; i < numWorkers; i++) {
            final int idx = i;
            String cmd = "MAP_PROV_STATS|" + mapId + "|" + reducerHost + "|" + reducerPort
                         + "|" + providerName + "|" + numWorkers;
            Thread t = new Thread(() -> sendToWorker(idx, cmd));
            t.setDaemon(true);
            t.start();
        }

        return waitForResult(mapId);
    }

    /**
     * Execute MapReduce player stats across all Workers.
     * Returns "totalProfit=X".
     */
    public String mapReducePlayerStats(String playerId) {
        String mapId = UUID.randomUUID().toString();
        pendingResults.register(mapId);
        registerJobWithReducer(mapId, "PLAY_STATS", numWorkers);

        for (int i = 0; i < numWorkers; i++) {
            final int idx = i;
            String cmd = "MAP_PLAY_STATS|" + mapId + "|" + reducerHost + "|" + reducerPort
                         + "|" + playerId + "|" + numWorkers;
            Thread t = new Thread(() -> sendToWorker(idx, cmd));
            t.setDaemon(true);
            t.start();
        }

        return waitForResult(mapId);
    }

    /** Block until the Reducer delivers the result for this mapId. */
    private String waitForResult(String mapId) {
        try {
            String result = pendingResults.waitForResult(mapId);
            return result != null ? result : "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    // -----------------------------------------------------------
    // Player account management (all synchronized on 'players' map)
    // -----------------------------------------------------------

    /**
     * Register a new player with 100 FUN starting balance.
     * @return true if newly created, false if player already exists (acts as login).
     */
    public synchronized boolean registerPlayer(String playerId) {
        if (players.containsKey(playerId)) return false;
        players.put(playerId, 100.0);
        System.out.println("[Master] New player registered: " + playerId);
        return true;
    }

    public synchronized boolean playerExists(String playerId) {
        return players.containsKey(playerId);
    }

    public synchronized boolean addBalance(String playerId, double amount) {
        if (!players.containsKey(playerId)) return false;
        players.merge(playerId, amount, Double::sum);
        return true;
    }

    public synchronized double getBalance(String playerId) {
        return players.getOrDefault(playerId, 0.0);
    }

    /**
     * Atomically deduct betAmount from player balance.
     * Returns false (does NOT deduct) if balance is insufficient.
     */
    public synchronized boolean deductBalance(String playerId, double amount) {
        double current = players.getOrDefault(playerId, 0.0);
        if (current < amount) return false;
        players.put(playerId, current - amount);
        return true;
    }

    /** Add winnings/refund to player balance. */
    public synchronized void addToBalance(String playerId, double amount) {
        players.merge(playerId, amount, Double::sum);
    }

    public int    getNumWorkers()  { return numWorkers; }
    public String getReducerHost() { return reducerHost; }
    public int    getReducerPort() { return reducerPort; }
}
