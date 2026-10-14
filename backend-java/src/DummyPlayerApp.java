import java.io.*;
import java.net.Socket;
import java.util.Scanner;

/**
 * Dummy Player Application (Παραδοτέο Α).
 *
 * Προσομοιώνει την Android app του Παραδοτέου Β μέσω console.
 * Συνδέεται στον Master και επιτρέπει στον παίκτη να:
 *   - Εγγραφεί / συνδεθεί (REGISTER_PLAYER)
 *   - Προσθέσει FUN tokens (ADD_BALANCE)
 *   - Αναζητήσει παιχνίδια με φίλτρα (SEARCH)
 *   - Παίξει / ποντάρει (PLAY)
 *   - Βαθμολογήσει παιχνίδι (RATE_GAME)
 *
 * Χρήση:
 *   java DummyPlayerApp
 */
public class DummyPlayerApp {

    private static PrintWriter    out;
    private static BufferedReader in;
    private static String         currentPlayerId = null;

    public static void main(String[] args) throws Exception {
        java.util.Properties cfg = ConfigLoader.load();
        String masterHost = cfg.getProperty("master.host", "localhost");
        int    masterPort = Integer.parseInt(cfg.getProperty("master.port", "5000"));

        System.out.println("=================================================");
        System.out.println("   Online Gaming Platform — Player Client");
        System.out.println("=================================================");
        System.out.println("Connecting to " + masterHost + ":" + masterPort + " ...");

        try (Socket socket = new Socket(masterHost, masterPort)) {
            out = new PrintWriter(socket.getOutputStream(), true);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println("Connected.\n");

            Scanner sc = new Scanner(System.in);
            // First: must register/login
            doRegister(sc);
            // Then: main game menu
            runMenu(sc);
        } catch (IOException e) {
            System.err.println("Cannot connect to Master: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------
    // Registration / login
    // -----------------------------------------------------------
    private static void doRegister(Scanner sc) throws IOException {
        System.out.print("Enter your Player ID (new or existing): ");
        currentPlayerId = sc.nextLine().trim();

        out.println("REGISTER_PLAYER|" + currentPlayerId);
        String resp = in.readLine();
        System.out.println(">> " + resp);
    }

    // -----------------------------------------------------------
    // Main menu
    // -----------------------------------------------------------
    private static void runMenu(Scanner sc) throws IOException {
        while (true) {
            System.out.println("\n--- Player Menu [" + currentPlayerId + "] ---");
            System.out.println("1. Search games");
            System.out.println("2. Play (place a bet)");
            System.out.println("3. Rate a game");
            System.out.println("4. Add balance (FUN tokens)");
            System.out.println("5. My stats (profit/loss)");
            System.out.println("0. Exit");
            System.out.print("Choice: ");

            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1": searchGames(sc); break;
                case "2": playGame(sc);    break;
                case "3": rateGame(sc);    break;
                case "4": addBalance(sc);  break;
                case "5": myStats();       break;
                case "0":
                    System.out.println("Good luck next time!");
                    return;
                default:
                    System.out.println("Invalid choice.");
            }
        }
    }

    // -----------------------------------------------------------
    // 1. Search games with optional filters
    // -----------------------------------------------------------
    private static void searchGames(Scanner sc) throws IOException {
        System.out.println("\n--- Search Games ---");
        System.out.println("(Press Enter to skip a filter)");

        System.out.print("Min stars (1-5):           ");
        String stars = sc.nextLine().trim();
        if (stars.isEmpty()) stars = "-";

        System.out.print("Risk level (low/medium/high): ");
        String risk = sc.nextLine().trim();
        if (risk.isEmpty()) risk = "-";

        System.out.print("Bet category ($, $$, $$$): ");
        String betCat = sc.nextLine().trim();
        if (betCat.isEmpty()) betCat = "-";

        System.out.println("\nSearching...");
        // SEARCH|playerId|minStars|riskLevel|betCategory
        out.println("SEARCH|" + currentPlayerId + "|" + stars + "|" + risk + "|" + betCat);

        String resp = in.readLine();
        // SEARCH_RESULT|game1;game2;...
        displaySearchResults(resp);
    }

    private static void displaySearchResults(String resp) {
        System.out.println("\n--- Search Results ---");
        if (resp == null || !resp.startsWith("SEARCH_RESULT|")) {
            System.out.println("Error: " + resp);
            return;
        }
        String data = resp.substring("SEARCH_RESULT|".length());
        if (data.isEmpty()) {
            System.out.println("No games found matching your criteria.");
            return;
        }
        String[] games = data.split(";");
        int idx = 1;
        for (String g : games) {
            // Format: name,provider,stars,betCategory,minBet,maxBet,riskLevel,jackpot
            String[] f = g.split(",");
            if (f.length < 8) continue;
            System.out.printf("  %2d. %-22s | %-14s | %-6s stars | Bet: %-4s | "
                    + "Risk: %-6s | %.2f-%.2f FUN | Jackpot: %sx%n",
                    idx++, f[0], f[1], f[2], f[3], f[6],
                    Double.parseDouble(f[4]), Double.parseDouble(f[5]), f[7]);
        }
        System.out.println("Total: " + games.length + " game(s) found.");
    }

    // -----------------------------------------------------------
    // 2. Play a game
    // -----------------------------------------------------------
    private static void playGame(Scanner sc) throws IOException {
        System.out.println("\n--- Place a Bet ---");
        System.out.print("Game name:   ");
        String gameName = sc.nextLine().trim();
        System.out.print("Bet amount (FUN): ");
        String betStr = sc.nextLine().trim();

        double bet;
        try {
            bet = Double.parseDouble(betStr);
        } catch (NumberFormatException e) {
            System.out.println("Invalid amount.");
            return;
        }

        System.out.println("Placing bet of " + bet + " FUN on \"" + gameName + "\"...");
        // PLAY|playerId|gameName|betAmount
        out.println("PLAY|" + currentPlayerId + "|" + gameName + "|" + bet);

        String resp = in.readLine();
        displayPlayResult(resp, bet);
    }

    private static void displayPlayResult(String resp, double bet) {
        System.out.println("\n--- Bet Result ---");
        if (resp == null) {
            System.out.println("No response from server.");
            return;
        }
        if (resp.startsWith("ERROR")) {
            System.out.println("Error: " + resp.substring(resp.indexOf('|') + 1));
            return;
        }
        // PLAY_RESULT|payout|playerProfit|resultType|Balance:X
        String[] parts = resp.split("\\|");
        if (parts.length < 4) {
            System.out.println(resp);
            return;
        }
        String resultType   = parts[3];
        double payout       = Double.parseDouble(parts[1]);
        double playerProfit = Double.parseDouble(parts[2]);
        String balanceStr   = parts.length > 4 ? parts[4] : "";

        // Display result banner
        if ("JACKPOT".equals(resultType)) {
            System.out.println("  *** J A C K P O T ! ***");
        } else if ("WIN".equals(resultType)) {
            System.out.println("  *** YOU WIN! ***");
        } else if ("PARTIAL".equals(resultType)) {
            System.out.println("  Partial return.");
        } else {
            System.out.println("  Better luck next time.");
        }

        System.out.printf("  Bet:        %.2f FUN%n", bet);
        System.out.printf("  Payout:     %.2f FUN%n", payout);
        if (playerProfit >= 0) {
            System.out.printf("  Profit:   + %.2f FUN%n", playerProfit);
        } else {
            System.out.printf("  Loss:     - %.2f FUN%n", -playerProfit);
        }
        if (!balanceStr.isEmpty()) {
            System.out.println("  New balance: " + balanceStr.replace("Balance:", "") + " FUN");
        }
    }

    // -----------------------------------------------------------
    // 3. Rate a game (1-5 stars)
    // -----------------------------------------------------------
    private static void rateGame(Scanner sc) throws IOException {
        System.out.println("\n--- Rate a Game ---");
        System.out.print("Game name:      ");
        String gameName = sc.nextLine().trim();
        System.out.print("Stars (1-5):    ");
        String starsStr = sc.nextLine().trim();

        int stars;
        try {
            stars = Integer.parseInt(starsStr);
            if (stars < 1 || stars > 5) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            System.out.println("Invalid rating. Must be 1-5.");
            return;
        }

        // RATE_GAME|playerId|gameName|stars
        out.println("RATE_GAME|" + currentPlayerId + "|" + gameName + "|" + stars);
        String resp = in.readLine();
        System.out.println("Server: " + resp);
    }

    // -----------------------------------------------------------
    // 4. Add balance (deposit FUN tokens)
    // -----------------------------------------------------------
    private static void addBalance(Scanner sc) throws IOException {
        System.out.print("Amount to add (FUN): ");
        String amountStr = sc.nextLine().trim();
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            System.out.println("Invalid amount.");
            return;
        }

        // ADD_BALANCE|playerId|amount
        out.println("ADD_BALANCE|" + currentPlayerId + "|" + amount);
        System.out.println("Server: " + in.readLine());
    }

    // -----------------------------------------------------------
    // 5. My stats (MapReduce query)
    // -----------------------------------------------------------
    private static void myStats() throws IOException {
        System.out.println("Fetching your stats across all games...");
        out.println("GET_PLAYER_STATS|" + currentPlayerId);
        String resp = in.readLine();
        // STATS_RESULT|totalProfit=X
        System.out.println("\n--- Stats for player: " + currentPlayerId + " ---");
        if (resp != null && resp.startsWith("STATS_RESULT|")) {
            String data = resp.substring("STATS_RESULT|".length());
            if (data.isEmpty()) {
                System.out.println("No bets placed yet.");
            } else {
                String[] kv = data.split("[=;]");
                for (int i = 0; i + 1 < kv.length; i += 2) {
                    double val = Double.parseDouble(kv[i + 1]);
                    System.out.printf("  %-20s %+.2f FUN%n", kv[i], val);
                }
            }
        } else {
            System.out.println("Error or timeout: " + resp);
        }
    }
}
