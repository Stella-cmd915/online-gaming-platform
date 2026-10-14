package com.gaming.platform;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

/**
 * StatsActivity — shows the player's total profit/loss via MapReduce query.
 *
 * Sends: GET_PLAYER_STATS|playerId
 * Receives: STATS_RESULT|totalProfit=X
 */
public class StatsActivity extends AppCompatActivity {

    private TextView    tvResult;
    private ProgressBar progressBar;
    private Button      btnRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        tvResult    = findViewById(R.id.tvResult);
        progressBar = findViewById(R.id.progressBar);
        btnRefresh  = findViewById(R.id.btnRefresh);

        btnRefresh.setOnClickListener(v -> loadStats());
        loadStats();
    }

    private void loadStats() {
        setLoading(true);
        tvResult.setText("Φόρτωση...");

        String pid = SessionManager.get().getPlayerId();

        new Thread(() -> {
            String response = NetworkClient.get().send("GET_PLAYER_STATS|" + pid);
            runOnUiThread(() -> {
                setLoading(false);
                displayStats(response);
            });
        }).start();
    }

    private void displayStats(String response) {
        if (response == null || response.startsWith("ERROR")) {
            tvResult.setText("Σφάλμα σύνδεσης:\n" + response);
            return;
        }

        // STATS_RESULT|totalProfit=X
        if (!response.startsWith("STATS_RESULT|")) {
            tvResult.setText("Απρόσμενη απάντηση:\n" + response);
            return;
        }

        String data = response.substring("STATS_RESULT|".length());
        if (data.isEmpty()) {
            tvResult.setText("Δεν βρέθηκαν στατιστικά.\n\nΔεν έχεις κάνει κάποιο ποντάρισμα ακόμα.");
            return;
        }

        // Parse totalProfit=X
        StringBuilder sb = new StringBuilder();
        for (String item : data.split(";")) {
            String[] kv = item.split("=");
            if (kv.length >= 2) {
                double val = 0;
                try { val = Double.parseDouble(kv[1]); } catch (Exception ignored) {}
                String sign = val >= 0 ? "+" : "";
                if ("totalProfit".equals(kv[0])) {
                    sb.append("Συνολικό Κέρδος/Ζημία:\n");
                    sb.append(sign).append(String.format("%.2f FUN", val));
                    sb.append("\n\n");
                    if (val > 0) {
                        sb.append("🎉 Είσαι σε κέρδος! Συνέχισε έτσι!");
                    } else if (val < 0) {
                        sb.append("📉 Είσαι σε ζημία. Καλή τύχη!");
                    } else {
                        sb.append("⚖️ Break even — ούτε κέρδος ούτε ζημία.");
                    }
                } else {
                    sb.append(kv[0]).append(": ").append(sign)
                      .append(String.format("%.2f FUN", val)).append("\n");
                }
            }
        }

        tvResult.setText(sb.toString());
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRefresh.setEnabled(!loading);
    }
}
