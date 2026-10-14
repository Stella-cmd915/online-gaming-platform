package com.gaming.platform;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * MainActivity — main menu shown after login.
 *
 * Buttons:
 *   - Search games
 *   - Add balance
 *   - My stats
 *   - Logout
 */
public class MainActivity extends AppCompatActivity {

    private TextView tvWelcome;
    private TextView tvBalance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvWelcome = findViewById(R.id.tvWelcome);
        tvBalance = findViewById(R.id.tvBalance);

        Button btnSearch     = findViewById(R.id.btnSearch);
        Button btnAddBalance = findViewById(R.id.btnAddBalance);
        Button btnStats      = findViewById(R.id.btnStats);
        Button btnLogout     = findViewById(R.id.btnLogout);

        btnSearch.setOnClickListener(v ->
            startActivity(new Intent(this, SearchActivity.class)));

        btnAddBalance.setOnClickListener(v ->
            startActivity(new Intent(this, AddBalanceActivity.class)));

        btnStats.setOnClickListener(v ->
            startActivity(new Intent(this, StatsActivity.class)));

        btnLogout.setOnClickListener(v -> {
            SessionManager.get().clear();
            NetworkClient.get().close();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh balance display every time we return to the menu
        String pid = SessionManager.get().getPlayerId();
        tvWelcome.setText("Καλώς ήρθες,\n" + pid + "!");
        tvBalance.setText(String.format("Υπόλοιπο: %.2f FUN", SessionManager.get().getBalance()));
    }
}
