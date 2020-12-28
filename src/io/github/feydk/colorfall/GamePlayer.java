package io.github.feydk.colorfall;

import java.util.Date;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.RequiredArgsConstructor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@RequiredArgsConstructor @Getter @Setter
public final class GamePlayer {
    private final ColorfallPlugin plugin;
    private final UUID uuid;
    boolean hasJoinedBefore = false;
    private PlayerType type;
    private String name;
    private boolean isReady;
    private boolean isAlive = true;
    private long lastDeath;
    private long disconnectedTics;
    private boolean statsRecorded;
    private boolean didPlay = false;
    private boolean diedThisRound = false;
    private Location spawnLocation;
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

    public enum PlayerType {
        PLAYER,
        SPECTATOR
    }

    // Is player a player (still participating in the game)?
    public boolean isPlayer() {
        return type == PlayerType.PLAYER;
    }

    // Is player a spectator?
    public boolean isSpectator() {
        return type == PlayerType.SPECTATOR;
    }

    // Set player as participant.
    public void setPlayer() {
        type = PlayerType.PLAYER;
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        didPlay = true;
    }

    // Set player as spectator.
    public void setSpectator() {
        type = PlayerType.SPECTATOR;
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) {
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    public boolean diedThisRound() {
        return diedThisRound;
    }

    public void setDiedThisRound(boolean diedThisRound) {
        this.diedThisRound = diedThisRound;
    }

    // Set amount of lives. Note: should only be called once when the game starts.
    public void setLives(int lives) {
        this.livesLeft = lives;
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) {
            plugin.getScoreboard().setPlayerScore(player, lives);
        }
    }

    public boolean isAlive() {
        return isAlive;
    }

    // Register that the player died. If the player has used all his lives, he is set as spectator.
    public void died() {
        deaths++;
        // See onEntityDamage for why I keep track of this.
        lastDeath = System.currentTimeMillis();
        diedThisRound = true;
        if (livesLeft > 0) {
            livesLeft--;
        }
        Player player = plugin.getServer().getPlayer(uuid);
        if (livesLeft == 0) {
            isAlive = false;
            // To avoid having spectators holding stuff in their hand.
            player.getInventory().clear();
            setSpectator();
            plugin.getScoreboard().setPlayerEliminated(player);
            plugin.getGame().onPlayerElimination(player);
        } else {
            plugin.getScoreboard().setPlayerScore(player, livesLeft);
            plugin.getGame().onPlayerDeath(player);
        }
    }

    // Get the system tick of players last death.
    public long getLastDeath() {
        return lastDeath;
    }

    public long getDisconnectedTicks() {
        return disconnectedTics;
    }

    public void setDisconnectedTicks(long ticks) {
        disconnectedTics = ticks;
    }

    public void addRound() {
        roundsPlayed++;
    }

    public void addRandomizer() {
        randomizersUsed++;
    }

    public void addClock() {
        clocksUsed++;
    }

    public void addDye() {
        dyesUsed++;
    }

    public void addEnderpearl() {
        enderpearlsUsed++;
    }

    public void addSnowball() {
        snowballsUsed++;
    }

    public void addSnowballHit() {
        snowballsHit++;
    }

    public void setWinner() {
        winner = true;
    }

    public void setEndTime(Date end) {
        endTime = end;
    }

    public void setStartTime(Date start) {
        startTime = start;
    }

    void makeImmobile(Player player, Location location) {
        this.immobileLocation = location;
        if (!player.getLocation().getWorld().equals(location.getWorld()) || player.getLocation().distanceSquared(location) > 2.0) {
            player.teleport(location);
            plugin.getLogger().info("Teleported " + player.getName() + " to their spawn location");
        }
        player.setFlySpeed(0);
        player.setWalkSpeed(0);
    }

    void makeMobile(Player player) {
        this.immobileLocation = null;
        player.setWalkSpeed(.2f);
        player.setFlySpeed(.1f);
    }

    void onTick(Player player) {
        if (player == null) return;
        if (immobileLocation != null) {
            if (!player.getLocation().getWorld().equals(immobileLocation.getWorld()) || player.getLocation().distanceSquared(immobileLocation) > 2.0) {
                player.setFlySpeed(0);
                player.setWalkSpeed(0);
                player.teleport(immobileLocation);
                plugin.getLogger().info("Teleported " + player.getName() + " to their spawn location");
            }
        }
    }

    public int getLivesLeft() {
        return livesLeft;
    }
}
