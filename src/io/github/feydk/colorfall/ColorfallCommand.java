package io.github.feydk.colorfall;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

@RequiredArgsConstructor
public final class ColorfallCommand implements CommandExecutor {
    private final ColorfallPlugin plugin;

    public ColorfallCommand enable() {
        plugin.getCommand("colorfall").setExecutor(this);
        return this;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command bcommand, String command, String[] args) {
        return true;
    }
}
