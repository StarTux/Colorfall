package io.github.feydk.colorfall;

import com.cavetale.core.command.CommandContext;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import io.github.feydk.colorfall.util.Msg;;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

@RequiredArgsConstructor
public final class ColorfallAdminCommand implements TabExecutor {
    private final ColorfallPlugin plugin;
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
            .arguments("<name>")
            .playerCaller(this::map);
        rootNode.addChild("stop")
            .completableList(ctx -> plugin.getWorldNames())
            .description("Stop the game")
            .playerCaller(this::stop);
        rootNode.addChild("item").arguments("<item>")
            .completableList(Arrays.asList("SpecialDye"))
            .description("Spawn an item")
            .playerCaller(this::item);
        rootNode.addChild("event").denyTabCompletion()
            .description("Toggle event mode")
            .senderCaller(this::event);
        rootNode.addChild("next").arguments("<worlds...>")
            .completableList(ctx -> plugin.getWorldNames())
            .description("Set the next worlds")
            .senderCaller(this::next);
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
        if (name.equals("random")) {
            Random random = ThreadLocalRandom.current();
            name = names.get(random.nextInt(names.size()));
        }
        if (plugin.getGame() != null && plugin.getGame().getState() != GameState.INIT) {
            throw new CommandWarn("Another map is already playing!");
        }
        player.sendMessage(ChatColor.YELLOW + "Loading map: " + name);
        ColorfallGame game = new ColorfallGame(plugin);
        plugin.setGame(game);
        game.loadMap(name);
        game.bringAllPlayers();
        game.setState(GameState.COUNTDOWN_TO_START);
        return true;
    }

    boolean stop(Player player, String[] args) {
        plugin.stopGame();
        player.sendMessage("Game stopped");
        return true;
    }

    boolean item(Player player, String[] args) {
        if (args.length != 1) return false;
        String key = args[0];
        ItemStack stack = plugin.getPowerups().get(key);
        if (args[0].equals("SpecialDye")) {
            ColorBlock cb = plugin.getGame().getGameMap().getRandomFromColorPool();
            ItemStack newStack = new ItemStack(cb.blockData.getMaterial());
            ItemMeta meta = newStack.getItemMeta();
            meta.setLore(stack.getItemMeta().getLore());
            meta.setDisplayName(stack.getItemMeta().getDisplayName());
            newStack.setItemMeta(meta);
            player.getInventory().addItem(newStack);
        } else {
            player.getInventory().addItem(stack);
        }
        Msg.send(player, " &eGiven item %s", key);
        return true;
    }

    boolean event(CommandSender sender, String[] args) {
        if (args.length != 0) return false;
        plugin.saveState.event = !plugin.saveState.event;
        plugin.save();
        sender.sendMessage("Event mode " + (plugin.saveState.event ? "enabled" : "disabled"));
        return true;
    }

    boolean next(CommandSender sender, String[] args) {
        plugin.saveState.worlds.clear();
        plugin.saveState.worlds.addAll(Arrays.asList(args));
        plugin.save();
        sender.sendMessage("Next worlds set: " + plugin.saveState.worlds);
        return true;
    }
}
