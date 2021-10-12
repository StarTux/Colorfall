package io.github.feydk.colorfall;

import io.github.feydk.colorfall.util.Msg;;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;
import org.bukkit.scoreboard.Team;

public final class GameScoreboard {
    private Objective objective = null;
    private Scoreboard board = null;
    private Team team = null;

    private String title;

    public GameScoreboard() {
        init();
    }

    // Create scoreboard and objective. Set scoreboard to display in sidebar.
    public void init() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        board = manager.getNewScoreboard();
        if (board.getObjective("Timer") == null) {
            objective = board.registerNewObjective("Timer", "timer");
        } else {
            objective = board.getObjective("Timer");
        }
        team = board.registerNewTeam("All");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    public void setCollision(boolean collision) {
        if (collision) {
            team.setOption(Option.COLLISION_RULE, OptionStatus.ALWAYS);
        } else {
            team.setOption(Option.COLLISION_RULE, OptionStatus.NEVER);
        }
    }

    // Add a player to the scoreboard.
    public void addPlayer(Player player) {
        player.setScoreboard(board);
        team.addPlayer(player);
    }

    // Refresh the scoreboard title.
    public void refreshTitle() {
        objective.setDisplayName(title);
    }

    // Refresh the scoreboard title with a timer (which is an amount of seconds).
    public void refreshTitle(long ticks) {
        objective.setDisplayName(title + " " + getFormattedTime(ticks));
    }

    // When a player is eliminated, make his name dark red in the scoreboard.
    public void setPlayerEliminated(Player player) {
        board.resetScores(player);
        objective.getScore(Msg.format("&4%s", player.getName())).setScore(0);
    }

    public void removePlayer(Player player) {
        board.resetScores(player);
    }

    public void updatePlayers() {
        for (String entry : board.getEntries()) {
            if (Bukkit.getPlayerExact(entry) == null) {
                board.resetScores(entry);
            }
        }
    }

    // Format seconds into mm:ss.
    private String getFormattedTime(long ticks) {
        long timer = ticks / 20;
        long minutes = timer / 60;
        long seconds = timer - (minutes * 60);
        return ChatColor.WHITE + String.format("%02d", minutes) + ChatColor.GRAY + ":" + ChatColor.WHITE + String.format("%02d", seconds);
    }

    // Set the title of the scoreboard.
    public void setTitle(String title) {
        this.title = title;
        refreshTitle();
    }

    // Set player score.
    public void setPlayerScore(Player player, int score) {
        objective.getScore(player).setScore(score);
    }
}
