package io.github.feydk.colorfall;

import com.cavetale.core.chat.Chat;
import com.cavetale.core.event.minigame.MinigameMatchType;
import com.cavetale.core.util.Json;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import com.cavetale.mytems.util.BlockColor;
import com.cavetale.server.ServerPlugin;
import com.winthier.creative.BuildWorld;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@Getter
public final class ColorfallPlugin extends JavaPlugin {
    protected final Map<String, ItemStack> powerups = new HashMap<String, ItemStack>();
    protected final Map<Integer, Round> rounds = new HashMap<Integer, Round>();
    protected final Map<UUID, GamePlayer> gamePlayers = new HashMap<>();
    protected final ColorfallAdminCommand colorfallAdminCommand = new ColorfallAdminCommand(this);
    protected final ColorfallCommand colorfallCommand = new ColorfallCommand(this);
    // Config stuff.
    protected int disconnectLimit;
    protected int waitForPlayersDuration = 60;
    protected int countdownToStartDuration;
    protected int startedDuration;
    protected int endDuration;
    protected int lives;
    protected List<String> worldNames;
    protected BossBar bossBar;
    //
    @Setter protected ColorfallGame game;
    protected SaveState saveState;
    protected int ticksWaiting;
    protected boolean schedulingGame; // informal
    protected List<Highscore> highscore = List.of();
    public static final Component TITLE = join(noSeparators(),
                                               text("C", BlockColor.ORANGE.textColor),
                                               text("o", BlockColor.MAGENTA.textColor),
                                               text("l", BlockColor.LIGHT_BLUE.textColor),
                                               text("o", BlockColor.YELLOW.textColor),
                                               text("r", BlockColor.LIME.textColor),
                                               text("f", BlockColor.PINK.textColor),
                                               text("a", BlockColor.BLUE.textColor),
                                               text("l", BlockColor.GREEN.textColor),
                                               text("l", BlockColor.RED.textColor));
    protected final Map<String, ColorfallWorld> colorfallWorlds = new HashMap<>();

    @Override
    public void onEnable() {
        colorfallAdminCommand.enable();
        colorfallCommand.enable();
        reloadConfig();
        saveDefaultConfig();
        saveResource("powerups.yml", false);
        saveResource("rounds.yml", false);
        loadConf();
        loadBuildWorlds();
        // Retiring the config value for now.
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1, 1);
        getServer().getPluginManager().registerEvents(new EventListener(this), this);
        bossBar = BossBar.bossBar(Component.text("Colorfall"), 0.0f,
                                  BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        loadSave();
        computeHighscore();
        for (Player player : getServer().getOnlinePlayers()) {
            enter(player);
        }
    }

    @Override
    public void onDisable() {
        save();
        for (Player player : getServer().getOnlinePlayers()) {
            exit(player);
        }
        if (game != null) {
            game.cleanUpMap();
            game = null;
        }
        gamePlayers.clear();
        ServerPlugin.getInstance().setServerSidebarLines(null);
    }

    void loadSave() {
        saveState = Json.load(new File(getDataFolder(), "save.json"), SaveState.class, SaveState::new);
    }

    void save() {
        Json.save(new File(getDataFolder(), "save.json"), saveState, true);
    }

    public void enter(Player player) {
        player.showBossBar(bossBar);
    }

    public void exit(Player player) {
        player.hideBossBar(bossBar);
    }

    protected void loadConf() {
        disconnectLimit = getConfig().getInt("general.disconnectLimit");
        waitForPlayersDuration = getConfig().getInt("general.waitForPlayersDuration", waitForPlayersDuration);
        countdownToStartDuration = getConfig().getInt("general.countdownToStartDuration");
        startedDuration = getConfig().getInt("general.startedDuration");
        endDuration = getConfig().getInt("general.endDuration");
        lives = getConfig().getInt("general.lives");
        worldNames = getConfig().getStringList("maps");
        loadPowerups();
        loadRounds();
    }

    protected void loadBuildWorlds() {
        colorfallWorlds.clear();
        for (BuildWorld buildWorld : BuildWorld.findMinigameWorlds(MinigameMatchType.COLORFALL, true)) {
            ColorfallWorld cw = new ColorfallWorld();
            cw.setPath(buildWorld.getPath());
            cw.setDisplayName(buildWorld.getName());
            cw.setDescription(String.join(" ", buildWorld.getBuilderNames()));
            cw.setScore(buildWorld.getRow().getVoteScore());
            colorfallWorlds.put(cw.path, cw);
        }
        getLogger().info(colorfallWorlds.size() + " worlds loaded");
    }

    protected void loadPowerups() {
        powerups.clear();
        ConfigurationSection config = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "powerups.yml"));
        for (String key : config.getKeys(false)) {
            ItemStack item = config.getItemStack(key);
            if (item == null) {
                getLogger().warning("Bad powerup definition: " + key);
            } else {
                powerups.put(key, item);
            }
        }
    }

    protected void loadRounds() {
        rounds.clear();
        ConfigurationSection config = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "rounds.yml"));
        // Load root config nodes from. Each root node is valid for rounds until a new root node is found.
        for (String key : config.getKeys(false)) {
            long roundDuration = config.getLong(key + ".duration") * 20;
            double roundPvp = config.getLong(key + ".pvp");
            boolean collision = config.getBoolean(key + ".collision");
            double roundRandomize = config.getLong(key + ".randomize");
            MemorySection roundPowerups = (MemorySection) config.get(key + ".powerups");
            Round round = new Round(this);
            round.setDuration(roundDuration);
            round.setPvpChance(roundPvp);
            round.setCollision(collision);
            round.setRandomizeChance(roundRandomize);
            // Determine if this round will have pvp enabled.
            double number = Math.random() * 100;
            if (number - roundPvp <= 0) {
                round.setPvp(true);
            }
            // Determine if this round will have randomize enabled.
            number = Math.random() * 100;
            if (number - roundRandomize <= 0) {
                round.setRandomize(true);
            }
            // Parse powerups.
            if (roundPowerups != null) {
                for (String powerup : roundPowerups.getKeys(true)) {
                    round.addPowerup(powerups.get(powerup), roundPowerups.getDouble(powerup));
                }
            }
            rounds.put(Integer.parseInt(key), round);
        }
    }

    void tick() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            if (game != null) {
                game.disable();
                game = null;
            }
            gamePlayers.clear();
            ticksWaiting = 0;
            schedulingGame = false;
            ServerPlugin.getInstance().setServerSidebarLines(null);
            return;
        }
        boolean test = game != null && game.isTest();
        if (!test && players.size() < 2) {
            bossBar.name(Component.text("Waiting for players...", NamedTextColor.LIGHT_PURPLE));
            ticksWaiting = 0;
            schedulingGame = false;
            return;
        }
        if (game == null) {
            if (saveState.event && !saveState.eventAuto) {
                ticksWaiting = 0;
                schedulingGame = false;
                bossBar.name(Component.text("Preparing Event...", NamedTextColor.GREEN));
                bossBar.progress(1.0f);
                ServerPlugin.getInstance().setServerSidebarLines(null);
            } else if (ticksWaiting < waitForPlayersDuration * 20) {
                bossBar.name(Component.text("Waiting for players...", NamedTextColor.LIGHT_PURPLE));
                float progress = (float) ticksWaiting / (float) (waitForPlayersDuration * 20);
                bossBar.progress(Math.max(0.0f, Math.min(1.0f, progress)));
                if (ticksWaiting == 0) {
                    schedulingGame = true;
                    saveState.votes.clear();
                    loadBuildWorlds();
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        remindToVote(player);
                    }
                }
                ticksWaiting += 1;
                ServerPlugin.getInstance().setServerSidebarLines(List.of(Component.text("/colorfall", NamedTextColor.YELLOW),
                                                                         Component.text(players.size() + " waiting...",
                                                                                        NamedTextColor.GRAY)));
                return;
            } else {
                schedulingGame = false;
                loadPowerups();
                loadRounds();
                game = new ColorfallGame(this);
                game.enable();
            }
        }
        if (game != null && game.isObsolete()) {
            stopGame();
        } else if (game != null && game.getState() == GameState.INIT) {
            // Load New World
            gamePlayers.clear();
            GameMap oldMap = game.getGameMap();
            final String worldName;
            if (!saveState.votes.isEmpty()) {
                Map<String, Integer> stats = new HashMap<>();
                List<ColorfallWorld> randomWorlds = new ArrayList<>();
                for (String it : saveState.votes.values()) {
                    stats.compute(it, (s, i) -> i != null ? i + 1 : 1);
                    ColorfallWorld cw = colorfallWorlds.get(it);
                    if (cw != null) randomWorlds.add(cw);
                }
                getLogger().info("Votes: " + stats);
                assert !randomWorlds.isEmpty();
                ColorfallWorld colorfallWorld = randomWorlds.get(ThreadLocalRandom.current().nextInt(randomWorlds.size()));
                worldName = colorfallWorld.getPath();
            } else {
                if (saveState.worlds.isEmpty()) {
                    if (worldNames.isEmpty()) throw new IllegalStateException("World name is empty!");
                    saveState.worlds.addAll(worldNames);
                    Collections.shuffle(saveState.worlds);
                }
                worldName = saveState.worlds.remove(saveState.worlds.size() - 1);
            }
            save();
            if (game.isObsolete()) {
                loadPowerups();
                loadRounds();
                game = new ColorfallGame(this);
                game.enable();
            }
            game.loadMap(worldName);
            game.bringAllPlayers();
            game.setState(GameState.COUNTDOWN_TO_START);
            ColorfallWorld cw = colorfallWorlds.get(worldName);
            if (cw != null) {
                getLogger().info("Loading world " + cw);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage("");
                    player.sendMessage(textOfChildren(text("Map ", GRAY), text(cw.getDisplayName(), GREEN)));
                    player.sendMessage(textOfChildren(text("By ", GRAY), text(cw.getDescription(), GREEN)));
                    player.sendMessage("");
                }
            }
            if (oldMap != null) oldMap.cleanUp();
        }
        if (game == null) return;
        game.tick();
        if (game.isTest()) {
            ServerPlugin.getInstance().setServerSidebarLines(null);
        } else {
            switch (game.getState()) {
            case INIT:
            case WAIT_FOR_PLAYERS:
            case COUNTDOWN_TO_START:
            case STARTED:
                ServerPlugin.getInstance().setServerSidebarLines(List.of(Component.text("/colorfall", NamedTextColor.YELLOW),
                                                                         Component.text(game.countActivePlayers() + " playing", NamedTextColor.GRAY)));
                break;
            case END:
                ServerPlugin.getInstance().setServerSidebarLines(List.of(Component.text("/colorfall", NamedTextColor.YELLOW),
                                                                         Component.text("Game Over", NamedTextColor.GRAY)));
            default: break;
            }
        }
    }

    public void remindToVote(Player player) {
        Chat.sendNoLog(player, textOfChildren(newline(),
                                              Mytems.ARROW_RIGHT,
                                              (text(" Click here to vote on the next map", GREEN)
                                               .hoverEvent(showText(text("Map Selection", GRAY)))
                                               .clickEvent(runCommand("/colorfall vote"))),
                                              newline()));
    }

    public GamePlayer getGamePlayer(Player player) {
        GamePlayer gp = gamePlayers.computeIfAbsent(player.getUniqueId(), u -> new GamePlayer(this, u));
        gp.setName(player.getName());
        return gp;
    }

    public void stopGame() {
        if (game != null) {
            game.setState(GameState.INIT);
            game.cleanUpMap();
            game = null;
        }
        gamePlayers.clear();
        ticksWaiting = 0;
        schedulingGame = false;
    }

    protected void computeHighscore() {
        highscore = Highscore.of(saveState.scores);
    }

    protected int rewardHighscore() {
        return Highscore.reward(saveState.scores,
                                "colorfall",
                                TrophyCategory.MEDAL,
                                TITLE,
                                hi -> ("You collected "
                                       + hi.score + " point" + (hi.score == 1 ? "" : "s")));
    }
}
