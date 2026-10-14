package com.gaming.platform;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Singleton TCP client that maintains a persistent connection to the Master server.
 *
 * ALL network operations MUST be called from a background thread
 * (never from the Android main/UI thread).
 *
 * Usage example (from an Activity):
 *   new Thread(() -> {
 *       String response = NetworkClient.get().send("SEARCH|player1|-|-|-");
 *       runOnUiThread(() -> { /* update UI *\/ });
 *   }).start();
 */
public class NetworkClient {

    // ── Server configuration ──────────────────────────────────────
    // Change MASTER_HOST to the IP of the machine running Master.java
    // Use 10.0.2.2 for Android Emulator connecting to host machine localhost
    public static String MASTER_HOST = "10.0.2.2";
    public static int    MASTER_PORT = 5000;
    // ──────────────────────────────────────────────────────────────

    private static NetworkClient instance;

    private Socket       socket;
    private PrintWriter  out;
    private BufferedReader in;

    private NetworkClient() {}

    public static synchronized NetworkClient get() {
        if (instance == null) instance = new NetworkClient();
        return instance;
    }

    // -----------------------------------------------------------
    // Connect to Master (call once after login)
    // -----------------------------------------------------------
    public synchronized void connect() throws IOException {
        if (isConnected()) return;
        socket = new Socket(MASTER_HOST, MASTER_PORT);
        out    = new PrintWriter(socket.getOutputStream(), true);
        in     = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    public synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    // -----------------------------------------------------------
    // Send a command and read one line response.
    // Reconnects automatically if connection was lost.
    // Returns the server response string, or "ERROR|..." on failure.
    // -----------------------------------------------------------
    public synchronized String send(String command) {
        try {
            if (!isConnected()) connect();
            out.println(command);
            return in.readLine();
        } catch (IOException e) {
            // Try to reconnect once
            try {
                close();
                connect();
                out.println(command);
                return in.readLine();
            } catch (IOException ex) {
                return "ERROR|Cannot connect to server: " + ex.getMessage();
            }
        }
    }

    // -----------------------------------------------------------
    // Disconnect (call on app exit or logout)
    // -----------------------------------------------------------
    public synchronized void close() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
        socket = null;
        out    = null;
        in     = null;
    }
}
