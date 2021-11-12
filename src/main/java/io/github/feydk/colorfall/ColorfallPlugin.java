package io.github.feydk.colorfall;

import com.cavetale.server.ServerPlugin;
import io.github.feydk.colorfall.util.Json;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

@Getter
public final class ColorfallPlugin extends JavaPlugin {
    protected final Map<String, ItemStack> powerups = new HashMap<String, ItemStack>();
    protected final Map<Integer, Round> rounds = new HashMap<Integer, Round>();
    protected final Map<UUID, GamePlayer> gamePlayers = new HashMap<>();
    // Config stuff.
    protected int disconnectLimit;
    protected int waitForPlayersDuration;
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

    @Override
    public void onEnable() {
        new ColorfallAdminCommand(this).enable();
        new ColorfallCommand(this).enable();
        reloadConfig();
        saveDefaultConfig();
        saveResource("powerups.yml", false);
        saveResource("rounds.yml", false);
        loadConf();
        // Retiring the config value for now.
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1, 1);
        getServer().getPluginManager().registerEvents(new EventListener(this), this);
        bossBar = BossBar.bossBar(Component.text("Colorfall"), 0.0f,
                                  BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        loadSave();
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

    void loadConf() {
        disconnectLimit = getConfig().getInt("general.disconnectLimit");
        waitForPlayersDuration = getConfig().getInt("general.waitForPlayersDuration");
        countdownToStartDuration = getConfig().getInt("general.countdownToStartDuration");
        startedDuration = getConfig().getInt("general.startedDuration");
        endDuration = getConfig().getInt("general.endDuration");
        lives = getConfig().getInt("general.lives");
        worldNames = getConfig().getStringList("maps");
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

    void tick() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            if (game != null) {
                game.disable();
                game = null;
            }
            gamePlayers.clear();
            ticksWaiting = 0;
            ServerPlugin.getInstance().setServerSidebarLines(null);
            return;
        }
        boolean test = game != null && game.isTest();
        if (!test && players.size() < 2) {
            bossBar.name(Component.text("Waiting for players...", NamedTextColor.LIGHT_PURPLE));
            ticksWaiting = 0;
            return;
        }
        if (game == null) {
            if (ticksWaiting < waitForPlayersDuration * 20) {
                bossBar.name(Component.text("Waiting for players...", NamedTextColor.LIGHT_PURPLE));
                float progress = (float) ticksWaiting / (float) (waitForPlayersDuration * 20);
                bossBar.progress(Math.max(0.0f, Math.min(1.0f, progress)));
                ticksWaiting += 1;
                ServerPlugin.getInstance().setServerSidebarLines(List.of(Component.text("/colorfall", NamedTextColor.YELLOW),
                                                                         Component.text(players.size() + " Waiting...",
                                                                                        NamedTextColor.GRAY)));
                return;
            } else {
                loadPowerups();
                loadRounds();
                game = new ColorfallGame(this);
                game.enable();
            }
        }
        boolean loadNewWorld = game.getState() == GameState.INIT || (game.getState() == GameState.END && game.isObsolete());
        if (loadNewWorld) {
            gamePlayers.clear();
            GameMap oldMap = game.getGameMap();
            if (saveState.worlds.isEmpty()) {
                if (worldNames.isEmpty()) throw new IllegalStateException("World name is empty!");
                saveState.worlds.addAll(worldNames);
                Collections.shuffle(saveState.worlds);
            }
            String worldName = saveState.worlds.remove(saveState.worlds.size() - 1);
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
            if (oldMap != null) oldMap.cleanUp();
        }
        game.tick();
        switch (game.getState()) {
        case INIT:
        case WAIT_FOR_PLAYERS:
        case COUNTDOWN_TO_START:
        case STARTED:
            ServerPlugin.getInstance().setServerSidebarLines(List.of(Component.text("/colorfall", NamedTextColor.YELLOW),
                                                                     Component.text("Playing", NamedTextColor.GRAY)));
            break;
        case END:
            ServerPlugin.getInstance().setServerSidebarLines(List.of(Component.text("/colorfall", NamedTextColor.YELLOW),
                                                                     Component.text("Game Over", NamedTextColor.GRAY)));
        default: break;
        }
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
    }
}
