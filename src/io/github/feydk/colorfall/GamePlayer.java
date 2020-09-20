package io.github.feydk.colorfall;

import java.sql.PreparedStatement;
import java.util.Date;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class GamePlayer
{
    private final ColorfallGame game;
    final UUID uuid;
    boolean hasJoinedBefore = false;
    private PlayerType type;
    private String name;
    private boolean isReady;
    private boolean isAlive = true;
    private long lastDeath;
    private long disconnectedTics;
    private Location spawnLocation;
    private boolean statsRecorded;
    private boolean didPlay = false;
    private boolean joinedAsSpectator = false;
    private boolean diedThisRound = false;

    // Player stats and highscore stuff.
    private boolean winner = false;
    private Date startTime;
    private Date endTime;
    private int roundsPlayed = 0;
    private int roundsSurvived = 0;
    private int deaths = 0;
    private int livesLeft;
    private boolean superior = false;
    private int dyesUsed = 0;
    private int randomizersUsed = 0;
    private int clocksUsed = 0;
    private int enderpearlsUsed = 0;
    private int snowballsUsed = 0;
    private int snowballsHit = 0;

    Location immobileLocation;

    static enum PlayerType
    {
        PLAYER,
        SPECTATOR
    }

    public GamePlayer(ColorfallGame game, UUID uuid)
    {
        this.game = game;
        this.uuid = uuid;
    }

    public boolean joinedAsSpectator()
    {
        return this.joinedAsSpectator;
    }

    public void setJoinedAsSpectator(boolean didhe)
    {
        joinedAsSpectator = didhe;
    }

    // Is player a player (still participating in the game)?
    public boolean isPlayer()
    {
        return type == PlayerType.PLAYER;
    }

    // Is player a spectator?
    public boolean isSpectator()
    {
        return type == PlayerType.SPECTATOR;
    }

    // Set player as participant.
    public void setPlayer()
    {
        type = PlayerType.PLAYER;
        Player player = game.getServer().getPlayer(uuid);
        if (player != null)
            player.setGameMode(GameMode.ADVENTURE);
        didPlay = true;
    }

    // Set player as spectator.
    public void setSpectator()
    {
        type = PlayerType.SPECTATOR;

        Player player = game.getServer().getPlayer(uuid);
        if (player != null)
            player.setGameMode(GameMode.SPECTATOR);
    }

    public boolean diedThisRound()
    {
        return diedThisRound;
    }

    public void setDiedThisRound(boolean diedThisRound)
    {
        this.diedThisRound = diedThisRound;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

    public boolean isReady()
    {
        return isReady;
    }

    public void setReady(boolean ready)
    {
        isReady = ready;
    }

    public Location getSpawnLocation()
    {
        if(spawnLocation != null)
            return spawnLocation;

        if (game.getMap() != null)
            return game.getMap().dealSpawnLocation();

        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    // Set amount of lives. Note: should only be called once when the game starts.
    public void setLives(int lives)
    {
        this.livesLeft = lives;

        Player player = game.getServer().getPlayer(uuid);
        if (player != null)
            game.getScoreboard().setPlayerScore(player, lives);
    }

    public boolean isAlive()
    {
        return isAlive;
    }

    // Register that the player died. If the player has used all his lives, he is set as spectator.
    public void died()
    {
        deaths++;

        // See onEntityDamage for why I keep track of this.
        lastDeath = System.currentTimeMillis();
        diedThisRound = true;

        if(livesLeft > 0)
            livesLeft--;

        Player player = game.getServer().getPlayer(uuid);

        if(livesLeft == 0)
            {
                isAlive = false;

                // To avoid having spectators holding stuff in their hand.
                player.getInventory().clear();

                setSpectator();

                game.getScoreboard().setPlayerEliminated(player);

                game.onPlayerElimination(player);
            }
        else
            {
                game.getScoreboard().setPlayerScore(player, livesLeft);

                game.onPlayerDeath(player);
            }
    }

    // Get the system tick of players last death.
    public long getLastDeath()
    {
        return lastDeath;
    }

    public long getDisconnectedTicks()
    {
        return disconnectedTics;
    }

    public void setDisconnectedTicks(long ticks)
    {
        disconnectedTics = ticks;
    }

    public void addRound()
    {
        roundsPlayed++;
    }

    public void addRandomizer()
    {
        randomizersUsed++;
    }

    public void addClock()
    {
        clocksUsed++;
    }

    public void addDye()
    {
        dyesUsed++;
    }

    public void addEnderpearl()
    {
        enderpearlsUsed++;
    }

    public void addSnowball()
    {
        snowballsUsed++;
    }

    public void addSnowballHit()
    {
        snowballsHit++;
    }

    public void setWinner()
    {
        winner = true;
    }

    public void setEndTime(Date end)
    {
        endTime = end;
    }

    public void setStartTime(Date start)
    {
        startTime = start;
    }

    public void updateStatsName()
    {
        String sql = "update `colorfall_playerstats` set `player_name` = ? where `player_uuid` = ?";

        try (PreparedStatement update = game.db.getConnection().prepareStatement(sql))
            {
                update.setString(1, getName());
                update.setString(2, this.uuid.toString());
                update.executeUpdate();
            }
        catch(Exception e)
            {
                e.printStackTrace();
            }
    }

    @SuppressWarnings("incomplete-switch")
    public void recordStats(boolean moreThanOnePlayed, String mapId)
    {
        switch(game.state)
            {
            case INIT:
            case WAIT_FOR_PLAYERS:
                return;
            }

        if(!didPlay)
            return;

        if(statsRecorded)
            return;

        if(endTime == null)
            endTime = new Date();

        roundsSurvived = roundsPlayed - deaths;
        superior = deaths == 0;

        final String sql =
            "insert into `colorfall_playerstats` (" +
            " `game_uuid`, `player_uuid`, `player_name`, `start_time`, `end_time`, `rounds_played`, `rounds_survived`, `deaths`, `lives_left`, `superior_win`, `dyes_used`, `randomizers_used`, `clocks_used`, `enderpearls_used`, `snowballs_used`, `snowballs_hit`, `winner`, `sp_game`, `map_id`" +
            ") values (" +
            " ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?" +
            ")";

        try (PreparedStatement update = game.db.getConnection().prepareStatement(sql))
            {
                update.setString(1, game.gameUuid.toString());
                update.setString(2, uuid.toString());
                update.setString(3, name);
                update.setTimestamp(4, new java.sql.Timestamp(startTime.getTime()));
                update.setTimestamp(5, new java.sql.Timestamp(endTime.getTime()));
                update.setInt(6, roundsPlayed);
                update.setInt(7, roundsSurvived);
                update.setInt(8, deaths);
                update.setInt(9, livesLeft);
                update.setBoolean(10, superior);
                update.setInt(11, dyesUsed);
                update.setInt(12, randomizersUsed);
                update.setInt(13, clocksUsed);
                update.setInt(14, enderpearlsUsed);
                update.setInt(15, snowballsUsed);
                update.setInt(16, snowballsHit);
                update.setBoolean(17, winner);
                update.setBoolean(18, !moreThanOnePlayed);
                update.setString(19, mapId);
                update.executeUpdate();

                game.getLogger().info("Stored player stats of " + name);
            }
        catch(Exception e)
            {
                e.printStackTrace();
            }

        statsRecorded = true;
    }

    void makeImmobile(Player player, Location location)
    {
        this.immobileLocation = location;
        if(!player.getLocation().getWorld().equals(location.getWorld()) || player.getLocation().distanceSquared(location) > 2.0)
            {
                player.teleport(location);
                game.getLogger().info("Teleported " + player.getName() + " to their spawn location");
            }
        player.setFlySpeed(0);
        player.setWalkSpeed(0);
    }

    void makeMobile(Player player)
    {
        this.immobileLocation = null;
        player.setWalkSpeed(.2f);
        player.setFlySpeed(.1f);
    }

    void onTick(Player player) {
        if (player == null) return;
        if (immobileLocation != null) {
            if(!player.getLocation().getWorld().equals(immobileLocation.getWorld()) || player.getLocation().distanceSquared(immobileLocation) > 2.0)
                {
                    player.setFlySpeed(0);
                    player.setWalkSpeed(0);
                    player.teleport(immobileLocation);
                    game.getLogger().info("Teleported " + player.getName() + " to their spawn location");
                }
        }
    }

    public int getLivesLeft() {
        return livesLeft;
    }
}
