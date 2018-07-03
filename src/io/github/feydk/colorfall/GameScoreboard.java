package io.github.feydk.colorfall;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;

public class GameScoreboard
{
    private Objective objective = null;
    private Scoreboard board = null;
    private Team team = null;

    private String title;

    public GameScoreboard()
    {
        init();
    }

    // Create scoreboard and objective. Set scoreboard to display in sidebar.
    public void init()
    {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        board = manager.getNewScoreboard();

        if(board.getObjective("Timer") == null)
            objective = board.registerNewObjective("Timer", "timer");
        else
            objective = board.getObjective("Timer");

        team = board.registerNewTeam("All");

        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    public void setCollision(boolean collision)
    {
        if(collision)
            team.setOption(Option.COLLISION_RULE, OptionStatus.ALWAYS);
        else
            team.setOption(Option.COLLISION_RULE, OptionStatus.NEVER);
    }

    // Add a player to the scoreboard.
    @SuppressWarnings("deprecation")
    public void addPlayer(Player player)
    {
        player.setScoreboard(board);
        team.addPlayer(player);
    }

    // Refresh the scoreboard title.
    public void refreshTitle()
    {
        String title = this.title;

        objective.setDisplayName(title);
    }

    // Refresh the scoreboard title with a timer (which is an amount of seconds).
    public void refreshTitle(long ticks)
    {
        String title = this.title;

        title += " " + getFormattedTime(ticks);

        objective.setDisplayName(title);
    }

    // When a player is eliminated, make his name dark red in the scoreboard.
    @SuppressWarnings("deprecation")
    public void setPlayerEliminated(Player player)
    {
        board.resetScores(player);
        objective.getScore(ColorfallGame.format("&4%s", player.getName())).setScore(0);
    }

    @SuppressWarnings("deprecation")
    public void removePlayer(Player player)
    {
        board.resetScores(player);
    }

    @SuppressWarnings("deprecation")
    public void updatePlayers()
    {
        for(OfflinePlayer player : board.getPlayers())
            {
                if(!player.isOnline())
                    board.resetScores(player);
            }
    }

    // Format seconds into mm:ss.
    private String getFormattedTime(long ticks)
    {
        long timer = ticks / 20;
        long minutes = timer / 60;
        long seconds = timer - (minutes * 60);

        return ChatColor.WHITE + String.format("%02d", minutes) + ChatColor.GRAY + ":" + ChatColor.WHITE + String.format("%02d", seconds);
    }

    // Set the title of the scoreboard.
    public void setTitle(String title)
    {
        this.title = title;
        refreshTitle();
    }

    // Set player score.
    @SuppressWarnings("deprecation")
    public void setPlayerScore(Player player, int score)
    {
        objective.getScore(player).setScore(score);
    }
}
