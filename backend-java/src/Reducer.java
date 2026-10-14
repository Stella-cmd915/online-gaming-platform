import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Reducer Node - aggregates intermediate map results from Workers.
 *
 * Architecture:
 *  - Multithreaded TCP server; one thread per incoming connection.
 *  - Receives REGISTER_JOB from Master (before MAP phase begins).
 *  - Receives MAP_RESULT from each Worker after the map phase.
 *  - Once all workers have reported for a job, executes the reduce function
 *    and sends the aggregated result back to Master via a callback connection.
 *
 * Reduce functions:
 *   SEARCH     → concatenate all game lists (union across workers)
 *   PROV_STATS → sum profits per game across workers, add "Total" row
 *   PLAY_STATS → sum player profit/loss across all workers
 *
 * Usage:
 *   java Reducer
 */
public class Reducer {

    private final int            port;
    private final ReduceJobManager jobManager = new ReduceJobManager();

    // -----------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------
    public static void main(String[] args) throws Exception {
        Properties cfg = ConfigLoader.load();
        int port = Integer.parseInt(cfg.getProperty("reducer.port", "5004"));
        new Reducer(port).start();
    }

    public Reducer(int port) { this.port = port; }

    // -----------------------------------------------------------
    // Main server loop
    // -----------------------------------------------------------
    public void start() throws IOException {
        ServerSocket server = new ServerSocket(port);
        System.out.println("[Reducer] Listening on port " + port);
        while (true) {
            Socket client = server.accept();
            Thread t = new Thread(new WorkerResultHandler(client, this));
            t.setDaemon(true);
            t.start();
        }
    }

    public ReduceJobManager getJobManager() { return jobManager; }

    // -----------------------------------------------------------
    // Reduce phase: aggregate all map results and send to Master
    // -----------------------------------------------------------

    /**
     * Called in a new thread when all workers have reported.
     * Executes the reduce function, then sends result to Master via TCP callback.
     */
    public void reduceAndSend(ReduceJobManager.ReduceJob job, String mapId) {
        String reducedData = reduce(job);
        System.out.println("[Reducer] Job " + mapId + " reduced. Sending to Master...");

        // Connect to Master's callback port and deliver the result
        try (Socket s      = new Socket(job.masterCallbackHost, job.masterCallbackPort);
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
            // REDUCE_RESULT|mapId|type|data
            out.println("REDUCE_RESULT|" + mapId + "|" + job.type + "|" + reducedData);
        } catch (IOException e) {
            System.err.println("[Reducer] Cannot send result to Master: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------
    // Reduce functions per query type
    // -----------------------------------------------------------

    private String reduce(ReduceJobManager.ReduceJob job) {
        switch (job.type) {
            case "SEARCH":     return reduceSearch(job.results);
            case "PROV_STATS": return reduceProvStats(job.results);
            case "PLAY_STATS": return reducePlayStats(job.results);
            default:
                System.err.println("[Reducer] Unknown job type: " + job.type);
                return "";
        }
    }

    /**
     * SEARCH reduce: concatenate all non-empty game lists.
     * Each worker returns semicolon-separated game entries (comma-fields inside).
     * Since each game belongs to exactly one worker, there are no duplicates.
     */
    private String reduceSearch(List<String> results) {
        StringBuilder sb = new StringBuilder();
        for (String r : results) {
            if (r == null || r.isEmpty()) continue;
            if (sb.length() > 0) sb.append(';');
            sb.append(r);
        }
        return sb.toString();
    }

    /**
     * PROV_STATS reduce: sum profits per game across workers.
     * Input format per worker: "game1=profit1;game2=profit2"
     * Output: "game1=profit1;game2=profit2;Total=totalProfit"
     */
    private String reduceProvStats(List<String> results) {
        Map<String, Double> totals = new LinkedHashMap<>();
        double grandTotal = 0.0;

        for (String r : results) {
            if (r == null || r.isEmpty()) continue;
            for (String item : r.split(";")) {
                String[] kv = item.split("=");
                if (kv.length < 2) continue;
                String gameName = kv[0];
                double profit   = Double.parseDouble(kv[1]);
                totals.merge(gameName, profit, Double::sum);
                grandTotal += profit;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Double> e : totals.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append('=').append(String.format("%.4f", e.getValue()));
        }
        sb.append(";Total=").append(String.format("%.4f", grandTotal));
        return sb.toString();
    }

    /**
     * PLAY_STATS reduce: sum player profit/loss from all workers.
     * Input per worker: "playerProfit=X"
     * Output: "totalProfit=X"
     */
    private String reducePlayStats(List<String> results) {
        double total = 0.0;
        for (String r : results) {
            if (r == null || r.isEmpty()) continue;
            String[] kv = r.split("=");
            if (kv.length >= 2) {
                try { total += Double.parseDouble(kv[1]); }
                catch (NumberFormatException ignored) {}
            }
        }
        return "totalProfit=" + String.format("%.4f", total);
    }
}
