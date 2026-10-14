import java.io.*;
import java.net.*;

/**
 * Lightweight server that runs inside the Master process and listens on a
 * separate port for REDUCE_RESULT messages sent back by the Reducer.
 *
 * When a result arrives, it is stored in PendingResultsMap and the waiting
 * Master client handler thread is woken via notifyAll().
 *
 * Message format (from Reducer):
 *   REDUCE_RESULT|mapId|type|data
 */
public class ReducerCallbackServer implements Runnable {

    private final int              callbackPort;
    private final PendingResultsMap pendingResults;

    public ReducerCallbackServer(int callbackPort, PendingResultsMap pendingResults) {
        this.callbackPort   = callbackPort;
        this.pendingResults = pendingResults;
    }

    @Override
    public void run() {
        try {
            ServerSocket server = new ServerSocket(callbackPort);
            System.out.println("[Master-Callback] Reducer callback server on port " + callbackPort);

            while (true) {
                Socket reducerConn = server.accept();
                // Each callback is very short; handle inline in a lightweight thread
                Thread t = new Thread(() -> handleCallback(reducerConn));
                t.setDaemon(true);
                t.start();
            }
        } catch (IOException e) {
            System.err.println("[Master-Callback] Fatal: " + e.getMessage());
        }
    }

    private void handleCallback(Socket socket) {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()))) {

            String line = in.readLine();
            if (line == null) return;

            // REDUCE_RESULT|mapId|type|data
            String[] parts = line.split("\\|", 4);
            if (!"REDUCE_RESULT".equals(parts[0]) || parts.length < 3) {
                System.err.println("[Master-Callback] Unexpected message: " + line);
                return;
            }
            String mapId = parts[1];
            // parts[2] = type (for logging)
            String data  = parts.length > 3 ? parts[3] : "";

            System.out.println("[Master-Callback] Received result for mapId " + mapId);
            // Wake the waiting Master client handler
            pendingResults.setResult(mapId, data);

        } catch (IOException e) {
            System.err.println("[Master-Callback] Handler error: " + e.getMessage());
        }
    }
}
