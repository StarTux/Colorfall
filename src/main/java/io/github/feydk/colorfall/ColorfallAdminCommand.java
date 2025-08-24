package io.github.feydk.colorfall;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.event.minigame.MinigameMatchType;
import com.winthier.creative.BuildWorld;
import com.winthier.playercache.PlayerCache;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class ColorfallAdminCommand extends AbstractCommand<ColorfallPlugin> {
    protected ColorfallAdminCommand(final ColorfallPlugin plugin) {
        super(plugin, "colorfalladmin");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("start")
            .completers(CommandArgCompleter.supplyList(() -> plugin.getWorldNames(true)))
            .description("Start a game")
            .arguments("<map>")
            .playerCaller(this::start);
        rootNode.addChild("test")
            .completableList(ctx -> plugin.getWorldNames(false))
            .description("Test a map")
            .arguments("<name>")
            .playerCaller(this::test);
        rootNode.addChild("stop").denyTabCompletion()
            .description("Stop the game")
            .playerCaller(this::stop);
        rootNode.addChild("item").arguments("<item>")
            .completableList(List.of("SpecialDye"))
            .description("Spawn an item")
            .playerCaller(this::item);
        rootNode.addChild("event").arguments("true|false")
            .completers(CommandArgCompleter.BOOLEAN)
            .description("Set event mode")
            .senderCaller(this::event);
        rootNode.addChild("pause").arguments("true|false")
            .completers(CommandArgCompleter.BOOLEAN)
            .description("Set pause mode")
            .senderCaller(this::pause);

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
        scoreNode.addChild("dump").denyTabCompletion()
            .description("Dump scores to file")
            .senderCaller(this::scoreDump);
    }

    private boolean start(Player player, String[] args) {
        if (args.length != 1) return false;
        final BuildWorld buildWorld = BuildWorld.findWithPath(args[0]);
        if (buildWorld == null) {
            throw new CommandWarn("Not found: " + args[0]);
        }
        if (buildWorld.getRow().parseMinigame() != MinigameMatchType.COLORFALL) {
            throw new CommandWarn("Not a Colorfall map: " + buildWorld.getName());
        }
        if (!buildWorld.getRow().isPurposeConfirmed()) {
            throw new CommandWarn("Unconfirmed map: " + buildWorld.getName());
        }
        player.sendMessage(text("Loading map: " + buildWorld.getName(), YELLOW));
        buildWorld.makeLocalCopyAsync(world -> {
                ColorfallGame game = new ColorfallGame(plugin);
                game.loadMap(buildWorld, world);
                plugin.getGames().add(game);
                game.bringAllPlayers();
                game.setState(GameState.COUNTDOWN_TO_START);
            });
        return true;
    }

    private boolean test(Player player, String[] args) {
        if (args.length != 1) return false;
        final BuildWorld buildWorld = BuildWorld.findWithPath(args[0]);
        if (buildWorld == null) {
            throw new CommandWarn("Not found: " + args[0]);
        }
        if (buildWorld.getRow().parseMinigame() != MinigameMatchType.COLORFALL) {
            throw new CommandWarn("Not a Colorfall map: " + buildWorld.getName());
        }
        player.sendMessage(text("Loading map: " + buildWorld.getName(), YELLOW));
        buildWorld.makeLocalCopyAsync(world -> {
                ColorfallGame game = new ColorfallGame(plugin);
                game.loadMap(buildWorld, world);
                plugin.getGames().add(game);
                game.bringAllPlayers();
                game.setState(GameState.COUNTDOWN_TO_START);
                game.setTest(true);
            });
        return true;
    }

    private void stop(Player player) {
        final ColorfallGame game = ColorfallGame.in(player.getWorld());
        if (game == null) {
            throw new CommandWarn("There is no game here");
        }
        plugin.stopGame(game);
        player.sendMessage("Game stopped");
    }

    private boolean item(Player player, String[] args) {
        if (args.length != 1) return false;
        String key = args[0];
        ItemStack stack = plugin.getPowerups().get(key);
        final ColorfallGame game = ColorfallGame.in(player.getWorld());
        if (game == null) {
            throw new CommandWarn("There is no game here");
        }
        if (args[0].equals("SpecialDye")) {
            BlockData blockData = game.getGameMap().getRandomFromColorPool();
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

    private boolean event(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        if (args.length == 1) {
            plugin.saveState.event = CommandArgCompleter.requireBoolean(args[0]);
            plugin.save();
        }
        sender.sendMessage(textOfChildren(text("Event mode ", YELLOW),
                                          (plugin.saveState.event
                                           ? text("Enabled", GREEN)
                                           : text("Disabled", RED))));
        return true;
    }

    private boolean pause(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        if (args.length == 1) {
            plugin.saveState.pause = CommandArgCompleter.requireBoolean(args[0]);
            plugin.save();
        }
        sender.sendMessage(textOfChildren(text("Pause mode ", YELLOW),
                                          (plugin.saveState.pause
                                           ? text("Enabled", GREEN)
                                           : text("Disabled", RED))));
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

    private void scoreDump(CommandSender sender) {
        final File file = new File(plugin.getDataFolder(), "score.dump");
        try (PrintStream out = new PrintStream(file, StandardCharsets.UTF_8)) {
            for (Map.Entry<UUID, Integer> entry : plugin.getSaveState().getScores().entrySet()) {
                if (entry.getValue() <= 0) continue;
                final UUID uuid = entry.getKey();
                out.println("" + uuid + " " + PlayerCache.nameForUuid(uuid));
            }
        } catch (IOException ioe) {
            plugin.getLogger().log(Level.SEVERE, "" + file, ioe);
        }
        sender.sendMessage(text("Scores dumped to " + file, YELLOW));
    }
}
