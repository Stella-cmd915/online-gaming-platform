import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Secure Random Generator (SRG) Server.
 *
 * Architecture:
 *  - Multithreaded TCP server; one thread per Worker connection.
 *  - Maintains a Map<gameId, GameBuffer> in memory.
 *  - When a game is registered, a dedicated ProducerThread is started that
 *    continuously fills that game's buffer using SecureRandom (CSPRNG).
 *  - Workers request numbers via TCP; this acts as the consumer side.
 *
 * Communication with Workers:
 *  - REGISTER_GAME|gameId|secret  → OK
 *  - GET_NUMBER|gameId             → NUMBER|<int>|<sha256(int+secret)>
 *
 * Usage:
 *   java SRGServer
 * (reads port and buffer size from config.properties)
 */
public class SRGServer {

    private final int port;
    private final int bufferSize;

    // Per-game circular buffers (gameId -> GameBuffer)
    // Access is synchronized via getOrCreateBuffer / getBuffer
    private final Map<String, GameBuffer> buffers = new HashMap<>();

    // -----------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------
    public static void main(String[] args) throws Exception {
        Properties cfg    = ConfigLoader.load();
        int port          = Integer.parseInt(cfg.getProperty("randomgenerator.port", "5005"));
        int bufferSize    = Integer.parseInt(cfg.getProperty("buffer.size",          "50"));
        new SRGServer(port, bufferSize).start();
    }

    public SRGServer(int port, int bufferSize) {
        this.port       = port;
        this.bufferSize = bufferSize;
    }

    // -----------------------------------------------------------
    // Main server loop
    // -----------------------------------------------------------
    public void start() throws IOException {
        ServerSocket server = new ServerSocket(port);
        System.out.println("[SRG] Secure Random Generator listening on port " + port);
        while (true) {
            Socket client = server.accept();
            // Each Worker connection is handled by a dedicated thread
            Thread t = new Thread(new SRGClientHandler(client, this));
            t.setDaemon(true);
            t.start();
        }
    }

    // -----------------------------------------------------------
    // Buffer management (synchronized for thread-safe access)
    // -----------------------------------------------------------

    /**
     * Returns the existing buffer for gameId, or creates one and starts a
     * producer thread if this is the first registration for that game.
     */
    public synchronized GameBuffer getOrCreateBuffer(String gameId, String secret) {
        if (!buffers.containsKey(gameId)) {
            GameBuffer gb = new GameBuffer(gameId, secret, bufferSize);
            buffers.put(gameId, gb);
            // Start the dedicated producer thread for this game
            Thread producer = new Thread(new ProducerThread(gb));
            producer.setDaemon(true);
            producer.setName("Producer-" + gameId);
            producer.start();
            System.out.println("[SRG] Registered game + started producer: " + gameId);
        }
        return buffers.get(gameId);
    }

    /** Returns the buffer for an already-registered game, or null. */
    public synchronized GameBuffer getBuffer(String gameId) {
        return buffers.get(gameId);
    }

    // -----------------------------------------------------------
    // Static SHA-256 utility (also called by Workers for verification)
    // -----------------------------------------------------------

    /**
     * Computes SHA-256 of the input string and returns it as a lowercase hex string.
     * Used to verify integrity of random numbers: hash = sha256(number + secret).
     */
    public static String sha256(String input) {
        try {
            MessageDigest md   = MessageDigest.getInstance("SHA-256");
            byte[]        hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex  = new StringBuilder(64);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
