package com.gaming.platform;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

/**
 * PlayActivity — place a bet on a specific game.
 *
 * Received via Intent extras: gameName, minBet, maxBet, riskLevel, jackpot.
 * Sends: PLAY|playerId|gameName|betAmount
 * Receives: PLAY_RESULT|payout|playerProfit|resultType|Balance:X
 */
public class PlayActivity extends AppCompatActivity {

    private String gameName;
    private double minBet, maxBet;

    private TextView    tvGameTitle, tvGameInfo, tvBalance;
    private EditText    etBetAmount;
    private Button      btnPlay;
    private ProgressBar progressBar;
    private CardView    cardResult;
    private TextView    tvResultTitle, tvResultDetails, tvNewBalance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);

        // Receive game info from SearchActivity
        gameName = getIntent().getStringExtra("gameName");
        minBet   = getIntent().getDoubleExtra("minBet", 0.1);
        maxBet   = getIntent().getDoubleExtra("maxBet", 100.0);
        String risk    = getIntent().getStringExtra("riskLevel");
        int    jackpot = getIntent().getIntExtra("jackpot", 10);

        tvGameTitle   = findViewById(R.id.tvGameTitle);
        tvGameInfo    = findViewById(R.id.tvGameInfo);
        tvBalance     = findViewById(R.id.tvBalance);
        etBetAmount   = findViewById(R.id.etBetAmount);
        btnPlay       = findViewById(R.id.btnPlay);
        progressBar   = findViewById(R.id.progressBar);
        cardResult    = findViewById(R.id.cardResult);
        tvResultTitle = findViewById(R.id.tvResultTitle);
        tvResultDetails = findViewById(R.id.tvResultDetails);
        tvNewBalance  = findViewById(R.id.tvNewBalance);

        tvGameTitle.setText(gameName);
        tvGameInfo.setText(String.format(
            "Risk: %s  |  Jackpot: %dx\nΌρια ποντaρίσματος: %.2f – %.2f FUN",
            risk, jackpot, minBet, maxBet));

        tvBalance.setText(String.format("Υπόλοιπό σου: %.2f FUN",
            SessionManager.get().getBalance()));

        etBetAmount.setHint(String.format("%.2f – %.2f FUN", minBet, maxBet));

        cardResult.setVisibility(View.GONE);

        btnPlay.setOnClickListener(v -> placeBet());
    }

    private void placeBet() {
        String betStr = etBetAmount.getText().toString().trim();
        if (betStr.isEmpty()) {
            etBetAmount.setError("Εισάγετε ποσό");
            return;
        }

        double bet;
        try {
            bet = Double.parseDouble(betStr);
        } catch (NumberFormatException e) {
            etBetAmount.setError("Μη έγκυρο ποσό");
            return;
        }

        if (bet < minBet) {
            etBetAmount.setError(String.format("Ελάχιστο πονtάρισμα: %.2f FUN", minBet));
            return;
        }
        if (bet > maxBet) {
            etBetAmount.setError(String.format("Μέγιστο πονtάρισμα: %.2f FUN", maxBet));
            return;
        }
        if (bet > SessionManager.get().getBalance()) {
            etBetAmount.setError("Ανεπαρκές υπόλοιπο");
            return;
        }

        setLoading(true);
        cardResult.setVisibility(View.GONE);

        String pid  = SessionManager.get().getPlayerId();
        String cmd  = "PLAY|" + pid + "|" + gameName + "|" + bet;
        final double finalBet = bet;

        new Thread(() -> {
            String response = NetworkClient.get().send(cmd);
            runOnUiThread(() -> {
                setLoading(false);
                handlePlayResult(response, finalBet);
            });
        }).start();
    }

    private void handlePlayResult(String response, double bet) {
        cardResult.setVisibility(View.VISIBLE);

        if (response == null || response.startsWith("ERROR")) {
            tvResultTitle.setText("Σφάλμα");
            tvResultTitle.setTextColor(getColor(R.color.colorError));
            tvResultDetails.setText(response != null ? response.replace("ERROR|", "") : "Δεν απάντησε ο server");
            tvNewBalance.setText("");
            return;
        }

        // PLAY_RESULT|payout|playerProfit|resultType|Balance:X
        String[] parts = response.split("\\|");
        if (parts.length < 4) {
            tvResultTitle.setText("Απρόσμενη απάντηση");
            tvResultDetails.setText(response);
            return;
        }

        double payout       = Double.parseDouble(parts[1]);
        double playerProfit = Double.parseDouble(parts[2]);
        String resultType   = parts[3];

        // Update session balance
        if (parts.length > 4) {
            SessionManager.get().parseAndSetBalance(parts[4]);
        }

        // Display result
        switch (resultType) {
            case "JACKPOT":
                tvResultTitle.setText("🎰 JACKPOT! 🎰");
                tvResultTitle.setTextColor(getColor(R.color.colorJackpot));
                break;
            case "WIN":
                tvResultTitle.setText("🎉 Κέρδισες!");
                tvResultTitle.setTextColor(getColor(R.color.colorWin));
                break;
            case "PARTIAL":
                tvResultTitle.setText("↩ Μερική επιστροφή");
                tvResultTitle.setTextColor(getColor(R.color.colorPartial));
                break;
            default:
                tvResultTitle.setText("😔 Δεν κέρδισες αυτή τη φορά");
                tvResultTitle.setTextColor(getColor(R.color.colorLoss));
        }

        String profitSign = playerProfit >= 0 ? "+" : "";
        tvResultDetails.setText(String.format(
            "Ποντάρισμα:  %.2f FUN\nΑποδοχή:     %.2f FUN\nΚέρδος/ζημία: %s%.2f FUN",
            bet, payout, profitSign, playerProfit));

        tvNewBalance.setText(String.format("Νέο υπόλοιπο: %.2f FUN",
            SessionManager.get().getBalance()));

        // Also update the balance label at the top
        tvBalance.setText(String.format("Υπόλοιπό σου: %.2f FUN",
            SessionManager.get().getBalance()));
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnPlay.setEnabled(!loading);
    }
}
