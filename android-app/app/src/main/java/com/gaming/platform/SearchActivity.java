package com.gaming.platform;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/**
 * SearchActivity — search for games with optional filters.
 *
 * Filters:
 *   - Min stars (Spinner: Any, 1-5)
 *   - Risk level (Spinner: Any, low, medium, high)
 *   - Bet category (Spinner: Any, $, $$, $$$)
 *
 * Sends: SEARCH|playerId|minStars|riskLevel|betCategory
 * Receives: SEARCH_RESULT|game1;game2;...
 *
 * Each result card has a "Παίξε" (Play) button and a "★ Αξιολόγηση" button.
 */
public class SearchActivity extends AppCompatActivity {

    private Spinner     spMinStars, spRisk, spBetCat;
    private Button      btnSearch;
    private ProgressBar progressBar;
    private TextView    tvEmpty;
    private RecyclerView recyclerView;
    private GameAdapter  adapter;
    private final List<GameModel> games = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        spMinStars   = findViewById(R.id.spMinStars);
        spRisk       = findViewById(R.id.spRisk);
        spBetCat     = findViewById(R.id.spBetCat);
        btnSearch    = findViewById(R.id.btnSearch);
        progressBar  = findViewById(R.id.progressBar);
        tvEmpty      = findViewById(R.id.tvEmpty);
        recyclerView = findViewById(R.id.recyclerView);

        setupSpinners();

        adapter = new GameAdapter(games, this::onPlayClicked, this::onRateClicked);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        btnSearch.setOnClickListener(v -> doSearch());

        // Auto-search on open (no filters)
        doSearch();
    }

    private void setupSpinners() {
        ArrayAdapter<String> starsAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,
            new String[]{"Οποιοδήποτε", "1+", "2+", "3+", "4+", "5"});
        starsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spMinStars.setAdapter(starsAdapter);

        ArrayAdapter<String> riskAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,
            new String[]{"Οποιοδήποτε", "low", "medium", "high"});
        riskAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spRisk.setAdapter(riskAdapter);

        ArrayAdapter<String> betAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,
            new String[]{"Οποιοδήποτε", "$", "$$", "$$$"});
        betAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBetCat.setAdapter(betAdapter);
    }

    private void doSearch() {
        // Build filter strings ("-" = no filter)
        int    starsIdx   = spMinStars.getSelectedItemPosition();
        String minStars   = starsIdx == 0 ? "-" : String.valueOf(starsIdx);

        int    riskIdx    = spRisk.getSelectedItemPosition();
        String[] riskOpts = {"", "low", "medium", "high"};
        String risk       = riskIdx == 0 ? "-" : riskOpts[riskIdx];

        int    betIdx     = spBetCat.getSelectedItemPosition();
        String[] betOpts  = {"", "$", "$$", "$$$"};
        String betCat     = betIdx == 0 ? "-" : betOpts[betIdx];

        String pid        = SessionManager.get().getPlayerId();
        String command    = "SEARCH|" + pid + "|" + minStars + "|" + risk + "|" + betCat;

        setLoading(true);
        tvEmpty.setVisibility(View.GONE);

        new Thread(() -> {
            String response = NetworkClient.get().send(command);
            runOnUiThread(() -> {
                setLoading(false);
                handleSearchResult(response);
            });
        }).start();
    }

    private void handleSearchResult(String response) {
        games.clear();

        if (response == null || response.startsWith("ERROR")) {
            tvEmpty.setText("Σφάλμα σύνδεσης: " + response);
            tvEmpty.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
            return;
        }

        // SEARCH_RESULT|game1;game2;...
        String data = response.startsWith("SEARCH_RESULT|")
                    ? response.substring("SEARCH_RESULT|".length())
                    : response;

        if (data.isEmpty()) {
            tvEmpty.setText("Δεν βρέθηκαν παιχνίδια με αυτά τα φίλτρα.");
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            for (String entry : data.split(";")) {
                GameModel g = GameModel.fromNetwork(entry);
                if (g != null) games.add(g);
            }
            if (games.isEmpty()) {
                tvEmpty.setText("Δεν βρέθηκαν παιχνίδια.");
                tvEmpty.setVisibility(View.VISIBLE);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void onPlayClicked(GameModel game) {
        Intent intent = new Intent(this, PlayActivity.class);
        intent.putExtra("gameName",  game.name);
        intent.putExtra("minBet",    game.minBet);
        intent.putExtra("maxBet",    game.maxBet);
        intent.putExtra("riskLevel", game.riskLevel);
        intent.putExtra("jackpot",   game.jackpot);
        startActivity(intent);
    }

    private void onRateClicked(GameModel game) {
        // Show a simple dialog to enter 1-5 stars
        String[] options = {"★ 1", "★★ 2", "★★★ 3", "★★★★ 4", "★★★★★ 5"};
        new android.app.AlertDialog.Builder(this)
            .setTitle("Αξιολόγηση: " + game.name)
            .setItems(options, (dialog, which) -> submitRating(game.name, which + 1))
            .show();
    }

    private void submitRating(String gameName, int stars) {
        String pid = SessionManager.get().getPlayerId();
        new Thread(() -> {
            String resp = NetworkClient.get().send("RATE_GAME|" + pid + "|" + gameName + "|" + stars);
            runOnUiThread(() -> {
                if ("OK".equals(resp)) {
                    Toast.makeText(this, "Βαθμολογία καταχωρήθηκε!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Σφάλμα: " + resp, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSearch.setEnabled(!loading);
    }
}
