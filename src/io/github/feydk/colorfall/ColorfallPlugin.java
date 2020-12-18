package io.github.feydk.colorfall;

import com.winthier.sql.SQLDatabase;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class ColorfallPlugin extends JavaPlugin {
    private final Map<String, ItemStack> powerups = new HashMap<String, ItemStack>();
    private final Map<Integer, Round> rounds = new HashMap<Integer, Round>();
    private final Map<UUID, GamePlayer> gamePlayers = new HashMap<>();
    // Config stuff.
    private int disconnectLimit;
    private int waitForPlayersDuration;
    private int countdownToStartDuration;
    private int startedDuration;
    private int endDuration;
    private int lives;
    private List<String> worldNames;
    //final Highscore highscore = new Highscore();
    private GameScoreboard scoreboard;
    private SQLDatabase db;
    private BossBar bossBar;
    //
    private ColorfallGame game;

    @Override
    public void onEnable() {
        new ColorfallAdminCommand(this).enable();
        new ColorfallCommand(this).enable();
        db = new SQLDatabase(this);
        reloadConfig();
        saveDefaultConfig();
        saveResource("powerups.yml", false);
        saveResource("rounds.yml", false);
        loadConf();
        // Retiring the config value for now.
        System.out.println("Setting up Colorfall player stats");
        final String sql = ""
            + "CREATE TABLE IF NOT EXISTS `colorfall_playerstats` ("
            + " `id` INT(11) NOT NULL AUTO_INCREMENT,"
            + " `game_uuid` VARCHAR(40) NOT NULL,"
            + " `player_uuid` VARCHAR(40) NOT NULL,"
            + " `player_name` VARCHAR(16) NOT NULL,"
            + " `start_time` DATETIME NOT NULL,"
            + " `end_time` DATETIME NOT NULL,"
            + " `rounds_played` INT(11) NOT NULL,"
            + " `rounds_survived` INT(11) NOT NULL,"
            + " `deaths` INT(11) NOT NULL,"
            + " `lives_left` INT(11) NOT NULL,"
            + " `superior_win` INT(11) NOT NULL,"
            + " `dyes_used` INT(11) NOT NULL,"
            + " `randomizers_used` INT(11) NOT NULL,"
            + " `clocks_used` INT(11) NOT NULL,"
            + " `enderpearls_used` INT(11) NOT NULL,"
            + " `snowballs_used` INT(11) NOT NULL,"
            + " `snowballs_hit` INT(11) NOT NULL,"
            + " `winner` INT(11) NOT NULL,"
            + " `sp_game` BOOLEAN NOT NULL,"
            + " `map_id` VARCHAR(40) NULL, "
            + " PRIMARY KEY (`id`)"
            + ")";
        try {
            db.executeUpdate(sql);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Done setting up Colorfall player stats");
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1, 1);
        getServer().getPluginManager().registerEvents(new EventListener(this), this);
        bossBar = getServer().createBossBar("Colorfall", BarColor.BLUE, BarStyle.SOLID);
        scoreboard = new GameScoreboard();
        game = new ColorfallGame(this);
        game.enable();
        for (Player player : getServer().getOnlinePlayers()) {
            enter(player);
        }
    }

    @Override
    public void onDisable() {
        for (Player player : getServer().getOnlinePlayers()) {
            exit(player);
        }
    }

    public void enter(Player player) {
        scoreboard.addPlayer(player);
        bossBar.addPlayer(player);
    }

    public void exit(Player player) {
        scoreboard.removePlayer(player);
        bossBar.removePlayer(player);
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

    private void loadPowerups() {
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

    private void loadRounds() {
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
                    round.addPowerup(this.powerups.get(powerup), roundPowerups.getDouble(powerup));
                }
            }
            rounds.put(Integer.parseInt(key), round);
        }
    }

    void tick() {
        game.tick();
    }

    public GamePlayer getGamePlayer(Player player) {
        GamePlayer gp = gamePlayers.computeIfAbsent(player.getUniqueId(), u -> new GamePlayer(this, u));
        gp.setName(player.getName());
        if (player.getName().equals("Cavetale")) {
            gp.setSpectator();
        }
        return gp;
    }
}
