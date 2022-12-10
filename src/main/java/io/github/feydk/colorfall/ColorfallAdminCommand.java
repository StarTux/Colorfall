package io.github.feydk.colorfall;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.winthier.playercache.PlayerCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class ColorfallAdminCommand extends AbstractCommand<ColorfallPlugin> {
    protected ColorfallAdminCommand(final ColorfallPlugin plugin) {
        super(plugin, "colorfalladmin");
    }

    @Override
    protected void onEnable() {
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
        rootNode.addChild("eventauto").arguments("<value>")
            .completableList(List.of("true", "false"))
            .description("Set event auto mode")
            .senderCaller(this::eventAuto);
        rootNode.addChild("next").arguments("<worlds...>")
            .completableList(ctx -> plugin.getWorldNames())
            .description("Set the next worlds")
            .senderCaller(this::next);

        CommandNode scoreNode = rootNode.addChild("score")
            .description("Score commands");
        scoreNode.addChild("add")
            .description("Manipulate score")
            .completers(PlayerCache.NAME_COMPLETER,
                        CommandArgCompleter.integer(i -> i != 0))
            .senderCaller(this::scoreAdd);
        scoreNode.addChild("clear").denyTabCompletion()
            .description("Clear all scores")
            .senderCaller(this::scoreClear);
        scoreNode.addChild("reward").denyTabCompletion()
            .description("Reward players")
            .senderCaller(this::scoreReward);
    }

    boolean maps(Player player, String[] args) {
        if (args.length != 0) return false;
        List<Component> components = new ArrayList<>();
        for (String name : plugin.getWorldNames()) {
            components.add(text("[" + name + "]", GOLD)
                           .hoverEvent(HoverEvent.showText(text(name)))
                           .clickEvent(ClickEvent.runCommand("/cfa map " + name)));
        }
        Component msg = Component.join(JoinConfiguration.builder()
                                       .prefix(text(components.size() + " maps: ", AQUA))
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
        player.sendMessage(text("Loading map: " + name, YELLOW));
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
        player.sendMessage(text("Loading map: " + name, YELLOW));
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
        player.sendMessage(text("Given item " + key, YELLOW));
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

    boolean eventAuto(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        if (args.length == 1) {
            try {
                plugin.saveState.eventAuto = Boolean.parseBoolean(args[0]);
                plugin.save();
            } catch (IllegalArgumentException iae) {
                throw new CommandWarn("Illegal value: " + args[0]);
            }
        }
        sender.sendMessage("Event auto mode " + (plugin.saveState.eventAuto ? "enabled" : "disabled"));
        return true;
    }

    boolean next(CommandSender sender, String[] args) {
        plugin.saveState.worlds.clear();
        plugin.saveState.worlds.addAll(List.of(args));
        plugin.save();
        sender.sendMessage("Next worlds set: " + plugin.saveState.worlds);
        return true;
    }

    private boolean scoreClear(CommandSender sender, String[] args) {
        if (args.length != 0) return false;
        plugin.saveState.scores.clear();
        plugin.computeHighscore();
        sender.sendMessage(text("All scores cleared", AQUA));
        return true;
    }

    private boolean scoreAdd(CommandSender sender, String[] args) {
        if (args.length != 2) return false;
        PlayerCache target = PlayerCache.require(args[0]);
        int value = CommandArgCompleter.requireInt(args[1], i -> i != 0);
        plugin.saveState.addScore(target.uuid, value);
        plugin.computeHighscore();
        sender.sendMessage(text("Score of " + target.name + " manipulated by " + value, AQUA));
        return true;
    }

    private void scoreReward(CommandSender sender) {
        int count = plugin.rewardHighscore();
        sender.sendMessage(text("Rewarded " + count + " players", AQUA));
    }
}
