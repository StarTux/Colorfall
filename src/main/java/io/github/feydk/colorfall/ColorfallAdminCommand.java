package io.github.feydk.colorfall;

import com.cavetale.core.command.CommandContext;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
        rootNode.addChild("test")
            .completableList(ctx -> plugin.getWorldNames())
            .description("Test a map")
            .arguments("<name>")
            .playerCaller(this::test);
        rootNode.addChild("stop")
            .completableList(ctx -> plugin.getWorldNames())
            .description("Stop the game")
            .playerCaller(this::stop);
        rootNode.addChild("item").arguments("<item>")
            .completableList(List.of("SpecialDye"))
            .description("Spawn an item")
            .playerCaller(this::item);
        rootNode.addChild("event").arguments("<value>")
            .completableList(List.of("true", "false"))
            .description("Set event mode")
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
        List<Component> components = new ArrayList<>();
        for (String name : plugin.getWorldNames()) {
            components.add(Component.text("[" + name + "]", NamedTextColor.GOLD)
                           .hoverEvent(HoverEvent.showText(Component.text(name)))
                           .clickEvent(ClickEvent.runCommand("/cfa map " + name)));
        }
        Component msg = Component.join(JoinConfiguration.builder()
                                       .prefix(Component.text(components.size() + " maps: ", NamedTextColor.AQUA))
                                       .separator(Component.space())
                                       .build(),
                                       components);
        player.sendMessage(msg);
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
        player.sendMessage(Component.text("Loading map: " + name, NamedTextColor.YELLOW));
        ColorfallGame game = new ColorfallGame(plugin);
        plugin.setGame(game);
        game.loadMap(name);
        game.bringAllPlayers();
        game.setState(GameState.COUNTDOWN_TO_START);
        return true;
    }

    boolean test(Player player, String[] args) {
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
        player.sendMessage(Component.text("Loading map: " + name, NamedTextColor.YELLOW));
        ColorfallGame game = new ColorfallGame(plugin);
        plugin.setGame(game);
        game.loadMap(name);
        game.bringAllPlayers();
        game.setState(GameState.COUNTDOWN_TO_START);
        game.setTest(true);
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
            BlockData blockData = plugin.getGame().getGameMap().getRandomFromColorPool();
            ItemStack newStack = new ItemStack(blockData.getMaterial());
            newStack.editMeta(meta -> {
                    meta.lore(stack.getItemMeta().lore());
                    meta.displayName(stack.getItemMeta().displayName());
                });
            player.getInventory().addItem(newStack);
        } else {
            player.getInventory().addItem(stack);
        }
        player.sendMessage(Component.text("Given item " + key, NamedTextColor.YELLOW));
        return true;
    }

    boolean event(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        if (args.length == 1) {
            try {
                plugin.saveState.event = Boolean.parseBoolean(args[0]);
                plugin.save();
            } catch (IllegalArgumentException iae) {
                throw new CommandWarn("Illegal value: " + args[0]);
            }
        }
        sender.sendMessage("Event mode " + (plugin.saveState.event ? "enabled" : "disabled"));
        return true;
    }

    boolean next(CommandSender sender, String[] args) {
        plugin.saveState.worlds.clear();
        plugin.saveState.worlds.addAll(List.of(args));
        plugin.save();
        sender.sendMessage("Next worlds set: " + plugin.saveState.worlds);
        return true;
    }
}
