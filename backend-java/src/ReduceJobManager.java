import java.util.*;

/**
 * Thread-safe registry of active MapReduce reduce jobs.
 *
 * Lifecycle of a reduce job:
 *  1. Master calls register() with a new mapId before sending MAP commands to Workers.
 *  2. As each Worker sends its MAP_RESULT, addResult() is called.
 *  3. When the last expected result arrives, addResult() returns the completed job.
 *  4. The Reducer then performs the reduce phase and sends the result to Master.
 *
 * All public methods are synchronized using the object monitor (no java.util.concurrent).
 */
public class ReduceJobManager {

    /** Represents a single pending reduce job. */
    public static class ReduceJob {
        public final String       type;                 // SEARCH | PROV_STATS | PLAY_STATS
        public final int          totalWorkers;         // how many MAP results to expect
        public       int          received;             // how many have arrived so far
        public final List<String> results;              // accumulated map results
        public final String       masterCallbackHost;
        public final int          masterCallbackPort;

        public ReduceJob(String type, int totalWorkers,
                         String masterCallbackHost, int masterCallbackPort) {
            this.type               = type;
            this.totalWorkers       = totalWorkers;
            this.received           = 0;
            this.results            = new ArrayList<>();
            this.masterCallbackHost = masterCallbackHost;
            this.masterCallbackPort = masterCallbackPort;
        }

        public boolean isComplete() { return received >= totalWorkers; }
    }

    // mapId -> ReduceJob
    private final Map<String, ReduceJob> jobs = new HashMap<>();

    // -----------------------------------------------------------
    // Called by Master (via REGISTER_JOB command) before MAP phase
    // -----------------------------------------------------------
    public synchronized void register(String mapId, ReduceJob job) {
        jobs.put(mapId, job);
    }

    // -----------------------------------------------------------
    // Called by WorkerResultHandler when a MAP_RESULT arrives.
    // Returns the ReduceJob if all workers have reported; otherwise null.
    // -----------------------------------------------------------
    public synchronized ReduceJob addResult(String mapId, String data) {
        ReduceJob job = jobs.get(mapId);
        if (job == null) {
            System.err.println("[Reducer] Received result for unknown mapId: " + mapId);
            return null;
        }
        job.results.add(data != null ? data : "");
        job.received++;

        if (job.isComplete()) {
            jobs.remove(mapId); // job is done; remove from pending
            return job;
        }
        return null; // still waiting for more workers
    }
}
