package io.github.feydk.colorfall;

import io.github.feydk.colorfall.util.Msg;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class ColorfallCommand implements CommandExecutor {
    private final ColorfallPlugin plugin;

    public ColorfallCommand enable() {
        plugin.getCommand("colorfall").setExecutor(this);
        return this;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command bcommand, String command, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        if (args.length == 0) return false;
        switch (args[0]) {
        case "ready": {
            if (plugin.getGame().getState() != GameState.WAIT_FOR_PLAYERS) {
                return true;
            }
            GamePlayer gp = plugin.getGamePlayer(player);
            gp.setReady(true);
            gp.setPlayer();
            plugin.getScoreboard().setPlayerScore(player, 1);
            player.teleport(gp.getSpawnLocation());
            Msg.send(player, ChatColor.GREEN + " Marked as ready");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
            return true;
        }
        default: return false;
        }
    }
}
