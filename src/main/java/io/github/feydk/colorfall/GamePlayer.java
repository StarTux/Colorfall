package io.github.feydk.colorfall;

import io.github.feydk.colorfall.util.Players;
import java.util.Date;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@RequiredArgsConstructor @Getter @Setter
public final class GamePlayer {
    private final ColorfallPlugin plugin;
    protected final ColorfallGame game;
    protected final UUID uuid;
    protected boolean hasJoinedBefore = false;
    protected PlayerType type;
    protected String name;
    protected boolean isAlive = true;
    protected long lastDeath;
    protected long disconnectedTics;
    protected boolean statsRecorded;
    protected boolean didPlay = false;
    protected boolean diedThisRound = false;
    protected Location spawnLocation;
    // Player stats and highscore stuff.
    protected boolean winner = false;
    protected Date startTime;
    protected Date endTime;
    protected int roundsPlayed = 0;
    protected int roundsSurvived = 0;
    protected int deaths = 0;
    protected int livesLeft;
    protected boolean superior = false;
    protected int dyesUsed = 0;
    protected int randomizersUsed = 0;
    protected int clocksUsed = 0;
    protected int enderpearlsUsed = 0;
    protected int snowballsUsed = 0;
    protected int snowballsHit = 0;
    protected Location immobileLocation;

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
        Player player = getPlayer();
        if (player != null) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        didPlay = true;
    }

    // Set player as spectator.
    public void setSpectator() {
        type = PlayerType.SPECTATOR;
        Player player = getPlayer();
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
        Player player = getPlayer();
        if (livesLeft == 0) {
            isAlive = false;
            // To avoid having spectators holding stuff in their hand.
            Players.clearInventory(player);
            setSpectator();
            game.onPlayerElimination(player);
        } else {
            game.onPlayerDeath(player);
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

    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }
}
