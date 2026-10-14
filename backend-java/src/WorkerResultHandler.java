import java.io.*;
import java.net.Socket;

/**
 * Handles a single TCP connection to the Reducer.
 * Two types of senders connect:
 *
 *   1. Master → REGISTER_JOB|mapId|type|totalWorkers|masterCallbackHost|masterCallbackPort
 *      Registers a new reduce job so the Reducer knows how many results to wait for.
 *
 *   2. Worker → MAP_RESULT|mapId|type|data
 *      Delivers one worker's map output. When all workers have reported,
 *      the Reducer executes the reduce phase and sends the final result back to Master.
 */
public class WorkerResultHandler implements Runnable {

    private final Socket  socket;
    private final Reducer reducer;

    public WorkerResultHandler(Socket socket, Reducer reducer) {
        this.socket  = socket;
        this.reducer = reducer;
    }

    @Override
    public void run() {
        try (BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter    out = new PrintWriter(socket.getOutputStream(), true)) {

            String line = in.readLine();
            if (line == null) return;

            // Split into at most 5 parts (data field may contain pipes in theory)
            String[] parts = line.split("\\|", 5);
            String   cmd   = parts[0];

            switch (cmd) {

                case "REGISTER_JOB": {
                    // REGISTER_JOB|mapId|type|totalWorkers|masterCallbackHost|masterCallbackPort
                    String mapId        = parts[1];
                    String type         = parts[2];
                    int    totalWorkers = Integer.parseInt(parts[3]);
                    String masterHost   = parts[4];
                    // masterCallbackPort is embedded in parts[4] as "host|port" when split with limit 5
                    // Reparse: split the original line without limit for these last fields
                    String[] full       = line.split("\\|");
                    String   cbHost     = full[4];
                    int      cbPort     = Integer.parseInt(full[5]);

                    ReduceJobManager.ReduceJob job = new ReduceJobManager.ReduceJob(
                            type, totalWorkers, cbHost, cbPort);
                    reducer.getJobManager().register(mapId, job);
                    System.out.println("[Reducer] Registered job " + mapId + " type=" + type
                            + " workers=" + totalWorkers);
                    out.println("OK");
                    break;
                }

                case "MAP_RESULT": {
                    // MAP_RESULT|mapId|type|data
                    // data may be empty (worker has no matching games)
                    String mapId = parts[1];
                    // parts[2] = type (already stored in job; used only for logging)
                    String data  = parts.length > 3 ? parts[3] : "";

                    ReduceJobManager.ReduceJob completedJob =
                            reducer.getJobManager().addResult(mapId, data);

                    if (completedJob != null) {
                        // All workers have reported → trigger reduce + send to Master
                        final ReduceJobManager.ReduceJob job = completedJob;
                        final String                     id  = mapId;
                        Thread t = new Thread(() -> reducer.reduceAndSend(job, id));
                        t.setDaemon(true);
                        t.start();
                    }
                    out.println("OK");
                    break;
                }

                default:
                    System.err.println("[Reducer] Unknown command: " + cmd);
                    out.println("ERROR|Unknown command");
            }

        } catch (IOException e) {
            System.err.println("[Reducer-Handler] Error: " + e.getMessage());
        }
    }
}
