package com.gaming.platform;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/**
 * LoginActivity — entry screen.
 *
 * The player enters a username (playerID).
 * REGISTER_PLAYER is sent; if the player already exists, it acts as login
 * and returns the current balance.
 *
 * On success → MainActivity.
 */
public class LoginActivity extends AppCompatActivity {

    private EditText    etPlayerId;
    private EditText    etServerHost;
    private Button      btnLogin;
    private ProgressBar progressBar;
    private TextView    tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etPlayerId   = findViewById(R.id.etPlayerId);
        etServerHost = findViewById(R.id.etServerHost);
        btnLogin     = findViewById(R.id.btnLogin);
        progressBar  = findViewById(R.id.progressBar);
        tvStatus     = findViewById(R.id.tvStatus);

        // Pre-fill default server host
        etServerHost.setText(NetworkClient.MASTER_HOST);

        btnLogin.setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
        String pid  = etPlayerId.getText().toString().trim();
        String host = etServerHost.getText().toString().trim();

        if (pid.isEmpty()) {
            etPlayerId.setError("Εισάγετε Player ID");
            return;
        }

        // Update host if changed
        NetworkClient.MASTER_HOST = host.isEmpty() ? "10.0.2.2" : host;

        setLoading(true);
        tvStatus.setText("Σύνδεση στον server...");

        new Thread(() -> {
            // Reconnect with potentially new host
            NetworkClient.get().close();
            String response = NetworkClient.get().send("REGISTER_PLAYER|" + pid);

            runOnUiThread(() -> {
                setLoading(false);
                if (response == null || response.startsWith("ERROR")) {
                    tvStatus.setText("Σφάλμα: " + (response != null ? response : "Δεν απάντησε ο server"));
                    Toast.makeText(this, "Αδυναμία σύνδεσης", Toast.LENGTH_LONG).show();
                } else {
                    // OK|Registered. Starting balance: 100.0 FUN
                    // OK|Welcome back pid. Balance: 95.5000 FUN
                    SessionManager.get().setPlayerId(pid);
                    SessionManager.get().parseAndSetBalance(response);

                    tvStatus.setText(response.replace("OK|", ""));
                    Toast.makeText(this, "Καλώς ήρθες, " + pid + "!", Toast.LENGTH_SHORT).show();

                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                }
            });
        }).start();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
    }
}
