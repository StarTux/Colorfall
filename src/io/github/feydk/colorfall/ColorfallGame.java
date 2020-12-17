package io.github.feydk.colorfall;

import com.destroystokyo.paper.event.entity.ProjectileCollideEvent;
import com.winthier.sql.SQLDatabase;
import io.github.feydk.colorfall.GameMap.ColorBlock;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.json.simple.JSONValue;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

@Getter
public class ColorfallGame extends JavaPlugin implements Listener
{
    // Stuff for keeping track of the game loop and ticks.
    GameState state;
    private RoundState roundState;
    BukkitRunnable task;
    long ticks;
    long stateTicks;
    long roundTicks;
    long winTicks = -1;
    boolean disallowRandomize;
    boolean disallowPistons;
    long randomizeCooldown = 0;
    private String joinRandomStat;

    private GameScoreboard scoreboard;
    private GameMap map;
    private Map<String, ItemStack> powerups = new HashMap<String, ItemStack>();
    private Map<Integer, Round> rounds = new HashMap<Integer, Round>();
    private final Map<UUID, GamePlayer> gamePlayers = new HashMap<>();

    // Config stuff.
    private int disconnectLimit;
    private int waitForPlayersDuration;
    private int countdownToStartDuration;
    private int startedDuration;
    private int endDuration;
    private int lives;
    private List<String> worldNames;

    // Level config, sent from the framework.
    private String mapID = "Classic";
    boolean debug = false;

    // Debug stuff.
    List<String> debugStrings = new ArrayList<String>();
    boolean denyStart = false;

    private boolean moreThanOnePlayed;
    private GamePlayer winner;
    private int currentRoundIdx;
    private Round currentRound;
    private long currentRoundDuration;
    private boolean currentRoundRandomized;
    private ColorBlock currentColor;
    private List<Block> paintedBlocks = new ArrayList<Block>();

    // Highscore stuff.
    UUID gameUuid = UUID.randomUUID();
    //final Highscore highscore = new Highscore();
    SQLDatabase db;
    BossBar bossBar;

    public ColorfallGame()
    {
        state = GameState.INIT;
    }

    public static enum GameState
    {
        INIT,
        WAIT_FOR_PLAYERS,
        COUNTDOWN_TO_START,
        STARTED,
        END
    }

    public static enum RoundState
    {
        RUNNING,
        REMOVING_BLOCKS,
        RESTORING_BLOCKS,
        OVER
    }

    public GameScoreboard getScoreboard()
    {
        return scoreboard;
    }

    public GameMap getMap()
    {
        return map;
    }

    @Override
    public void onEnable() {
        new ColorfallAdminCommand(this).enable();
        db = new SQLDatabase(this);
        saveDefaultConfig();
        reloadConfig();
        saveResource("powerups.yml", false);
        saveResource("rounds.yml", false);

        ConfigurationSection config = getConfig();

        disconnectLimit = config.getInt("general.disconnectLimit");
        waitForPlayersDuration = config.getInt("general.waitForPlayersDuration");
        countdownToStartDuration = config.getInt("general.countdownToStartDuration");
        startedDuration = config.getInt("general.startedDuration");
        endDuration = config.getInt("general.endDuration");
        lives = config.getInt("general.lives");
        worldNames = config.getStringList("maps");

        // Retiring the config value for now.

        loadPowerups();
        loadRounds();

        System.out.println("Setting up Colorfall player stats");

        final String sql =
            "CREATE TABLE IF NOT EXISTS `colorfall_playerstats` (" +
            " `id` INT(11) NOT NULL AUTO_INCREMENT," +
            " `game_uuid` VARCHAR(40) NOT NULL," +
            " `player_uuid` VARCHAR(40) NOT NULL," +
            " `player_name` VARCHAR(16) NOT NULL," +
            " `start_time` DATETIME NOT NULL," +
            " `end_time` DATETIME NOT NULL," +
            " `rounds_played` INT(11) NOT NULL," +
            " `rounds_survived` INT(11) NOT NULL," +
            " `deaths` INT(11) NOT NULL," +
            " `lives_left` INT(11) NOT NULL," +
            " `superior_win` INT(11) NOT NULL," +
            " `dyes_used` INT(11) NOT NULL," +
            " `randomizers_used` INT(11) NOT NULL," +
            " `clocks_used` INT(11) NOT NULL," +
            " `enderpearls_used` INT(11) NOT NULL," +
            " `snowballs_used` INT(11) NOT NULL," +
            " `snowballs_hit` INT(11) NOT NULL," +
            " `winner` INT(11) NOT NULL," +
            " `sp_game` BOOLEAN NOT NULL," +
            " `map_id` VARCHAR(40) NULL, " +
            " PRIMARY KEY (`id`)" +
            ")";

        try
            {
                db.executeUpdate(sql);
            }
        catch(Exception e)
            {
                e.printStackTrace();
            }

        System.out.println("Done setting up Colorfall player stats");

        task = new BukkitRunnable()
            {
                @Override public void run()
                {
                    onTick();
                }
            };

        task.runTaskTimer(this, 1, 1);
        getServer().getPluginManager().registerEvents(this, this);

        bossBar = getServer().createBossBar("Colorfall", BarColor.BLUE, BarStyle.SOLID);

        scoreboard = new GameScoreboard();
        for (Player player : getServer().getOnlinePlayers()) {
            scoreboard.addPlayer(player);
            bossBar.addPlayer(player);
        }
    }

    void reset() {
        roundState = null;
        ticks = 0;
        stateTicks = 0;
        roundTicks = 0;
        winTicks = -1;
        randomizeCooldown = 0;
        powerups.clear();
        rounds.clear();
        gamePlayers.clear();
        moreThanOnePlayed = false;
        winner = null;
        currentRoundIdx = 0;
        currentRound = null;
        currentRoundDuration = 0;
        currentRoundRandomized = false;
        currentColor = null;
        paintedBlocks.clear();
    }

    public void loadWorld(String worldName) {
        World world = ColorfallLoader.loadWorld(this, worldName);

        world.setDifficulty(Difficulty.HARD);
        world.setPVP(false);
        world.setGameRuleValue("doTileDrops", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setGameRuleValue("doFireTick", "false");
        world.setWeatherDuration(Integer.MAX_VALUE);
        world.setStorm(false);

        map = new GameMap(getConfig().getInt("general.chunkRadius"), this, world);
        map.process();

        if(map.getStartingTime() == -1)
            world.setTime(1000L);
        else
            world.setTime(map.getStartingTime());

        if(map.getLockTime())
            world.setGameRuleValue("doDaylightCycle", "false");
        else
            world.setGameRuleValue("doDaylightCycle", "true");
    }

    private void loadPowerups()
    {
        ConfigurationSection config = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "powerups.yml"));

        for(String key : config.getKeys(false))
            {
                ItemStack item = config.getItemStack(key);

                if(item == null)
                    {
                        getLogger().warning("Bad powerup definition: " + key);
                    }
                else
                    {
                        powerups.put(key, item);
                    }
            }
    }

    private void loadRounds()
    {
        ConfigurationSection config = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "rounds.yml"));

        // Load root config nodes from. Each root node is valid for rounds until a new root node is found.
        for(String key : config.getKeys(false))
            {
                long roundDuration = config.getLong(key + ".duration") * 20;
                double roundPvp = config.getLong(key + ".pvp");
                boolean collision = config.getBoolean(key + ".collision");
                double roundRandomize = config.getLong(key + ".randomize");
                MemorySection roundPowerups = (MemorySection)config.get(key + ".powerups");

                Round round = new Round(this);
                round.setDuration(roundDuration);
                round.setPvpChance(roundPvp);
                round.setCollision(collision);
                round.setRandomizeChance(roundRandomize);

                // Determine if this round will have pvp enabled.
                double number = Math.random() * 100;

                if(number - roundPvp <= 0)
                    round.setPvp(true);

                // Determine if this round will have randomize enabled.
                number = Math.random() * 100;

                if(number - roundRandomize <= 0)
                    round.setRandomize(true);

                // Parse powerups.
                if(roundPowerups != null)
                    {
                        for(String powerup : roundPowerups.getKeys(true))
                            {
                                round.addPowerup(this.powerups.get(powerup), roundPowerups.getDouble(powerup));
                            }
                    }

                rounds.put(Integer.parseInt(key), round);
            }
    }

    // Find the config that belongs to this round. I.e. if round is 2 and we have round configs for 1 and 20, we use the config for round 1.
    private Round getRound(int round)
    {
        if(currentRound == null)
            {
                Round found = null;

                for(Entry<Integer, Round> entry : rounds.entrySet())
                    {
                        if(round >= entry.getKey())
                            {
                                found = entry.getValue();
                                //debug("Using config for round " + entry.getKey());
                            }
                    }

                if(found != null)
                    {
                        Round r = found.copy();

                        // Determine if this round will have pvp enabled.
                        double number = Math.random() * 100;

                        if(number - r.getPvpChance() <= 0)
                            r.setPvp(true);

                        // Determine if this round will have randomize enabled.
                        number = Math.random() * 100;

                        if(number - r.getRandomizeChance() <= 0)
                            r.setRandomize(true);

                        currentRound = r;
                    }
            }

        return currentRound;
    }

    @Override
    public void onDisable()
    {
        cleanUpMap();
        task.cancel();
        for (Player player : getServer().getOnlinePlayers()) {
            scoreboard.removePlayer(player);
            bossBar.removePlayer(player);
        }
    }

    @SuppressWarnings("static-access")
    private void onTick()
    {
        ticks++;
        GameState newState = null;

        if(state != GameState.INIT && state != GameState.WAIT_FOR_PLAYERS && state != GameState.END)
            {
                // Check if only one player is left.
                int aliveCount = 0;
                GamePlayer survivor = null;

                for(Player player : getServer().getOnlinePlayers())
                    {
                        GamePlayer gp = getGamePlayer(player);
                        if(gp.isAlive() && !gp.isSpectator())
                            {
                                survivor = gp;
                                aliveCount += 1;
                            }
                    }
 
                // There will only be picked a winner if there were
                // more than one player playing. Meaning that a SP
                // game shouldn't be rewarded with a win.
                if(aliveCount == 1 && survivor != null && moreThanOnePlayed)
                    {
                        // Consider this scenario: 2 players left
                        // alive, both with 1 life left.  Both of them
                        // falls about at the same time, but one
                        // reaches the void slightly before the other.
                        // This should be declared a draw, but without
                        // the code below, whoever reaches the void
                        // last will win.  So I'm trying to prevent
                        // that by waiting a few secs, then see if the
                        // 'winner' is actually still alive.
                        if(winTicks == -1)
                            {
                                winTicks = 0;
                            }
                        else
                            {
                                winTicks++;

                                if(winTicks >= 80)
                                    {                                           
                                        if(survivor.isAlive())
                                            {
                                                winner = survivor;
                                                survivor.setWinner();
                                                survivor.setEndTime(new Date());

                                                // if(!debug)
                                                //     survivor.recordStats(moreThanOnePlayed, mapID);

                                                newState = GameState.END;
                                            }
                                    }
                            }
                    }
                else if(aliveCount == 0)
                    {
                        winner = null;
                        newState = GameState.END;
                    }

                if(randomizeCooldown > 0)
                    randomizeCooldown--;
            }

        // Check for disconnects.
        for(GamePlayer gp : new ArrayList<>(gamePlayers.values()))
            {
                Player player = getServer().getPlayer(gp.uuid);
                if (player != null) gp.onTick(player);

                if(player == null)
                    {
                        // Kick players who disconnect too long.
                        long discTicks = gp.getDisconnectedTicks();

                        if(discTicks > disconnectLimit * 20)
                            {
                                getLogger().info("Kicking " + gp.getName() + " because they were disconnected too long");
                                gp.setSpectator();
                                gamePlayers.remove(gp.uuid);
                            }

                        gp.setDisconnectedTicks(discTicks + 1);
                    }
            }

        if(newState == null)
            newState = tickState(state);

        if(newState != null && state != newState)
            {
                onStateChange(state, newState);
                state = newState;
            }
    }

    @SuppressWarnings("incomplete-switch")
    void onStateChange(GameState oldState, GameState newState)
    {
        stateTicks = 0;

        switch(newState)
            {
            case INIT:
                reset();
                break;
            case WAIT_FOR_PLAYERS:
                scoreboard.setTitle(ChatColor.GREEN + "Waiting");
                break;
            case COUNTDOWN_TO_START:
                scoreboard.setTitle(ChatColor.GREEN + "Get ready..");

                // Once the countdown starts, remove everyone who disconnected.
                for(GamePlayer gp: gamePlayers.values())
                    {
                        Player player = getServer().getPlayer(gp.uuid);
                        if(player == null)
                            {
                                gp.setSpectator();
                            }

                        if(gp.isPlayer())
                            {
                                gp.setLives(lives);
                            }
                    }
                for(Player player : getServer().getOnlinePlayers()) {
                    GamePlayer gp = getGamePlayer(player);
                    player.teleport(gp.getSpawnLocation());
                    if (gp.isReady()) {
                        player.getInventory().clear();
                        gp.setPlayer();
                    }
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
                }
                break;
            case STARTED:
                int count = 0;

                for(Player player : getServer().getOnlinePlayers())
                    {
                        giveStartingItems(player);

                        GamePlayer gp = getGamePlayer(player);

                        gp.makeMobile(player);
                        gp.setStartTime(new Date());
                        player.playSound(player.getEyeLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.MASTER, 1f, 1f);
                        count++;
                    }

                if(count > 1)
                    {
                        moreThanOnePlayed = true;
                    }
                else
                    {
                        // If it's a single player game not in debug mode, reduce lives to 1.
                        if(!debug)
                            {
                                for(Player player : getServer().getOnlinePlayers())
                                    {
                                        GamePlayer gp = getGamePlayer(player);
                                        gp.setLives(1);
                                    }
                            }
                    }

                break;
            case END:
                for(Player player : getServer().getOnlinePlayers())
                    {
                        getGamePlayer(player).setSpectator();

                        player.playSound(player.getEyeLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, SoundCategory.MASTER, 1f, 1f);
                    }

                // Restore the map if this state was entered in the removing blocks round state.
                if(roundState == RoundState.REMOVING_BLOCKS)
                    map.restoreBlocks(paintedBlocks);

                scoreboard.setTitle("Game over");
            }
    }

    private void onRoundStateChange(RoundState oldState, RoundState newState)
    {
        roundTicks = 0;
        World world = map.getWorld();
        switch(newState)
            {
                // We started a new round.
            case RUNNING:
                paintedBlocks.clear();
                currentRoundRandomized = false;
                currentRound = null;
                currentRoundIdx++;
                scoreboard.setTitle(ChatColor.GREEN + "Round " + currentRoundIdx);
                setColorForRound();
                disallowRandomize = false;

                Round round = getRound(currentRoundIdx);
                currentRoundDuration = round.getDuration();

                scoreboard.setCollision(round.getCollision());

                // If single player game not in debug mode, disable pvp.
                if(!moreThanOnePlayed && !debug)
                    world.setPVP(false);
                else
                    world.setPVP(round.getPvp());

                // Do this again to make sure. It seems 1.9 changed this somehow.
                world.setWeatherDuration(Integer.MAX_VALUE);
                world.setStorm(false);

                for(Player player : getServer().getOnlinePlayers())
                    {
                        GamePlayer gp = getGamePlayer(player);

                        if(gp.isAlive() && gp.isPlayer())
                            {
                                gp.addRound();
                                gp.setDiedThisRound(false);

                                // Hand out powerups.
                                
                                List<ItemStack> powerups = round.getDistributedPowerups(gp.getLivesLeft());

                                if(powerups.size() > 0)
                                    {
                                        for(ItemStack stack : powerups)
                                            player.getInventory().addItem(stack);
                                    }

                                if(gp.isPlayer() && player.getGameMode() == GameMode.SPECTATOR)
                                    {
                                        player.teleport(gp.getSpawnLocation());
                                        gp.setPlayer();
                                    }
                            }

                        // Announce pvp and color.
                        //Color color = Color.fromBlockData(currentColor.blockData);
                        //showTitle(player, (world.getPVP() ? ChatColor.DARK_RED + "PVP is on!" : ""), ChatColor.WHITE + "The color of this round is " + color.toChatColor() + color.niceName);
                    }

                break;
                // Round time is over, remove blocks.
            case REMOVING_BLOCKS:
                world.setPVP(false);
                map.removeBlocks(currentColor);
                break;
                // Restore blocks.
            case RESTORING_BLOCKS:
                map.removeEnderPearls();
                map.restoreBlocks(paintedBlocks);
                disallowPistons = false;
                break;
                // Round is over, wait for next round.
            case OVER:
                scoreboard.setTitle(ChatColor.GREEN + "Get ready.. ");
                break;
            }
    }

    private void setColorForRound()
    {
        ColorBlock pick;

        // Pick a color for this round. Which has to be different than the one from the previous round.
        while((pick = map.getRandomFromColorPool()).equals(currentColor))
            {
                // do nothing
            }

        // Item for the new color block.
        // ItemStack stack = new ItemStack(pick.blockData.getMaterial());

        // Remove all color blocks from player inventories and give them the new one.
        // for(Player player : getServer().getOnlinePlayers())
        //     {
        //         Inventory inv = player.getInventory();

        //         if(currentColor != null)
        //             inv.remove(currentColor.blockData.getMaterial());

        //         inv.addItem(stack);
        //     }

        currentColor = pick;
    }

    private GameState tickState(GameState state)
    {
        long ticks = this.stateTicks++;

        switch(state)
            {
            case INIT:
                return tickInit(ticks);
            case WAIT_FOR_PLAYERS:
                return tickWaitForPlayers(ticks);
            case COUNTDOWN_TO_START:
                return tickCountdownToStart(ticks);
            case STARTED:
                return tickStarted(ticks);
            case END:
                return tickEnd(ticks);
            }

        return null;
    }

    GameState tickInit(long ticks)
    {
        return null;
    }

    GameState tickWaitForPlayers(long ticks)
    {
        // Every 5 seconds, ask players to ready (or leave).
        if(ticks % (20 * 5) == 0)
            {
                for(Player player : getServer().getOnlinePlayers())
                    {
                        GamePlayer gp = getGamePlayer(player);

                        if(!gp.isReady())
                            {
                                List<Object> list = new ArrayList<>();
                                list.add(format(" &fClick here when ready: "));
                                list.add(button("&3[Ready]", "&3Mark yourself as ready", "/ready"));
                                // list.add(format("&f or "));
                                // list.add(button("&c[Quit]", "&cLeave this game", "/quit"));

                                sendRaw(player, list);
                            }
                    }
            }

        long totalCount = 0;
        long readyCount = 0;
        for (Player player : getServer().getOnlinePlayers()) {
            GamePlayer gamePlayer = getGamePlayer(player);
            if (gamePlayer.isSpectator()) continue;
            totalCount += 1;
            if (gamePlayer.isReady()) readyCount += 1;
        }
        long timeLeft = (waitForPlayersDuration * 20) - ticks;
        bossBar.setTitle(ChatColor.AQUA + "Waiting for players (" + readyCount + "/" + totalCount + ")");
        bossBar.setProgress(waitForPlayersDuration == 0.0 ? 0.0 : (double) timeLeft / (double) (waitForPlayersDuration * 20));

        // Every second, update the sidebar timer.
        if(timeLeft % 20 == 0)
            {
                scoreboard.refreshTitle(timeLeft);

                // Check if all players are ready.
                boolean allReady = true;
                int playerCount = 0;

                for(Player player : getServer().getOnlinePlayers())
                    {
                        playerCount++;
                        GamePlayer gp = getGamePlayer(player);

                        if(!gp.isReady() && !gp.isSpectator())
                            {
                                allReady = false;
                                break;
                            }
                    }

                // If they are, start the countdown (to start the game).
                if(allReady) {
                    return GameState.COUNTDOWN_TO_START;
                }
            }

        // Time ran out, so we force everyone ready.
        if(timeLeft <= 0) {
            return GameState.COUNTDOWN_TO_START;
        }

        return null;
    }

    GameState tickCountdownToStart(long ticks)
    {
        long timeLeft = (countdownToStartDuration * 20) - ticks;

        // Every second..
        if(timeLeft % 20L == 0)
            {
                long seconds = timeLeft / 20;

                scoreboard.refreshTitle(timeLeft);

                for(Player player : getServer().getOnlinePlayers())
                    {
                        if(seconds == 0)
                            {
                                showTitle(player, ChatColor.GREEN + "Go!", "", 0, 20, 0);
                                player.playSound(player.getEyeLocation(), Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.MASTER, 0.2f, 1f);
                            }
                        else if(seconds == countdownToStartDuration)
                            {
                                showTitle(player, ChatColor.GREEN + "Get ready!", ChatColor.GREEN + "Game starts in " + countdownToStartDuration + " seconds");
                                send(player, ChatColor.AQUA + " Game starts in %d seconds", seconds);
                            }
                        else
                            {
                                showTitle(player, ChatColor.GREEN + "Get ready!", "" + ChatColor.GREEN + seconds);
                                player.playNote(player.getEyeLocation(), Instrument.PIANO, new Note((int)seconds));
                            }
                    }
            }

        if(timeLeft <= 0)
            return GameState.STARTED;

        return null;
    }

    GameState tickStarted(long ticks)
    {
        RoundState newState = null;

        if(denyStart)
            {
                for(Player player : getServer().getOnlinePlayers())
                    {
                        send(player, ChatColor.RED + " Not starting game, due to missing configuration.");
                    }

                return GameState.END;
            }

        if(roundState == null)
            {
                newState = RoundState.RUNNING;
            }

        roundTicks++;

        long timeLeft = (startedDuration * 20) - ticks;
        long roundTimeLeft = 0;

        if(roundState == RoundState.RUNNING)
            roundTimeLeft = currentRoundDuration - roundTicks;
        else if(roundState == RoundState.REMOVING_BLOCKS)
            roundTimeLeft = 60 - roundTicks; // 3 seconds
        else if(roundState == RoundState.RESTORING_BLOCKS)
            roundTimeLeft = 20 - roundTicks;
        else if(roundState == RoundState.OVER)
            roundTimeLeft = 80 - roundTicks;

        World world = map.getWorld();
        if(roundState == RoundState.RUNNING && roundTimeLeft % 20L == 0)
            {
                Round round = getRound(currentRoundIdx);

                scoreboard.refreshTitle(roundTimeLeft);
                scoreboard.updatePlayers();

                //String actionMsg = (world.getPVP() ? ChatColor.DARK_RED + "PVP is on " + ChatColor.WHITE + "- " : "");

                // If it's night time or if we're in the end, use white color.
                // if(world.getTime() >= 13000 || world.getEnvironment() == World.Environment.THE_END)
                //     actionMsg += ChatColor.WHITE;
                // else
                //     actionMsg += ChatColor.BLACK;

                Color color = Color.fromBlockData(currentColor.blockData);
                //actionMsg += "The color of this round is " + color.toChatColor() + color.niceName;

                bossBar.setTitle("" + color.toChatColor() + ChatColor.BOLD + color.niceName
                                 + (world.getPVP() ? ChatColor.DARK_RED + " PVP" : ""));
                bossBar.setProgress(currentRoundDuration == 0.0 ? 0.0 : (double) roundTimeLeft / (double) currentRoundDuration);

                long seconds = roundTimeLeft / 20;
                if (seconds <= 3) disallowRandomize = true;

                for(Player player : getServer().getOnlinePlayers())
                    {
                        //sendActionBar(player, actionMsg);

                        // Countdown 3 seconds before round ends.
                        if(seconds > 0 && seconds <= 3)
                            {
                                showTitle(player, "", "" + ChatColor.RED + seconds, 0, 20, 0);
                                player.playNote(player.getEyeLocation(), Instrument.PIANO, new Note((int)seconds));

                                if(seconds <= 2)
                                    disallowPistons = true;
                            }
                        else if(seconds == 0)
                            {
                                // Reset title so we don't have the '1' from the countdown hanging too long.
                                //showTitle(player, "", "");
                            }
                    }

                // Show/refresh particle effect above the blocks.
                //map.animateBlocks(currentColor);
                map.highlightBlocks(currentColor);

                // Handle randomize events.
                if(round.getRandomize() && !currentRoundRandomized)
                    {
                        // Fire this about 2 seconds before we're half way through the round, but no later than 2 seconds after half way.
                        // Note: the 2 seconds works with 15 second rounds. Should probably be made more dynamic or configurable.
                        if(roundTimeLeft - 40 <= Math.round(currentRoundDuration / 2) && roundTimeLeft + 40 >= Math.round(currentRoundDuration / 2) && randomizeCooldown <= 0)
                            {
                                map.randomizeBlocks();
                                currentRoundRandomized = true;
                                randomizeCooldown = 5 * 20;

                                String title = ChatColor.WHITE + "" + ChatColor.DARK_AQUA + "R" + ChatColor.DARK_PURPLE + "a" + ChatColor.GOLD + "n" + ChatColor.GREEN + "d" + ChatColor.AQUA + "o" + ChatColor.RED + "m";
                                title += ChatColor.WHITE + "i" + ChatColor.LIGHT_PURPLE + "z" + ChatColor.AQUA + "e" + ChatColor.GOLD + "d" + ChatColor.WHITE + "!";

                                for(Player player : getServer().getOnlinePlayers())
                                    {
                                        showTitle(player, "", title);
                                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.2f, 1f);
                                    }
                            }
                    }

                if(roundTimeLeft <= 0)
                    {
                        map.clearHighlightBlocks();
                        newState = RoundState.REMOVING_BLOCKS;
                    }
            }

        if(roundState == RoundState.REMOVING_BLOCKS && roundTimeLeft <= 0)
            {
                newState = RoundState.RESTORING_BLOCKS;
            }

        if(roundState == RoundState.RESTORING_BLOCKS && roundTimeLeft <= 0)
            {
                newState = RoundState.OVER;
            }

        if(roundState == RoundState.OVER && roundTimeLeft % 20L == 0)
            {
                long seconds = roundTimeLeft / 20;

                for(Player player : getServer().getOnlinePlayers())
                    {
                        if(seconds <= 0)
                            {
                                player.playSound(player.getEyeLocation(), Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.MASTER, 0.2f, 1f);
                            }
                        else
                            {
                                showTitle(player, "" + seconds, ChatColor.GREEN + "Round " + (currentRoundIdx + 1), 0, 20, 0);
                                player.playNote(player.getEyeLocation(), Instrument.PIANO, new Note((int)seconds));
                            }
                    }

                if(roundTimeLeft <= 0)
                    newState = RoundState.RUNNING;
            }

        if(newState != null && roundState != newState)
            {
                //getLogger().info("Entering round state: " + newState);
                onRoundStateChange(roundState, newState);
                roundState = newState;
            }

        if(startedDuration > 0 && timeLeft <= 0) {
            return GameState.END;
        }

        return null;
    }

    GameState tickEnd(long ticks)
    {
        if(ticks == 0)
            {
                if (winner != null) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "titles unlockset " + winner.getName() + " Technicolor");
                }
                for(Player player : getServer().getOnlinePlayers())
                    {
                        if(winner != null)
                            {
                                send(player, " &b%s wins the game!", winner.getName());
                            }
                        else
                            {
                                send(player, " &bDraw! Nobody wins.");
                            }

                        List<Object> list = new ArrayList<>();
                        list.add(" Click here to leave the game: ");
                        list.add(button("&c[Spawn]", "&cLeave this game", "/spawn"));
                        sendRaw(player, list);
                    }
            }

        long timeLeft = (endDuration * 20) - ticks;

        // Every second, update the sidebar timer.
        if(timeLeft % 20L == 0)
            {
                scoreboard.refreshTitle(timeLeft);
            }

        // Every 5 seconds, show/refresh the winner title announcement.
        if(timeLeft % (20 * 5) == 0)
            {
                for(Player player : getServer().getOnlinePlayers())
                    {
                        if(winner != null)
                            {
                                showTitle(player, "&a" + winner.getName(), "&aWins the Game!");
                            }
                        else
                            {
                                showTitle(player, "&cDraw!", "&cNobody wins");
                            }
                    }
            }

        if(timeLeft <= 0) {
            cleanUpMap();
            setState(GameState.INIT);
        }

        return null;
    }

    // Called once, when the player joins for the first time.
    // public void playerJoinedForTheFirstTime(final Player player)
    // {
    //     GamePlayer gp = getGamePlayer(player);

    //     gp.hasJoinedBefore = true;
    //     gp.setName(player.getName());
    //     gp.setStartTime(new Date());
    //     gp.updateStatsName();

    //     scoreboard.addPlayer(player);

    //     if(debug)
    //         {
    //             if(debugStrings.size() > 0)
    //                 {
    //                     send(player, ChatColor.DARK_RED + " === DEBUG INFO ===");

    //                     for(String s : debugStrings)
    //                         {
    //                             send(player, " " + ChatColor.RED + s);
    //                         }
    //                 }
    //         }

    //     switch(state)
    //         {
    //         case INIT:
    //         case WAIT_FOR_PLAYERS:
    //         case COUNTDOWN_TO_START:
    //             gp.setPlayer();
    //             scoreboard.setPlayerScore(player, 0);
    //             break;
    //         default:
    //             if(gp.isPlayer())
    //                 {
    //                     scoreboard.setPlayerScore(player, 0);
    //                 }

    //             gp.setSpectator();
    //         }

    //     if(joinRandomStat == null)
    //         {
    //             joinRandomStat = getStatsJson(new Random(System.currentTimeMillis()).nextInt(8));
    //         }

    //     new BukkitRunnable()
    //     {
    //         @Override public void run()
    //         {
    //             send(player, " ");

    //             if (map != null) {
    //                 String credits = map.getCredits();

    //                 if(!credits.isEmpty())
    //                     send(player, ChatColor.GOLD + " Welcome to Colorfall!" + ChatColor.WHITE + " Map name: " + ChatColor.AQUA + mapID + ChatColor.WHITE + " - Made by: " + ChatColor.AQUA + credits);

    //                 send(player, " ");

    //                 sendJsonMessage(player, joinRandomStat);
    //             }
    //         }
    //     }.runTaskLater(this, 20 * 3);
    // }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    Object button(String chat, String tooltip, String command)
    {
        Map<String, Object> map = new HashMap<>();
        map.put("text", format(chat));

        Map<String, Object> map2 = new HashMap<>();
        map.put("clickEvent", map2);

        map2.put("action", "run_command");
        map2.put("value", command);

        map2 = new HashMap();
        map.put("hoverEvent", map2);
        map2.put("action", "show_text");
        map2.put("value", format(tooltip));

        return map;
    }

    // Called whenever a player joins. This could be after a player disconnect during a game, for instance.
    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        Player player = event.getPlayer();
        bossBar.addPlayer(player);
        GamePlayer gp = getGamePlayer(player);
        // if (!gp.hasJoinedBefore) {
        //     playerJoinedForTheFirstTime(player);
        // }

        gp.setDisconnectedTicks(0);

        scoreboard.addPlayer(player);

        if(gp.isSpectator())
            {
                gp.setSpectator();
                return;
            }

        switch(state)
            {
            case STARTED:
                if (!gp.isPlayer()) {
                    gp.setSpectator();
                }
                break;
            case INIT:
            case WAIT_FOR_PLAYERS:
            case COUNTDOWN_TO_START:
                // Someone joins in the early stages, we make sure they are locked in the right place.
                //gp.makeImmobile(player, gp.getSpawnLocation());
                break;
            default:
                // Join later and we make sure you are in the right state.
                // gp.makeMobile(player);
                // gp.setPlayer();
            }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event)
    {
        if(event.getCause() != TeleportCause.ENDER_PEARL)
            return;

        if(!map.isBlockWithinCuboid(event.getTo().getBlock()))
            event.setCancelled(true);
        else
            getGamePlayer(event.getPlayer()).addEnderpearl();
    }

    @EventHandler
    public void onProjectileThrownEvent(ProjectileLaunchEvent event)
    {
        if(event.getEntity() instanceof Snowball)
            {
                Snowball snowball = (Snowball)event.getEntity();
                if(snowball.getShooter() instanceof Player)
                    {
                        Player playerThrower = (Player)snowball.getShooter();
                        getGamePlayer(playerThrower).addSnowball();
                    }
            }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event)
    {
        if(!(event.getEntity() instanceof Player))
            return;

        if(event.getDamager() instanceof Snowball)
            {
                Snowball snowball = (Snowball)event.getDamager();
                if(snowball.getShooter() instanceof Player)
                    {
                        Player playerThrower = (Player)snowball.getShooter();
                        getGamePlayer(playerThrower).addSnowballHit();
                    }
            }

        Player player = (Player)event.getEntity();
        player.setHealth(20);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event)
    {
        if (event.getEntity() instanceof ArmorStand) {
            event.setCancelled(true);
            return;
        }

        if(!(event.getEntity() instanceof Player))
            return;

        Player player = (Player)event.getEntity();
        GamePlayer gp = getGamePlayer(player);

        // Ok, this isn't pretty. But..
        // It seems that when a player is teleported, any fall damage he is due to take is inflicted immediately. Even when falling into the void.
        // This peculiarity leads to the player dying twice, once by falling out of the world and then by taking fall damage.
        // So, to avoid double deaths I check if the player last died less than 500 ms ago.
        if((gp.getLastDeath() > 0 && System.currentTimeMillis() - gp.getLastDeath() <= 500) || gp.diedThisRound())
            {
                event.setCancelled(true);
                return;
            }

        if(event.getCause() == DamageCause.VOID)
            {
                event.setCancelled(true);
                player.setHealth(20);
                gp.died();

                if(roundState == RoundState.RUNNING || roundState == RoundState.REMOVING_BLOCKS || roundState == RoundState.RESTORING_BLOCKS)
                    {
                        player.setVelocity(new Vector().zero());
                        player.teleport(gp.getSpawnLocation());
                        player.setHealth(20.0);
                        player.setGameMode(GameMode.SPECTATOR);
                        //gp.makeImmobile(player, gp.getSpawnLocation());
                    }
                else
                    {
                        player.teleport(gp.getSpawnLocation());
                    }
            }
        else
            {
                player.setHealth(20);

                if(event.getCause() != DamageCause.ENTITY_ATTACK && event.getCause() != DamageCause.PROJECTILE)
                    event.setCancelled(true);
            }
    }

    private void giveStartingItems(Player player)
    {
        // Give feather.
        // ItemStack feather = new ItemStack(Material.FEATHER);
        // ItemMeta meta = feather.getItemMeta();
        // meta.setDisplayName("Color checker");

        // List<String> lore = new ArrayList<String>();
        // lore.add(ChatColor.DARK_AQUA + "Use this feather on a");
        // lore.add(ChatColor.DARK_AQUA + "colored block to check");
        // lore.add(ChatColor.DARK_AQUA + "the color of the block.");

        // meta.setLore(lore);

        // feather.setItemMeta(meta);

        // player.getInventory().setItem(1, feather);

        // Give one dye.
        player.getInventory().addItem(map.getDye());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command bcommand, String command, String[] args)
    {
        if (!(sender instanceof Player)) return true;
        Player player = (Player)sender;
        if(command.equalsIgnoreCase("ready") && state == GameState.WAIT_FOR_PLAYERS)
            {
                GamePlayer gp = getGamePlayer(player);
                gp.setReady(true);
                gp.setPlayer();
                scoreboard.setPlayerScore(player, 1);
                player.teleport(gp.getSpawnLocation());
                send(player, ChatColor.GREEN + " Marked as ready");
                getServer().dispatchCommand(getServer().getConsoleSender(), "ml add " + player.getName());
            }
        else if(command.equalsIgnoreCase("item") && args.length == 1 && player.isOp())
            {
                String key = args[0];
                ItemStack stack = powerups.get(key);

                if(args[0].equals("SpecialDye"))
                    {
                        ColorBlock cb = map.getRandomFromColorPool();

                        ItemStack newStack = new ItemStack(cb.blockData.getMaterial());
                        ItemMeta meta = newStack.getItemMeta();

                        meta.setLore(stack.getItemMeta().getLore());
                        meta.setDisplayName(stack.getItemMeta().getDisplayName());

                        newStack.setItemMeta(meta);

                        player.getInventory().addItem(newStack);
                    }
                else
                    {
                        player.getInventory().addItem(stack);
                    }
                send(player, " &eGiven item %s", key);
            }
        else if(command.equalsIgnoreCase("tp") && getGamePlayer(player).isSpectator())
            {
                if(args.length != 1)
                    {
                        send(player, " &cUsage: /tp <player>");
                        return true;
                    }
                String arg = args[0];

                for(Player target : getServer().getOnlinePlayers())
                    {
                        if(arg.equalsIgnoreCase(target.getName()))
                            {
                                player.teleport(target);
                                send(player, " &bTeleported to %s", target.getName());
                                return true;
                            }
                    }

                send(player, " &cPlayer not found: %s", arg);
                return true;
            }
        else if(command.equalsIgnoreCase("highscore") || command.equalsIgnoreCase("hi"))
            {
                int type = 0;

                if(args.length == 1)
                    {
                        try
                            {
                                type = Integer.parseInt(args[0]);
                            }
                        catch(NumberFormatException e)
                            {}
                    }

                String json = getStatsJson(type);

                sendJsonMessage(player, json);
            }
        else if(command.equalsIgnoreCase("stats"))
            {
                showStats(player, (args.length == 0 ? player.getName() : args[0]));
            }
        else
            {
                return false;
            }

        return true;
    }

    public void onPlayerDeath(Player player)
    {
        for(Player p : getServer().getOnlinePlayers())
            {
                if(!p.equals(player))
                    send(p, " " + ChatColor.RED + player.getName() + " had bad timing and lost a life.");
            }

        if(roundState == RoundState.RUNNING || roundState == RoundState.REMOVING_BLOCKS)
            send(player, ChatColor.RED + " You lost a life and are put in spectator mode until this round is over.");
    }

    public void onPlayerElimination(Player player)
    {
        getGamePlayer(player).setEndTime(new Date());

        // if(!debug)
        //     getGamePlayer(player).recordStats(moreThanOnePlayed, mapID);

        for(Player p : getServer().getOnlinePlayers())
            {
                showTitle(p, "", ChatColor.RED + player.getName() + " died and is out of the game");
            }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event)
    {
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event)
    {
        announce("&a%s", event.getBlock().getType());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event)
    {
        if (event.getHand() != EquipmentSlot.HAND) return;
        switch (event.getAction()) {
        case RIGHT_CLICK_BLOCK:
        case RIGHT_CLICK_AIR:
            break;
        default: return;
        }
        final Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) return;
        if(hand.getType() == Material.FEATHER && event.getAction() == Action.RIGHT_CLICK_BLOCK)
            {
                Block b = event.getClickedBlock();

                if(map.isColoredBlock(b))
                    {
                        Color color = Color.fromBlockData(b.getBlockData());
                        if(b.getType() != Material.AIR && b.getBlockData().equals(currentColor.blockData))
                            {
                                send(p, ChatColor.GREEN + " That block is " + color.niceName + ", and it is the right one!");
                            }
                        else
                            {
                                send(p, ChatColor.RED + " That block is " + color.niceName + ". That's not the right one!");
                            }
                    }
            }

        if(roundState == RoundState.RUNNING)
            {
                // Dyes.
                Color dyeColor = Color.fromDyeMaterial(hand.getType());
                if(dyeColor != null && event.getAction() == Action.RIGHT_CLICK_BLOCK && map.isColoredBlock(event.getClickedBlock()))
                    {
                        Block block = event.getClickedBlock();
                        Color blockColor = Color.fromBlockData(block.getBlockData());

                        // Don't bother if block is already same color as the dye.
                        if(blockColor == dyeColor)
                            return;

                        // Only register the original color once, in case a block is dyed multiple times.
                        if(!paintedBlocks.contains(event.getClickedBlock()))
                            {
                                event.getClickedBlock().setMetadata("org-color", new FixedMetadataValue(this, event.getClickedBlock().getBlockData()));
                                paintedBlocks.add(event.getClickedBlock());
                            }

                        block.setBlockData(dyeColor.stain(block.getBlockData()));

                        p.getWorld().playSound(event.getClickedBlock().getLocation(), Sound.ENTITY_SHEEP_SHEAR, SoundCategory.MASTER, 0.2f, 1f);

                        reduceItemInHand(p);
                        getGamePlayer(p).addDye();
                    }
                if(event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                    {
                        tryUseItemInHand(p);
                    }
            }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event)
    {
        event.setCancelled(true);
        tryUseItemInHand(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event)
    {
        event.setCancelled(true);
        tryUseItemInHand(event.getPlayer());
    }

    void tryUseItemInHand(Player p)
    {
        // The clock.
        if(p.getItemInHand().getType() == Material.CLOCK)
            {
                for(Player pp : getServer().getOnlinePlayers())
                    {
                        send(pp, " " + ChatColor.GOLD + p.getName() + " used a clock to extend the round!");
                    }

                currentRoundDuration += 100;

                // Allow pistons again since there is now at least 5 seconds left.
                disallowPistons = false;

                reduceItemInHand(p);
                getGamePlayer(p).addClock();
            }
        // The randomizer.
        else if(p.getItemInHand().getType() == Material.EMERALD)
            {
                if(disallowRandomize)
                    {
                        send(p, " " + ChatColor.RED + "You can't use the randomizer this late in the round!");
                    }
                else
                    {
                        if(randomizeCooldown <= 0)
                            {
                                randomizeCooldown = 5 * 20;

                                map.randomizeBlocks();
                                reduceItemInHand(p);
                                getGamePlayer(p).addRandomizer();

                                for(Player pp : getServer().getOnlinePlayers())
                                    {
                                        send(pp, " " + ChatColor.WHITE + p.getName() + " " + ChatColor.DARK_AQUA + "r" + ChatColor.DARK_PURPLE + "a" + ChatColor.GOLD + "n" + ChatColor.GREEN + "d" + ChatColor.AQUA + "o" + ChatColor.RED + "m" + ChatColor.WHITE + "i" + ChatColor.LIGHT_PURPLE + "z" + ChatColor.AQUA + "e" + ChatColor.GOLD + "d" + ChatColor.WHITE + " the colors!");

                                        pp.playSound(pp.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.2f, 1f);
                                    }
                            }
                        else
                            {
                                send(p, " " + ChatColor.RED + "Randomize is on cooldown for another " + (randomizeCooldown / 20) + " seconds.");
                            }
                    }
            }
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event)
    {
        if(disallowPistons)
            event.setCancelled(true);
    }

    @EventHandler
    public void onLeavesDecay(LeavesDecayEvent event)
    {
        event.setCancelled(true);
    }

    private void reduceItemInHand(Player player)
    {
        ItemStack item = player.getItemInHand();

        if(item.getAmount() <= 1)
            {
                player.setItemInHand(null);
            }
        else
            {
                item.setAmount(item.getAmount() - 1);
                player.setItemInHand(item);
            }
    }

    private GamePlayer getGamePlayer(Player player)
    {
        GamePlayer gp = gamePlayers.computeIfAbsent(player.getUniqueId(), u -> new GamePlayer(this, u));
        gp.setName(player.getName());
        if (player.getName().equals("Cavetale")) {
            gp.setSpectator();
        }
        return gp;
    }

    @EventHandler
    public void onPlayerSpawnLocation(PlayerSpawnLocationEvent event) {
        event.setSpawnLocation(getSpawnLocation(event.getPlayer()));
    }

    @EventHandler
    public void onProjectileCollide(ProjectileCollideEvent event) {
        Projectile proj = event.getEntity();
        if (!(proj instanceof Snowball)) return;
        Entity target = event.getCollidedWith();
        if (!(target instanceof Player)) return;
        Player victim = (Player) target;
        event.setCancelled(true);
        if (roundState != RoundState.RUNNING) return;
        Vector velo = proj.getVelocity().normalize().setY(0.25).normalize();
        victim.setVelocity(velo.multiply(3.0));
        victim.getWorld().spawnParticle(Particle.SNOWBALL, proj.getLocation(), 48, 0.2, 0.2, 0.2, 0.0);
        victim.getWorld().playSound(proj.getLocation(), Sound.BLOCK_SNOW_BREAK, SoundCategory.MASTER, 2.0f, 1.0f);
        if (proj.getShooter() instanceof Player) {
            Player launcher = (Player) proj.getShooter();
            launcher.playSound(launcher.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, SoundCategory.MASTER, 1.0f, 1.0f);
        }
    }

    public Location getSpawnLocation(Player player)
    {
        switch (state)
            {
            case INIT:
            case WAIT_FOR_PLAYERS:
            case COUNTDOWN_TO_START:
                return getGamePlayer(player).getSpawnLocation();
            default:
                return map.getWorld().getSpawnLocation();
            }
    }

    String getStatsJson(int type)
    {
        // 0: Top dog (wins, superior wins)
        // 1: Top painter (dyes)
        // 2: Top manipulators of time
        // 3: Top dislocators
        // 4: Top trigger happy
        // 5: Top randomizers
        // 6: Top investigators of the void
        // 7: Top contestants

        String json = "[";

        if(type == 0)
            {
                List<PlayerStats> list = PlayerStats.loadTopWinners(this);

                json += "{\"text\": \" §6»» §bColorfall top dogs §6««\\n\"}, ";

                int i = 0;
                for(PlayerStats obj : list)
                    {
                        if(i == 0)
                            json += "{\"text\": \" §3Current leader: \"}, ";
                        else
                            json += "{\"text\": \" §3Runner-up: \"}, ";

                        json += "{\"text\": \"§b" + obj.getName() + " §fwith §b" + obj.getGamesWon() + " §fwin" + (obj.getGamesWon() == 1 ? "" : "s") + "\"}, ";

                        if(obj.getSuperiorWins() > 0)
                            json += "{\"text\": \"§f, of which §b" + obj.getSuperiorWins() + " §f" + (obj.getSuperiorWins() == 1 ? "is a" : "are") + " §asuperior win" + (obj.getSuperiorWins() == 1 ? "" : "s") + "\"}, ";

                        json += "{\"text\": \"§f.\\n\"}, ";

                        i++;
                    }

                if(i == 0)
                    {
                        json += "{\"text\": \" §fNothing in this category yet. You can be the first ?\\n\"}, ";
                    }
            }
        else if(type == 1)
            {
                List<PlayerStats> list = PlayerStats.loadTopPainters(this);

                json += "{\"text\": \" §6»» §bColorfall top painters §6««\\n\"}, ";

                int i = 0;
                for(PlayerStats obj : list)
                    {
                        if(i == 0)
                            json += "{\"text\": \" §3Current leader: \"}, ";
                        else
                            json += "{\"text\": \" §3Runner-up: \"}, ";

                        json += "{\"text\": \"§b" + obj.getName() + " §fhas used §b" + obj.getDyesUsed() + " §fdye" + (obj.getDyesUsed() == 1 ? "" : "s") + ".\\n\"}, ";

                        i++;
                    }

                if(i == 0)
                    {
                        json += "{\"text\": \" §fNothing in this category yet. You can be the first ?\\n\"}, ";
                    }
            }
        else if(type == 2)
            {
                List<PlayerStats> list = PlayerStats.loadTopClockers(this);

                json += "{\"text\": \" §6»» §bColorfall top manipulators of time §6««\\n\"}, ";

                int i = 0;
                for(PlayerStats obj : list)
                    {
                        if(i == 0)
                            json += "{\"text\": \" §3Current leader: \"}, ";
                        else
                            json += "{\"text\": \" §3Runner-up: \"}, ";

                        json += "{\"text\": \"§b" + obj.getName() + " §fextended the time §b" + obj.getClocksUsed() + " §ftime" + (obj.getClocksUsed() == 1 ? "" : "s") + ".\\n\"}, ";

                        i++;
                    }

                if(i == 0)
                    {
                        json += "{\"text\": \" §fNothing in this category yet. You can be the first ?\\n\"}, ";
                    }
            }
        else if(type == 3)
            {
                List<PlayerStats> list = PlayerStats.loadTopPearlers(this);

                json += "{\"text\": \" §6»» §bColorfall top dislocators §6««\\n\"}, ";

                int i = 0;
                for(PlayerStats obj : list)
                    {
                        if(i == 0)
                            json += "{\"text\": \" §3Current leader: \"}, ";
                        else
                            json += "{\"text\": \" §3Runner-up: \"}, ";

                        json += "{\"text\": \"§b" + obj.getName() + " §fhas thrown §b" + obj.getEnderpearlsUsed() + " §fender pearl" + (obj.getEnderpearlsUsed() == 1 ? "" : "s") + ".\\n\"}, ";

                        i++;
                    }

                if(i == 0)
                    {
                        json += "{\"text\": \" §fNothing in this category yet. You can be the first ?\\n\"}, ";
                    }
            }
        else if(type == 4)
            {
                List<PlayerStats> list = PlayerStats.loadTopSnowballers(this);

                json += "{\"text\": \" §6»» §bColorfall top trigger happy players §6««\\n\"}, ";

                int i = 0;
                for(PlayerStats obj : list)
                    {
                        if(i == 0)
                            json += "{\"text\": \" §3Current leader: \"}, ";
                        else
                            json += "{\"text\": \" §3Runner-up: \"}, ";

                        json += "{\"text\": \"§b" + obj.getName() + " §fhas thrown §b" + obj.getSnowballsUsed() + " §fsnow ball" + (obj.getSnowballsUsed() == 1 ? "" : "s") + "\"}, ";

                        if(obj.getSnowballsHit() > 0)
                            json += "{\"text\": \"§f, of which §b" + obj.getSnowballsHit() + " §fhit their target\"}, ";

                        json += "{\"text\": \"§f.\\n\"}, ";

                        i++;
                    }

                if(i == 0)
                    {
                        json += "{\"text\": \" §fNothing in this category yet. You can be the first ?\\n\"}, ";
                    }
            }
        else if(type == 5)
            {
                List<PlayerStats> list = PlayerStats.loadTopRandomizers(this);

                json += "{\"text\": \" §6»» §bColorfall top randomizers §6««\\n\"}, ";

                int i = 0;
                for(PlayerStats obj : list)
                    {
                        if(i == 0)
                            json += "{\"text\": \" §3Current leader: \"}, ";
                        else
                            json += "{\"text\": \" §3Runner-up: \"}, ";

                        json += "{\"text\": \"§b" + obj.getName() + " §fhas used §b" + obj.getRandomizersUsed() + " §frandomizer" + (obj.getRandomizersUsed() == 1 ? "" : "s") + ".\\n\"}, ";

                        i++;
                    }

                if(i == 0)
                    {
                        json += "{\"text\": \" §fNothing in this category yet. You can be the first ?\\n\"}, ";
                    }
            }
        else if(type == 6)
            {
                List<PlayerStats> list = PlayerStats.loadTopDeaths(this);

                json += "{\"text\": \" §6»» §bColorfall top investigators of the Void §6««\\n\"}, ";

                int i = 0;
                for(PlayerStats obj : list)
                    {
                        if(i == 0)
                            json += "{\"text\": \" §3Current leader: \"}, ";
                        else
                            json += "{\"text\": \" §3Runner-up: \"}, ";

                        json += "{\"text\": \"§b" + obj.getName() + " §fdied §b" + obj.getDeaths() + " §ftime" + (obj.getDeaths() == 1 ? "" : "s") + ".\\n\"}, ";

                        i++;
                    }

                if(i == 0)
                    {
                        json += "{\"text\": \" §fNothing in this category yet. You can be the first ?\\n\"}, ";
                    }
            }
        else if(type == 7)
            {
                List<PlayerStats> list = PlayerStats.loadTopContestants(this);

                json += "{\"text\": \" §6»» §bColorfall top contestants §6««\\n\"}, ";

                int i = 0;
                for(PlayerStats obj : list)
                    {
                        if(i == 0)
                            json += "{\"text\": \" §3Current leader: \"}, ";
                        else
                            json += "{\"text\": \" §3Runner-up: \"}, ";

                        json += "{\"text\": \"§b" + obj.getName() + " §fhas played §b" + obj.getGamesPlayed() + " §fgame" + (obj.getGamesPlayed() == 1 ? "" : "s") + "\"}, ";

                        if(obj.getRoundsPlayed() > 0)
                            json += "{\"text\": \"§f, and a total of §b" + obj.getRoundsPlayed() + " §fround" + (obj.getRoundsPlayed() == 1 ? "" : "s") + "\"}, ";

                        json += "{\"text\": \"§f.\\n\"}, ";

                        i++;
                    }

                if(i == 0)
                    {
                        json += "{\"text\": \" §fNothing in this category yet. You can be the first ?\\n\"}, ";
                    }
            }

        json += "{\"text\": \"\\n §eOther stats:\\n\"}, ";

        if(type != 0)
            json += "{\"text\": \" §f[§bWins§f]\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/hi 0\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"§fSee who won the most games.\"}}, ";

        if(type != 6)
            json += "{\"text\": \" §f[§bDeaths§f]\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/hi 6\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"§fSee who died the most.\"}}, ";

        if(type != 7)
            json += "{\"text\": \" §f[§bContestants§f]\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/hi 7\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"§fSee who played the most games.\"}}, ";

        if(type != 1)
            json += "{\"text\": \" §f[§bPainters§f]\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/hi 1\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"§fSee who used the most dyes.\"}}, ";

        json += "{\"text\": \"\\n\"}, ";

        if(type != 2)
            json += "{\"text\": \" §f[§bClockers§f]\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/hi 2\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"§fSee who used the most clocks.\"}}, ";

        if(type != 3)
            json += "{\"text\": \" §f[§bPearlers§f]\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/hi 3\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"§fSee who used the most enderpearls.\"}}, ";

        if(type != 4)
            json += "{\"text\": \" §f[§bSnowballers§f]\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/hi 4\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"§fSee who used the most snowballs.\"}}, ";

        if(type != 5)
            json += "{\"text\": \" §f[§bRandomizers§f]\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/hi 5\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"§fSee who used the most randomizers.\"}}, ";

        json += "{\"text\": \"\\n §f[§6See your own stats§f]\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/stats\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"§fSee your personal stats.\"}}, ";

        json += "{\"text\": \" \\n\"} ";
        json += "] ";

        return json;
    }

    void showStats(Player player, String name)
    {
        PlayerStats stats = new PlayerStats();
        stats.loadOverview(this, name);

        if(stats.getGamesPlayed() > 0)
            {
                String json = "[";
                json += "{\"text\": \" §3§l§m   §3 Stats for §b" + name + " §3§l§m   \\n\"}, ";

                String who = player.getName().equalsIgnoreCase(name) ? "You have" : name + " has";

                json += "{\"text\": \" §f" + who + " played §b" + stats.getGamesPlayed() + " §fgame" + (stats.getGamesPlayed() == 1 ? "" : "s") + " of Colorfall.\\n\"}, ";
                json += "{\"text\": \" §6Notable stats:\\n\"}, ";
                json += "{\"text\": \" §b" + stats.getGamesWon() + " §fwin" + (stats.getGamesWon() == 1 ? "" : "s") + " §7/\"}, ";
                json += "{\"text\": \" §b" + stats.getSuperiorWins() + " §fsuperior win" + (stats.getSuperiorWins() == 1 ? "" : "s") + " §7/\"}, ";
                json += "{\"text\": \" §b" + stats.getDeaths() + " §fdeath" + (stats.getDeaths() == 1 ? "" : "s") + " §7/\"}, ";
                json += "{\"text\": \" §b" + stats.getRoundsPlayed() + " §fround" + (stats.getRoundsPlayed() == 1 ? "" : "s") + " played §7/\"}, ";
                json += "{\"text\": \" §b" + stats.getRoundsSurvived() + " §fround" + (stats.getRoundsSurvived() == 1 ? "" : "s") + " survived\\n\"}, ";

                json += "{\"text\": \" §6Powerups used:\\n\"}, ";
                json += "{\"text\": \" §b" + stats.getDyesUsed() + " §fdye" + (stats.getDyesUsed() == 1 ? "" : "s") + " §7/\"}, ";
                json += "{\"text\": \" §b" + stats.getRandomizersUsed() + " §frandomizer" + (stats.getRandomizersUsed() == 1 ? "" : "s") + " §7/\"}, ";
                json += "{\"text\": \" §b" + stats.getClocksUsed() + " §fclock" + (stats.getClocksUsed() == 1 ? "" : "s") + " §7/\"}, ";
                json += "{\"text\": \" §b" + stats.getEnderpearlsUsed() + " §fenderpearl" + (stats.getEnderpearlsUsed() == 1 ? "" : "s") + " §7/\"}, ";
                json += "{\"text\": \" §b" + stats.getSnowballsUsed() + " §fsnowball" + (stats.getSnowballsUsed() == 1 ? "" : "s") + " \\n\"}, ";

                json += "{\"text\": \" \\n\"} ";
                json += "] ";

                sendJsonMessage(player, json);
            }
        else
            {
                send(player, " No stats recorded for " + name);
            }
    }

    private boolean sendRaw(Player player, Object json)
    {
        return sendJsonMessage(player, JSONValue.toJSONString(json));
    }

    private boolean sendJsonMessage(Player player, String json)
    {
        if(player == null)
            return false;

        final CommandSender console = getServer().getConsoleSender();
        final String command = "minecraft:tellraw " + player.getName() + " " + json;

        getServer().dispatchCommand(console, command);

        return true;
    }

    @SuppressWarnings("unused")
    private void debug(Object o)
    {
        System.out.println(o);
    }

    static String format(String msg, Object... args){
        msg = ChatColor.translateAlternateColorCodes('&', msg);
        if (args.length > 0) msg = String.format(msg, args);
        return msg;
    }

    static void send(Player player, String msg, Object... args) {
        player.sendMessage(format(msg, args));
    }

    void sendActionBar(Player player, String msg) {
        player.sendActionBar(format(msg));
    }

    void showTitle(Player player, String title, String subtitle) {
        player.sendTitle(format(title), format(subtitle));
    }

    void showTitle(Player player, String title, String subtitle, int a, int b, int c) {
        player.sendTitle(format(title), format(subtitle), a, b, c);
    }

    void announce(String msg, Object... args) {
        for (Player player: getServer().getOnlinePlayers()) {
            send(player, msg, args);
        }
    }

    public void setState(GameState newState) {
        GameState oldState = state;
        if (newState == oldState) return;
        state = newState;
        onStateChange(oldState, newState);
    }

    void cleanUpMap() {
        if (map == null) return;
        map.clearHighlightBlocks();
        for (Player player : map.getWorld().getPlayers()) {
            player.teleport(getServer().getWorlds().get(0).getSpawnLocation());
        }
        File dir = map.getWorld().getWorldFolder();
        if (!getServer().unloadWorld(map.getWorld(), false)) {
            throw new IllegalStateException("Cannot unload world: " + map.getWorld().getName());
        }
        ColorfallLoader.deleteFiles(dir);
        map = null;
    }
}
