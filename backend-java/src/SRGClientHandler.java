import java.io.*;
import java.net.Socket;

/**
 * Handles a single TCP connection from a Worker to the Secure Random Generator.
 *
 * Supported commands (pipe-delimited):
 *   REGISTER_GAME|gameId|secret
 *       → registers a game and starts its producer thread.
 *       → responds: OK
 *
 *   GET_NUMBER|gameId
 *       → consumes one random integer from the game's buffer (blocks if empty).
 *       → computes sha256(number + secret) for integrity verification.
 *       → responds: NUMBER|<int>|<sha256hex>
 *
 * The connection remains open for the duration of the interaction.
 */
public class SRGClientHandler implements Runnable {

    private final Socket    socket;
    private final SRGServer server;

    public SRGClientHandler(Socket socket, SRGServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try (BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter    out = new PrintWriter(socket.getOutputStream(), true)) {

            String line;
            while ((line = in.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length < 1) continue;

                switch (parts[0]) {

                    case "REGISTER_GAME": {
                        // REGISTER_GAME|gameId|secret
                        if (parts.length < 3) { out.println("ERROR|Bad REGISTER_GAME"); break; }
                        String gameId = parts[1];
                        String secret = parts[2];
                        server.getOrCreateBuffer(gameId, secret);
                        out.println("OK");
                        break;
                    }

                    case "GET_NUMBER": {
                        // GET_NUMBER|gameId
                        if (parts.length < 2) { out.println("ERROR|Bad GET_NUMBER"); break; }
                        String gameId = parts[1];
                        GameBuffer gb = server.getBuffer(gameId);
                        if (gb == null) {
                            out.println("ERROR|Game not registered: " + gameId);
                        } else {
                            // Consume a pre-generated random number from the buffer
                            int number = gb.consume();
                            // Compute integrity hash: sha256(number + secret)
                            String hash = SRGServer.sha256(number + gb.getSecret());
                            out.println("NUMBER|" + number + "|" + hash);
                        }
                        break;
                    }

                    default:
                        out.println("ERROR|Unknown SRG command: " + parts[0]);
                }
            }

        } catch (Exception e) {
            System.err.println("[SRG-Handler] Error: " + e.getMessage());
        }
    }
}
