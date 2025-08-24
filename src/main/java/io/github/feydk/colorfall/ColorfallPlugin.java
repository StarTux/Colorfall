package io.github.feydk.colorfall;

import com.cavetale.core.event.minigame.MinigameMatchType;
import com.cavetale.core.util.Json;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import com.cavetale.mytems.util.BlockColor;
import com.cavetale.server.ServerPlugin;
import com.winthier.creative.BuildWorld;
import com.winthier.creative.vote.MapVote;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import static com.cavetale.afk.AFKPlugin.isAfk;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@Getter
public final class ColorfallPlugin extends JavaPlugin {
    private static ColorfallPlugin instance;
    protected final Map<String, ItemStack> powerups = new HashMap<String, ItemStack>();
    protected final Map<Integer, Round> rounds = new HashMap<Integer, Round>();
    protected final ColorfallAdminCommand colorfallAdminCommand = new ColorfallAdminCommand(this);
    protected final ColorfallCommand colorfallCommand = new ColorfallCommand(this);
    // Config stuff.
    protected int disconnectLimit;
    protected int waitForPlayersDuration = 60;
    protected int countdownToStartDuration;
    protected int startedDuration;
    protected int endDuration;
    protected int lives;
    //
    private final List<ColorfallGame> games = new ArrayList<>();
    protected SaveState saveState;
    protected int ticksWaiting;
    protected List<Highscore> highscore = List.of();
    public static final Component TITLE = textOfChildren(text("C", BlockColor.ORANGE.textColor),
                                                         text("o", BlockColor.MAGENTA.textColor),
                                                         text("l", BlockColor.LIGHT_BLUE.textColor),
                                                         text("o", BlockColor.YELLOW.textColor),
                                                         text("r", BlockColor.LIME.textColor),
                                                         text("f", BlockColor.PINK.textColor),
                                                         text("a", BlockColor.BLUE.textColor),
                                                         text("l", BlockColor.GREEN.textColor),
                                                         text("l", BlockColor.RED.textColor));
    protected final Map<String, ColorfallWorld> colorfallWorlds = new HashMap<>();

    public static ColorfallPlugin colorfallPlugin() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        colorfallAdminCommand.enable();
        colorfallCommand.enable();
        reloadConfig();
        saveDefaultConfig();
        saveResource("powerups.yml", false);
        saveResource("rounds.yml", false);
        loadConf();
        // Retiring the config value for now.
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1, 1);
        getServer().getPluginManager().registerEvents(new EventListener(this), this);
        loadSave();
        computeHighscore();
    }

    @Override
    public void onDisable() {
        save();
        for (ColorfallGame game : games) {
            game.stop();
        }
        games.clear();
        ServerPlugin.getInstance().setServerSidebarLines(null);
    }

    void loadSave() {
        saveState = Json.load(new File(getDataFolder(), "save.json"), SaveState.class, SaveState::new);
    }

    void save() {
        Json.save(new File(getDataFolder(), "save.json"), saveState, true);
    }

    protected void loadConf() {
        disconnectLimit = getConfig().getInt("general.disconnectLimit");
        waitForPlayersDuration = getConfig().getInt("general.waitForPlayersDuration", waitForPlayersDuration);
        countdownToStartDuration = getConfig().getInt("general.countdownToStartDuration");
        startedDuration = getConfig().getInt("general.startedDuration");
        endDuration = getConfig().getInt("general.endDuration");
        lives = getConfig().getInt("general.lives");
        loadPowerups();
        loadRounds();
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

    public void stopGame(ColorfallGame game) {
        games.remove(game);
        game.stop();
    }

    private void tick() {
        for (ColorfallGame game : List.copyOf(games)) {
            if (game.isObsolete()) {
                games.remove(game);
                game.stop();
            } else {
                try {
                    game.tick();
                } catch (Exception e) {
                    getLogger().log(Level.SEVERE, game.getWorldName(), e);
                    games.remove(game);
                    game.stop();
                }
            }
        }
        scheduleMapVote();
    }

    private void scheduleMapVote() {
        if (saveState.pause || (saveState.event && !games.isEmpty())) {
            MapVote.stop(MinigameMatchType.COLORFALL);
            return;
        }
        int availablePlayers = 0;
        for (Player player : getLobbyWorld().getPlayers()) {
            if (isAfk(player)) continue;
            availablePlayers += 1;
        }
        if (MapVote.isActive(MinigameMatchType.COLORFALL) && availablePlayers < 2) {
            MapVote.stop(MinigameMatchType.COLORFALL);
            return;
        }
        if (!MapVote.isActive(MinigameMatchType.COLORFALL) && availablePlayers >= 2) {
            MapVote.start(MinigameMatchType.COLORFALL, mapVote -> {
                    mapVote.setTitle(TITLE);
                    mapVote.setLobbyWorld(getLobbyWorld());
                    // @Setter private Consumer<MapVote> voteHandler = null;
                    // @Setter private Consumer<MapVoteResult> callback = null;
                    mapVote.setCallback(result -> {
                            final BuildWorld buildWorld = result.getBuildWorldWinner();
                            final World world = result.getLocalWorldCopy();
                            final ColorfallGame game = new ColorfallGame(this);
                            game.loadMap(buildWorld, world);
                            games.add(game);
                            game.bringAllPlayers();
                            game.setState(GameState.COUNTDOWN_TO_START);
                        });
                });
        }
    }

    public World getLobbyWorld() {
        return Bukkit.getWorlds().get(0);
    }

    public List<String> getWorldNames(boolean requireConfirmation) {
        final List<String> result = new ArrayList<>();
        for (BuildWorld buildWorld : BuildWorld.findMinigameWorlds(MinigameMatchType.COLORFALL, requireConfirmation)) {
            result.add(buildWorld.getPath());
        }
        return result;
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
