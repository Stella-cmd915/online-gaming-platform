package com.gaming.platform;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

/**
 * AddBalanceActivity — deposit FUN tokens to the player's account.
 *
 * Sends: ADD_BALANCE|playerId|amount
 * Receives: OK|New balance: X FUN
 */
public class AddBalanceActivity extends AppCompatActivity {

    private TextView    tvCurrentBalance;
    private EditText    etAmount;
    private Button      btnAdd;
    private ProgressBar progressBar;

    // Quick-add preset amounts
    private static final double[] PRESETS = {10, 25, 50, 100, 200};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_balance);

        tvCurrentBalance = findViewById(R.id.tvCurrentBalance);
        etAmount         = findViewById(R.id.etAmount);
        btnAdd           = findViewById(R.id.btnAdd);
        progressBar      = findViewById(R.id.progressBar);

        updateBalanceDisplay();

        // Preset buttons
        int[] presetIds = {R.id.btn10, R.id.btn25, R.id.btn50, R.id.btn100, R.id.btn200};
        for (int i = 0; i < presetIds.length; i++) {
            final double amount = PRESETS[i];
            Button btn = findViewById(presetIds[i]);
            if (btn != null) {
                btn.setText("+" + (int) amount);
                btn.setOnClickListener(v -> etAmount.setText(String.valueOf((int) amount)));
            }
        }

        btnAdd.setOnClickListener(v -> addBalance());
    }

    private void addBalance() {
        String amtStr = etAmount.getText().toString().trim();
        if (amtStr.isEmpty()) {
            etAmount.setError("Εισάγετε ποσό");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amtStr);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            etAmount.setError("Εισάγετε έγκυρο θετικό ποσό");
            return;
        }

        setLoading(true);

        String pid = SessionManager.get().getPlayerId();
        String cmd = "ADD_BALANCE|" + pid + "|" + amount;

        new Thread(() -> {
            String response = NetworkClient.get().send(cmd);
            runOnUiThread(() -> {
                setLoading(false);
                if (response != null && response.startsWith("OK")) {
                    SessionManager.get().parseAndSetBalance(response);
                    updateBalanceDisplay();
                    etAmount.setText("");
                    Toast.makeText(this,
                        String.format("Κατατέθηκαν %.2f FUN!", amount),
                        Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this,
                        "Σφάλμα: " + (response != null ? response : "Δεν απάντησε ο server"),
                        Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void updateBalanceDisplay() {
        tvCurrentBalance.setText(String.format(
            "Τρέχον υπόλοιπο: %.2f FUN", SessionManager.get().getBalance()));
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnAdd.setEnabled(!loading);
    }
}
