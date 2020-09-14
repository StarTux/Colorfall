package io.github.feydk.colorfall;

import com.cavetale.core.command.CommandContext;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class ColorfallAdminCommand implements TabExecutor {
    private final ColorfallGame plugin;
    private CommandNode rootNode;

    public void enable() {
        plugin.getCommand("colorfalladmin").setExecutor(this);
        rootNode = new CommandNode("cfa");
        rootNode.addChild("maps")
            .denyTabCompletion()
            .description("List maps")
            .playerCaller(this::maps);
        rootNode.addChild("map")
            .completableList(ctx -> plugin.getWorldNames())
            .description("Start a map")
            .playerCaller(this::map);
        rootNode.addChild("stop")
            .completableList(ctx -> plugin.getWorldNames())
            .description("Stop the game")
            .playerCaller(this::stop);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        return rootNode.call(new CommandContext(sender, command, alias, args), args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return rootNode.complete(new CommandContext(sender, command, alias, args), args);
    }

    boolean maps(Player player, String[] args) {
        if (args.length != 0) return false;
        ComponentBuilder cb = new ComponentBuilder();
        List<String> names = plugin.getWorldNames();
        cb.append(names.size() + " maps:").color(ChatColor.AQUA);
        for (String name : names) {
            cb.append(" ").reset();
            cb.append("[" + name + "]").color(ChatColor.GOLD)
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(name)))
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cfa map " + name));
        }
        player.sendMessage(cb.create());
        return true;
    }

    boolean map(Player player, String[] args) {
        if (args.length != 1) return false;
        String name = args[0];
        List<String> names = plugin.getWorldNames();
        if (!names.contains(name)) {
            throw new CommandWarn("Unknown map: " + name);
        }
        if (plugin.getState() != ColorfallGame.GameState.INIT) {
            throw new CommandWarn("Another map is already playing!");
        }
        plugin.loadWorld(name);
        plugin.setState(ColorfallGame.GameState.WAIT_FOR_PLAYERS);
        return true;
    }

    boolean stop(Player player, String[] args) {
        plugin.setState(ColorfallGame.GameState.INIT);
        plugin.cleanUpMap();
        player.sendMessage("Cleaned up.");
        return true;
    }
}
