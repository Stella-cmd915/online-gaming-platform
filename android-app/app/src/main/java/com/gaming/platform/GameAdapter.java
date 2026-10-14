package com.gaming.platform;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

/**
 * RecyclerView adapter for the game search results list.
 * Each card shows game info and has Play + Rate buttons.
 */
public class GameAdapter extends RecyclerView.Adapter<GameAdapter.ViewHolder> {

    public interface OnPlayClick { void onClick(GameModel game); }
    public interface OnRateClick { void onClick(GameModel game); }

    private final List<GameModel> games;
    private final OnPlayClick     onPlay;
    private final OnRateClick     onRate;

    public GameAdapter(List<GameModel> games, OnPlayClick onPlay, OnRateClick onRate) {
        this.games  = games;
        this.onPlay = onPlay;
        this.onRate = onRate;
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                               .inflate(R.layout.item_game, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        GameModel g = games.get(position);

        h.tvGameName.setText(g.name);
        h.tvProvider.setText(g.provider);
        h.tvStars.setText(g.starsDisplay());
        h.tvDetails.setText(String.format(
            "Bet: %s  |  Risk: %s  |  Jackpot: %dx\n%.2f – %.2f FUN",
            g.betCategory, g.riskLevel, g.jackpot, g.minBet, g.maxBet));

        h.btnPlay.setOnClickListener(v -> onPlay.onClick(g));
        h.btnRate.setOnClickListener(v -> onRate.onClick(g));
    }

    @Override public int getItemCount() { return games.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvGameName, tvProvider, tvStars, tvDetails;
        Button   btnPlay, btnRate;

        ViewHolder(View itemView) {
            super(itemView);
            tvGameName = itemView.findViewById(R.id.tvGameName);
            tvProvider = itemView.findViewById(R.id.tvProvider);
            tvStars    = itemView.findViewById(R.id.tvStars);
            tvDetails  = itemView.findViewById(R.id.tvDetails);
            btnPlay    = itemView.findViewById(R.id.btnPlay);
            btnRate    = itemView.findViewById(R.id.btnRate);
        }
    }
}
