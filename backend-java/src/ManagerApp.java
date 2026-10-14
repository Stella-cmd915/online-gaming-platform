import java.io.*;
import java.net.Socket;
import java.util.Scanner;

/**
 * Manager Console Application.
 *
 * Connects to the Master via TCP and provides a menu-driven interface for:
 *   1. Add a game (from JSON file path or manual entry)
 *   2. Remove a game
 *   3. Update game risk level
 *   4. Show profits/losses per Provider (MapReduce)
 *   5. Show profits/losses per Player   (MapReduce)
 *
 * Communication:
 *   All commands are sent as pipe-delimited text over a persistent TCP socket.
 *   The connection stays open for the session duration.
 *
 * Usage:
 *   java ManagerApp
 */
public class ManagerApp {

    private static PrintWriter    out;
    private static BufferedReader in;

    public static void main(String[] args) throws Exception {
        java.util.Properties cfg = ConfigLoader.load();
        String masterHost = cfg.getProperty("master.host", "localhost");
        int    masterPort = Integer.parseInt(cfg.getProperty("master.port", "5000"));

        System.out.println("=================================================");
        System.out.println("   Online Gaming Platform — Manager Console");
        System.out.println("=================================================");
        System.out.println("Connecting to Master at " + masterHost + ":" + masterPort + " ...");

        try (Socket socket = new Socket(masterHost, masterPort)) {
            out = new PrintWriter(socket.getOutputStream(), true);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            System.out.println("Connected.\n");
            Scanner scanner = new Scanner(System.in);
            runMenu(scanner);
        } catch (IOException e) {
            System.err.println("Cannot connect to Master: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------
    // Main menu loop
    // -----------------------------------------------------------
    private static void runMenu(Scanner sc) throws IOException {
        while (true) {
            System.out.println("\n--- Manager Menu ---");
            System.out.println("1. Add game (from JSON file)");
            System.out.println("2. Add game (manual entry)");
            System.out.println("3. Remove game");
            System.out.println("4. Update game risk level");
            System.out.println("5. Show profits/losses per Provider");
            System.out.println("6. Show profits/losses per Player");
            System.out.println("0. Exit");
            System.out.print("Choice: ");

            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1": addGameFromFile(sc); break;
                case "2": addGameManual(sc);   break;
                case "3": removeGame(sc);      break;
                case "4": updateRisk(sc);      break;
                case "5": providerStats(sc);   break;
                case "6": playerStats(sc);     break;
                case "0":
                    System.out.println("Goodbye.");
                    return;
                default:
                    System.out.println("Invalid choice.");
            }
        }
    }

    // -----------------------------------------------------------
    // 1. Add game from JSON file
    // -----------------------------------------------------------
    private static void addGameFromFile(Scanner sc) throws IOException {
        System.out.print("Enter path to game JSON file: ");
        String path = sc.nextLine().trim();
        File f = new File(path);
        if (!f.exists()) {
            System.out.println("File not found: " + path);
            return;
        }

        // Read entire file
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }

        String json = sb.toString();
        String[] fields = parseGameJson(json);
        if (fields == null) {
            System.out.println("Failed to parse JSON.");
            return;
        }

        sendAddGame(fields);
    }

    // -----------------------------------------------------------
    // 2. Add game with manual field entry
    // -----------------------------------------------------------
    private static void addGameManual(Scanner sc) throws IOException {
        System.out.print("Game name:      "); String name     = sc.nextLine().trim();
        System.out.print("Provider name:  "); String provider = sc.nextLine().trim();
        System.out.print("Stars (1-5):    "); String stars    = sc.nextLine().trim();
        System.out.print("No. of votes:   "); String votes    = sc.nextLine().trim();
        System.out.print("Logo path:      "); String logo     = sc.nextLine().trim();
        System.out.print("Min bet:        "); String minBet   = sc.nextLine().trim();
        System.out.print("Max bet:        "); String maxBet   = sc.nextLine().trim();
        System.out.print("Risk (low/medium/high): "); String risk = sc.nextLine().trim();
        System.out.print("Hash key:       "); String hashKey  = sc.nextLine().trim();

        sendAddGame(new String[]{name, provider, stars, votes, logo, minBet, maxBet, risk, hashKey});
    }

    // Send ADD_GAME command and display response
    private static void sendAddGame(String[] f) throws IOException {
        // Compute and display the auto-computed fields before sending
        double minBet = Double.parseDouble(f[4]);
        String betCat = Game.computeBetCategory(minBet);
        int    jackpot = Game.computeJackpot(f[7]);
        System.out.println("\n[Auto-computed] BetCategory: " + betCat + " | Jackpot: " + jackpot + "x");

        // ADD_GAME|name|provider|stars|votes|logo|minBet|maxBet|risk|hashKey
        String cmd = "ADD_GAME|" + f[0] + "|" + f[1] + "|" + f[2] + "|" + f[3] + "|"
                     + f[4] + "|" + f[5] + "|" + f[6] + "|" + f[7] + "|" + f[8];
        out.println(cmd);
        System.out.println("Server: " + in.readLine());
    }

    // -----------------------------------------------------------
    // 3. Remove game
    // -----------------------------------------------------------
    private static void removeGame(Scanner sc) throws IOException {
        System.out.print("Game name to remove: ");
        String name = sc.nextLine().trim();
        out.println("REMOVE_GAME|" + name);
        System.out.println("Server: " + in.readLine());
    }

    // -----------------------------------------------------------
    // 4. Update risk level
    // -----------------------------------------------------------
    private static void updateRisk(Scanner sc) throws IOException {
        System.out.print("Game name:         ");
        String name = sc.nextLine().trim();
        System.out.print("New risk (low/medium/high): ");
        String risk = sc.nextLine().trim();
        out.println("UPDATE_RISK|" + name + "|" + risk);
        System.out.println("Server: " + in.readLine());
    }

    // -----------------------------------------------------------
    // 5. Provider stats (MapReduce)
    // -----------------------------------------------------------
    private static void providerStats(Scanner sc) throws IOException {
        System.out.print("Provider name: ");
        String provider = sc.nextLine().trim();
        System.out.println("Running MapReduce query... (may take a moment)");

        out.println("GET_PROVIDER_STATS|" + provider);
        String resp = in.readLine();

        // STATS_RESULT|game1=profit1;game2=profit2;Total=X
        displayStatsResult("Provider: " + provider, resp);
    }

    // -----------------------------------------------------------
    // 6. Player stats (MapReduce)
    // -----------------------------------------------------------
    private static void playerStats(Scanner sc) throws IOException {
        System.out.print("Player ID: ");
        String pid = sc.nextLine().trim();
        System.out.println("Running MapReduce query... (may take a moment)");

        out.println("GET_PLAYER_STATS|" + pid);
        String resp = in.readLine();

        // STATS_RESULT|totalProfit=X
        displayStatsResult("Player: " + pid, resp);
    }

    // -----------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------

    private static void displayStatsResult(String header, String resp) {
        System.out.println("\n--- Stats for " + header + " ---");
        if (resp == null || !resp.startsWith("STATS_RESULT|")) {
            System.out.println("Error or timeout: " + resp);
            return;
        }
        String data = resp.substring("STATS_RESULT|".length());
        if (data.isEmpty()) {
            System.out.println("No data found.");
            return;
        }
        for (String item : data.split(";")) {
            String[] kv = item.split("=");
            if (kv.length >= 2) {
                double val = Double.parseDouble(kv[1]);
                String sign = val >= 0 ? "+" : "";
                System.out.printf("  %-25s %s%.2f FUN%n", kv[0], sign, val);
            }
        }
    }

    // -----------------------------------------------------------
    // Minimal JSON parser for the game registration format.
    // No external library — parses the specific JSON structure described
    // in the specification (GameName, ProviderName, Stars, etc.).
    // Returns [name, provider, stars, votes, logo, minBet, maxBet, risk, hashKey]
    // -----------------------------------------------------------
    private static String[] parseGameJson(String json) {
        try {
            String name     = extractString(json, "GameName");
            String provider = extractString(json, "ProviderName");
            String stars    = extractNumber(json, "Stars");
            String votes    = extractNumber(json, "NoOfVotes");
            String logo     = extractString(json, "GameLogo");
            String minBet   = extractNumber(json, "MinBet");
            String maxBet   = extractNumber(json, "MaxBet");
            String risk     = extractString(json, "RiskLevel");
            String hashKey  = extractString(json, "HashKey");

            // Validate required fields
            if (name.isEmpty() || provider.isEmpty() || stars.isEmpty()) {
                System.out.println("Missing required JSON fields.");
                return null;
            }
            return new String[]{name, provider, stars, votes, logo, minBet, maxBet, risk, hashKey};
        } catch (Exception e) {
            System.err.println("JSON parse error: " + e.getMessage());
            return null;
        }
    }

    /** Extract a string value from JSON: "key": "value" */
    private static String extractString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return "";
        int colon = json.indexOf(':', idx + pattern.length());
        int start = json.indexOf('"', colon + 1) + 1;
        int end   = json.indexOf('"', start);
        return start > 0 && end > start ? json.substring(start, end) : "";
    }

    /** Extract a numeric value from JSON: "key": 42 or "key": 3.14 */
    private static String extractNumber(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return "0";
        int colon = json.indexOf(':', idx + pattern.length());
        int start = colon + 1;
        while (start < json.length() && !Character.isDigit(json.charAt(start))
               && json.charAt(start) != '-') start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end))
               || json.charAt(end) == '.' || json.charAt(end) == '-')) end++;
        return start < end ? json.substring(start, end) : "0";
    }
}
