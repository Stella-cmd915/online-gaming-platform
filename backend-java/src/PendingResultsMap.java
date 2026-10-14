import java.util.*;

/**
 * Thread-safe map used by the Master to block-wait for Reducer results.
 *
 * Flow:
 *  1. Master thread calls register(mapId) to declare it expects a result.
 *  2. Master thread calls waitForResult(mapId) and blocks (wait()).
 *  3. ReducerCallbackServer calls setResult(mapId, data) when the Reducer responds.
 *  4. notifyAll() wakes the waiting Master thread; it retrieves and removes the result.
 *
 * Only synchronized / wait / notify are used (no java.util.concurrent).
 */
public class PendingResultsMap {

    // null value = registered but not yet fulfilled
    private final Map<String, String> results = new HashMap<>();
    private final long timeoutMs;

    public PendingResultsMap(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /** Register interest in a mapId result (called before MAP phase starts). */
    public synchronized void register(String mapId) {
        results.put(mapId, null);
    }

    /**
     * Called by ReducerCallbackServer when the Reducer delivers a result.
     * Wakes all threads waiting on this monitor.
     */
    public synchronized void setResult(String mapId, String data) {
        if (results.containsKey(mapId)) {
            results.put(mapId, data);
            notifyAll(); // wake Master threads waiting in waitForResult()
        } else {
            System.err.println("[PendingResults] Received result for unregistered mapId: " + mapId);
        }
    }

    /**
     * Block until the Reducer delivers the result for mapId or timeout expires.
     * Removes the entry from the map on return.
     *
     * @return the result string, or null on timeout.
     */
    public synchronized String waitForResult(String mapId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (results.get(mapId) == null) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                System.err.println("[PendingResults] Timeout waiting for mapId: " + mapId);
                break;
            }
            wait(remaining);
        }
        return results.remove(mapId);
    }
}
