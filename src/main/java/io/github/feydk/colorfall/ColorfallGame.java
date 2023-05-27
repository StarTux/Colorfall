package io.github.feydk.colorfall;

import com.cavetale.core.event.minigame.MinigameFlag;
import com.cavetale.core.event.minigame.MinigameMatchCompleteEvent;
import com.cavetale.core.event.minigame.MinigameMatchType;
import com.cavetale.core.font.VanillaItems;
import com.cavetale.mytems.Mytems;
import io.github.feydk.colorfall.util.Players;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;
import static net.kyori.adventure.title.Title.Times.times;

@RequiredArgsConstructor @Getter
public final class ColorfallGame {
    protected final ColorfallPlugin plugin;
    // Stuff for keeping track of the game loop and ticks.
    protected GameState state = GameState.INIT;
    protected RoundState roundState;
    protected BukkitRunnable task;
    protected long ticks;
    protected long stateTicks;
    protected long roundTicks;
    protected long winTicks = -1;
    protected boolean disallowRandomize;
    protected boolean disallowPistons;
    protected long randomizeCooldown = 0;
    protected String joinRandomStat;
    protected GameMap gameMap;
    // Level config, sent from the framework.
    protected String mapID = "Classic";
    protected boolean debug = false;
    // Debug stuff.
    protected final List<String> debugStrings = new ArrayList<>();
    protected boolean denyStart = false;
    protected boolean moreThanOnePlayed;
    protected GamePlayer winner;
    protected int currentRoundIdx;
    protected Round currentRound;
    protected long currentRoundDuration;
    protected boolean currentRoundRandomized;
    protected BlockData currentColor;
    protected List<Block> paintedBlocks = new ArrayList<Block>();
    protected boolean obsolete = false;
    @Setter protected boolean test = false;
    protected long secondsLeft; // scoreboard

    void enable() {
    }

    void disable() {
        cleanUpMap();
    }

    // Find the config that belongs to this round. I.e. if round is 2 and we have round configs for 1 and 20, we use the config for round 1.
    private Round getRound(int round) {
        if (currentRound == null) {
            Round found = null;
            for (Entry<Integer, Round> entry : plugin.getRounds().entrySet()) {
                if (round >= entry.getKey()) {
                    found = entry.getValue();
                }
            }
            if (found != null) {
                Round r = found.copy();
                // Determine if this round will have pvp enabled.
                double number = Math.random() * 100;
                if (number - r.getPvpChance() <= 0) {
                    r.setPvp(true);
                }
                // Determine if this round will have randomize enabled.
                number = Math.random() * 100;
                if (number - r.getRandomizeChance() <= 0) {
                    r.setRandomize(true);
                }
                currentRound = r;
            }
        }
        return currentRound;
    }

    protected void tick() {
        ticks++;
        GameState newState = null;
        if (state == GameState.STARTED) {
            // Check if only one player is left.
            int aliveCount = 0;
            GamePlayer survivor = null;
            for (Player player : Bukkit.getOnlinePlayers()) {
                GamePlayer gp = plugin.getGamePlayer(player);
                if (gp.isAlive() && !gp.isSpectator()) {
                    survivor = gp;
                    aliveCount += 1;
                }
            }
            // There will only be picked a winner if there were
            // more than one player playing. Meaning that a SP
            // game shouldn't be rewarded with a win.
            if ((!test && aliveCount == 1) && survivor != null && moreThanOnePlayed) {
                // Consider this scenario: 2 players left
                // alive, both with 1 life left.  Both of them
                // falls about at the same time, but one
                // reaches the void slightly before the other.
                // This should be declared a draw, but without
                // the code below, whoever reaches the void
                // last will win.  So I'm trying to prevent
                // that by waiting a few secs, then see if the
                // 'winner' is actually still alive.
                if (winTicks == -1) {
                    winTicks = 0;
                } else {
                    winTicks++;
                    if (winTicks >= 80) {
                        if (survivor.isAlive()) {
                            winner = survivor;
                            survivor.setWinner();
                            survivor.setEndTime(new Date());
                            newState = GameState.END;
                            if (plugin.saveState.event) {
                                List<String> titles = List.of("Colorful",
                                                              "Technicolor",
                                                              "Prismatic",
                                                              "Pastel",
                                                              "Spectral");
                                String cmd = "titles unlockset " + winner.getName() + " " + String.join(" ", titles);
                                plugin.getLogger().info("Running command: " + cmd);
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                                plugin.saveState.addScore(winner.uuid, 10);
                                plugin.computeHighscore();
                            }
                        }
                    }
                }
            } else if (aliveCount == 0) {
                winner = null;
                newState = GameState.END;
            }
            if (randomizeCooldown > 0) {
                randomizeCooldown--;
            }
        }
        // Check for disconnects.
        for (GamePlayer gp : new ArrayList<>(plugin.getGamePlayers().values())) {
            if (!gp.isPlayer()) continue;
            Player player = Bukkit.getPlayer(gp.getUuid());
            if (player != null) gp.onTick(player);
            if (player == null) {
                // Kick players who disconnect too long.
                long discTicks = gp.getDisconnectedTicks();
                if (discTicks > plugin.getDisconnectLimit() * 20) {
                    plugin.getLogger().info("Kicking " + gp.getName() + " because they were disconnected too long");
                    gp.setSpectator();
                    plugin.getGamePlayers().remove(gp.getUuid());
                }
                gp.setDisconnectedTicks(discTicks + 1);
            }
        }
        if (newState == null) {
            newState = tickState(state);
        }
        if (newState != null && state != newState) {
            onStateChange(state, newState);
            state = newState;
        }
    }

    void onStateChange(GameState oldState, GameState newState) {
        stateTicks = 0;
        switch (newState) {
        case INIT:
            break;
        case WAIT_FOR_PLAYERS:
            break;
        case COUNTDOWN_TO_START:
            // Once the countdown starts, remove everyone who disconnected.
            for (GamePlayer gp: plugin.getGamePlayers().values()) {
                Player player = Bukkit.getPlayer(gp.getUuid());
                if (player == null) {
                    gp.setSpectator();
                }
                if (gp.isPlayer()) {
                    gp.setLives(plugin.getLives());
                }
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isPermissionSet("group.streamer") && player.hasPermission("group.streamer")) continue;
                GamePlayer gp = plugin.getGamePlayer(player);
                player.teleport(gp.getSpawnLocation());
                Players.clearInventory(player);
                gp.setPlayer();
                player.setWalkSpeed(0.0f);
            }
            break;
        case STARTED:
            int count = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isPermissionSet("group.streamer") && player.hasPermission("group.streamer")) continue;
                giveStartingItems(player);
                GamePlayer gp = plugin.getGamePlayer(player);
                player.setWalkSpeed(0.2f);
                gp.setStartTime(new Date());
                player.playSound(player.getEyeLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.MASTER, 1f, 1f);
                if (plugin.saveState.event) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
                }
                count++;
            }
            moreThanOnePlayed = count > 1;
            break;
        case END:
            do {
                MinigameMatchCompleteEvent mmcEvent = new MinigameMatchCompleteEvent(MinigameMatchType.COLORFALL);
                if (plugin.saveState.event) mmcEvent.addFlags(MinigameFlag.EVENT);
                for (GamePlayer gp : plugin.getGamePlayers().values()) {
                    if (gp.isDidPlay()) mmcEvent.addPlayerUuid(gp.uuid);
                }
                if (winner != null) {
                    mmcEvent.addWinnerUuid(winner.uuid);
                }
                mmcEvent.callEvent();
            } while (false);
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.getGamePlayer(player).setSpectator();
                player.playSound(player.getEyeLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, SoundCategory.MASTER, 1f, 1f);
            }
            // Restore the map if this state was entered in the removing blocks round state.
            if (roundState == RoundState.REMOVING_BLOCKS) {
                gameMap.restoreBlocks(paintedBlocks);
            }
        default:
            break;
        }
    }

    private void onRoundStateChange(RoundState oldState, RoundState newState) {
        roundTicks = 0;
        World world = gameMap.getWorld();
        switch (newState) {
            // We started a new round.
        case RUNNING:
            paintedBlocks.clear();
            currentRoundRandomized = false;
            currentRound = null;
            currentRoundIdx++;
            setColorForRound();
            disallowRandomize = false;
            Round round = getRound(currentRoundIdx);
            currentRoundDuration = round.getDuration();
            // If single player game not in debug mode, disable pvp.
            if (!moreThanOnePlayed && !debug) {
                world.setPVP(false);
            } else {
                world.setPVP(round.getPvp());
            }
            // Do this again to make sure. It seems 1.9 changed this somehow.
            world.setWeatherDuration(Integer.MAX_VALUE);
            world.setStorm(false);
            List<Block> blocks = new ArrayList<>(gameMap.getReplacedBlocks());
            Collections.shuffle(blocks);
            Iterator<Block> blocksIter = blocks.iterator();
            List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
            Collections.shuffle(players);
            for (Player player : players) {
                GamePlayer gp = plugin.getGamePlayer(player);
                if (gp.isAlive() && gp.isPlayer()) {
                    gp.addRound();
                    gp.setDiedThisRound(false);
                    // Hand out powerups.
                    List<ItemStack> powerups = round.getDistributedPowerups();
                    for (ItemStack stack : powerups) {
                        if (blocksIter.hasNext()) {
                            Block block = blocksIter.next();
                            Location location = block.getLocation().add(0.5, 1.01, 0.5);
                            Item item = location.getWorld().dropItem(location, stack);
                            item.setGlowing(true);
                            item.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                        } else {
                            player.getInventory().addItem(stack.clone());
                        }
                    }
                    if (gp.isPlayer() && player.getGameMode() == GameMode.SPECTATOR) {
                        player.teleport(gp.getSpawnLocation());
                        gp.setPlayer();
                    }
                }
                // Announce pvp and color.
            }
            break;
            // Round time is over, remove blocks.
        case REMOVING_BLOCKS:
            world.setPVP(false);
            gameMap.removeBlocks(currentColor);
            for (GamePlayer gp : plugin.getGamePlayers().values()) {
                if (!gp.isPlayer()) continue;
                Player player = Bukkit.getPlayer(gp.getUuid());
                if (player == null) continue;
                Block playerBlock = player.getLocation().getBlock();
                if (!gameMap.isBlockWithinCuboid(playerBlock)) {
                    if (((LivingEntity) player).isOnGround() || player.isSwimming() || playerBlock.isLiquid()) {
                        player.teleport(gp.getSpawnLocation());
                    }
                }
            }
            break;
            // Restore blocks.
        case RESTORING_BLOCKS:
            gameMap.removeEnderPearls();
            gameMap.restoreBlocks(paintedBlocks);
            disallowPistons = false;
            break;
            // Round is over, wait for next round.
        case OVER:
            break;
        default:
            break;
        }
    }

    private void setColorForRound() {
        BlockData pick;
        // Pick a color for this round. Which has to be different than the one from the previous round.
        do {
            pick = gameMap.getRandomFromColorPool();
            // do nothing
        } while (pick.equals(currentColor));
        currentColor = pick;
    }

    private GameState tickState(GameState theState) {
        long theTicks = this.stateTicks++;
        switch (theState) {
        case INIT: return tickInit(theTicks);
        case WAIT_FOR_PLAYERS: return tickWaitForPlayers(theTicks);
        case COUNTDOWN_TO_START: return tickCountdownToStart(theTicks);
        case STARTED: return tickStarted(theTicks);
        case END: return tickEnd(theTicks);
        default: return null;
        }
    }

    GameState tickInit(long theTicks) {
        return null;
    }

    public void bringAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            GamePlayer gp = plugin.getGamePlayer(player);
            gp.setPlayer();
            Location loc = gameMap.dealSpawnLocation();
            gp.setSpawnLocation(loc);
            player.teleport(loc, TeleportCause.PLUGIN);
        }
    }

    GameState tickWaitForPlayers(long theTicks) {
        return null;
    }

    GameState tickCountdownToStart(long theTicks) {
        long timeLeft = (plugin.getCountdownToStartDuration() * 20) - theTicks;

        // Every second..
        if (timeLeft % 20L == 0) {
            long seconds = timeLeft / 20;
            this.secondsLeft = seconds;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (seconds == 0) {
                    player.showTitle(Title.title(text("Go!", GREEN),
                                                 empty(),
                                                 times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)));
                    player.playSound(player.getEyeLocation(), Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.MASTER, 0.2f, 1f);
                } else if (seconds == plugin.getCountdownToStartDuration()) {
                    player.showTitle(Title.title(text("Get ready!", GREEN),
                                                 text("Game starts in " + plugin.getCountdownToStartDuration() + " seconds",
                                                      GREEN)));
                    player.sendMessage(text(" Game starts in " + seconds + " seconds", AQUA));
                } else {
                    player.showTitle(Title.title(text("Get ready!", GREEN),
                                                 text(seconds, GREEN)));
                    player.playNote(player.getEyeLocation(), Instrument.PIANO, new Note((int) seconds));
                }
            }
        }
        if (timeLeft <= 0) return GameState.STARTED;
        return null;
    }

    GameState tickStarted(long theTicks) {
        RoundState newState = null;
        if (denyStart) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(text(" Not starting game, due to missing configuration.", RED));
            }
            return GameState.END;
        }
        if (roundState == null) {
            newState = RoundState.RUNNING;
        }
        roundTicks++;
        long timeLeft = (plugin.getStartedDuration() * 20) - theTicks;
        long roundTimeLeft = 0;
        if (roundState == RoundState.RUNNING) {
            roundTimeLeft = currentRoundDuration - roundTicks;
        } else if (roundState == RoundState.REMOVING_BLOCKS) {
            roundTimeLeft = 60 - roundTicks; // 3 seconds
        } else if (roundState == RoundState.RESTORING_BLOCKS) {
            roundTimeLeft = 20 - roundTicks;
        } else if (roundState == RoundState.OVER) {
            roundTimeLeft = 80 - roundTicks;
        }
        this.secondsLeft = (roundTimeLeft - 1) / 20 + 1;
        World world = gameMap.getWorld();
        if (roundState == RoundState.RUNNING && roundTimeLeft % 20L == 0) {
            Round round = getRound(currentRoundIdx);
            Color color = Color.fromBlockData(currentColor);
            plugin.getBossBar().name(text(color.niceName, color.toTextColor(), BOLD));
            plugin.getBossBar().progress(currentRoundDuration == 0.0f
                                         ? 0.0f
                                         : (float) roundTimeLeft / (float) currentRoundDuration);
            long seconds = roundTimeLeft / 20;
            if (seconds <= 3) disallowRandomize = true;
            for (Player player : Bukkit.getOnlinePlayers()) {
                // Countdown 3 seconds before round ends.
                if (seconds > 0 && seconds <= 3) {
                    player.showTitle(Title.title(empty(),
                                                 text(seconds, RED),
                                                 times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)));
                    player.playNote(player.getEyeLocation(), Instrument.PIANO, new Note((int) seconds));
                    if (seconds <= 2) disallowPistons = true;
                } else if (seconds == 0) {
                    // Reset title so we don't have the '1' from the countdown hanging too long.
                    player.resetTitle();
                }
            }
            // Show/refresh particle effect above the blocks.
            //gameMap.animateBlocks(currentColor);
            gameMap.highlightBlocks(currentColor);
            gameMap.animateBlocks(currentColor);
            // Handle randomize events.
            if (round.getRandomize() && !currentRoundRandomized) {
                // Fire this about 2 seconds before we're half way through the round, but no later than 2 seconds after half way.
                // Note: the 2 seconds works with 15 second rounds. Should probably be made more dynamic or configurable.
                boolean doRandomize = roundTimeLeft - 40 <= Math.round(currentRoundDuration / 2)
                    && roundTimeLeft + 40 >= Math.round(currentRoundDuration / 2)
                    && randomizeCooldown <= 0;
                if (doRandomize) {
                    gameMap.randomizeBlocks();
                    gameMap.highlightBlocks(currentColor);
                    currentRoundRandomized = true;
                    randomizeCooldown = 5 * 20;
                    Component title = join(noSeparators(),
                                           text("R", DARK_AQUA),
                                           text("a", DARK_PURPLE),
                                           text("n", GOLD),
                                           text("d", GREEN),
                                           text("o", AQUA),
                                           text("m", RED),
                                           text("i", WHITE),
                                           text("z", LIGHT_PURPLE),
                                           text("e", AQUA),
                                           text("d", GOLD),
                                           text("!", WHITE));
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.showTitle(Title.title(empty(), title));
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.2f, 1f);
                    }
                }
            }
            if (roundTimeLeft <= 0) {
                gameMap.clearHighlightBlocks();
                newState = RoundState.REMOVING_BLOCKS;
            }
        }
        if (roundState == RoundState.REMOVING_BLOCKS && roundTimeLeft <= 0) {
            newState = RoundState.RESTORING_BLOCKS;
        }
        if (roundState == RoundState.RESTORING_BLOCKS && roundTimeLeft <= 0) {
            newState = RoundState.OVER;
        }
        if (roundState == RoundState.OVER && roundTimeLeft % 20L == 0) {
            long seconds = roundTimeLeft / 20;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (seconds <= 0) {
                    player.playSound(player.getEyeLocation(), Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.MASTER, 0.2f, 1f);
                } else {
                    player.showTitle(Title.title(text(seconds, GREEN),
                                                 text("Round " + (currentRoundIdx + 1), GREEN),
                                                 times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)));
                    player.playNote(player.getEyeLocation(), Instrument.PIANO, new Note((int) seconds));
                }
            }
            if (roundTimeLeft <= 0) {
                newState = RoundState.RUNNING;
            }
        }
        if (newState != null && roundState != newState) {
            //plugin.getLogger().info("Entering round state: " + newState);
            onRoundStateChange(roundState, newState);
            roundState = newState;
        }
        if (plugin.getStartedDuration() > 0 && timeLeft <= 0) {
            return GameState.END;
        }
        return null;
    }

    GameState tickEnd(long theTicks) {
        long timeLeft = (plugin.getEndDuration() * 20) - theTicks;
        this.secondsLeft = (timeLeft - 1) / 20  + 1;
        // Every 5 seconds, show/refresh the winner title announcement.
        if (timeLeft % (20 * 5) == 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (winner != null) {
                    player.showTitle(Title.title(text(winner.getName(), GREEN),
                                                 text("Wins the Game!", GREEN)));
                } else {
                    player.showTitle(Title.title(text("Draw!", RED),
                                                 text("Nobody wins", RED)));
                }
            }
        }
        if (timeLeft <= 0) {
            obsolete = true;
        }
        return null;
    }

    private void giveStartingItems(Player player) {
        // Give one dye.
        player.getInventory().addItem(gameMap.getDye());
    }

    public void onPlayerDeath(Player player) {
        Component msg = join(noSeparators(),
                             newline(),
                             space(), Mytems.SKELETON_FACE.component,
                             text(player.getName() + " had bad timing and lost a life.", RED),
                             newline());
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(player)) {
                p.sendMessage(msg);
            }
        }
        if (roundState == RoundState.RUNNING || roundState == RoundState.REMOVING_BLOCKS) {
            player.sendMessage(text(" You lost a life and are put in spectator mode until this round is over.",
                                    RED));
        }
    }

    public void onPlayerElimination(Player player) {
        plugin.getGamePlayer(player).setEndTime(new Date());
        for (Player p : Bukkit.getOnlinePlayers()) {
            player.showTitle(Title.title(empty(),
                                         text(player.getName() + " died and is out of the game", RED)));
        }
    }

    public boolean tryUseItemInHand(Player p) {
        ItemStack itemInHand = p.getInventory().getItemInMainHand();
        if (itemInHand == null || itemInHand.getType() == Material.AIR) return false;
        // The clock.
        if (itemInHand.getType() == Material.CLOCK) {
            for (Player pp : Bukkit.getOnlinePlayers()) {
                pp.sendMessage(join(noSeparators(),
                                    newline(),
                                    space(), VanillaItems.CLOCK.component,
                                    text(p.getName() + " used a clock to extend the round!", GOLD),
                                    newline()));
            }
            currentRoundDuration += 100;
            // Allow pistons again since there is now at least 5 seconds left.
            disallowPistons = false;
            itemInHand.subtract(1);
            plugin.getGamePlayer(p).addClock();
            if (plugin.saveState.event) {
                plugin.saveState.addScore(p.getUniqueId(), 1);
                plugin.computeHighscore();
            }
            return true;
        } else if (itemInHand.getType() == Material.EMERALD) {
            if (disallowRandomize) {
                p.sendMessage(text(" " + "You can't use the randomizer this late in the round!", RED));
            } else {
                if (randomizeCooldown <= 0) {
                    randomizeCooldown = 5 * 20;
                    gameMap.randomizeBlocks();
                    gameMap.highlightBlocks(currentColor);
                    itemInHand.subtract(1);
                    plugin.getGamePlayer(p).addRandomizer();
                    if (plugin.saveState.event) {
                        plugin.saveState.addScore(p.getUniqueId(), 1);
                        plugin.computeHighscore();
                    }
                    Component message = join(noSeparators(),
                                             newline(),
                                             space(), VanillaItems.EMERALD.component,
                                             text(p.getName() + " "),
                                             text("r", DARK_AQUA),
                                             text("a", DARK_PURPLE),
                                             text("n", GOLD),
                                             text("d", GREEN),
                                             text("o", AQUA),
                                             text("m", RED),
                                             text("i", WHITE),
                                             text("z", LIGHT_PURPLE),
                                             text("e", AQUA),
                                             text("d", GOLD),
                                             text(" the colors!", WHITE),
                                             newline());
                    for (Player pp : Bukkit.getOnlinePlayers()) {
                        pp.sendMessage(message);
                        pp.playSound(pp.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.2f, 1f);
                    }
                } else {
                    p.sendMessage(text(" " + "Randomize is on cooldown for another " + (randomizeCooldown / 20) + " seconds.",
                                       RED));
                }
            }
            return true;
        }
        return false;
    }

    public Location getSpawnLocation(Player player) {
        switch (state) {
        case INIT:
        case WAIT_FOR_PLAYERS:
        case COUNTDOWN_TO_START:
            return plugin.getGamePlayer(player).getSpawnLocation();
        default:
            return gameMap.getWorld().getSpawnLocation();
        }
    }

    public void setState(GameState newState) {
        GameState oldState = state;
        if (newState == oldState) return;
        state = newState;
        onStateChange(oldState, newState);
    }

    void cleanUpMap() {
        if (gameMap == null) return;
        gameMap.cleanUp();
        gameMap = null;
    }

    public void loadMap(String worldName) {
        plugin.getLogger().info("Loading world: " + worldName);
        World world = ColorfallLoader.loadWorld(plugin, worldName);
        world.setDifficulty(Difficulty.HARD);
        world.setPVP(false);
        world.setGameRule(GameRule.DO_TILE_DROPS, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setWeatherDuration(Integer.MAX_VALUE);
        world.setStorm(false);
        WorldBorder worldBorder = world.getWorldBorder();
        worldBorder.setSize(8192);
        gameMap = new GameMap(worldName, this, world);
        gameMap.process();
        if (gameMap.getStartingTime() == -1) {
            world.setTime(1000L);
        } else {
            world.setTime(gameMap.getStartingTime());
        }
        if (gameMap.getLockTime()) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        } else {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        }
        for (String debugString : debugStrings) {
            plugin.getLogger().warning(debugString);
        }
    }

    /**
     * @return true if the event shall be cancelled, false otherwise.
     */
    public boolean onPlayerRightClick(Player player, ItemStack hand, Block block) {
        if (hand == null || hand.getType() == Material.AIR) return false;
        if (hand.getType() == Material.FEATHER) {
            if (plugin.getGame().getGameMap().isColoredBlock(block)) {
                Color color = Color.fromBlockData(block.getBlockData());
                if (block.getType() != Material.AIR && block.getBlockData().equals(currentColor)) {
                    player.sendMessage(text(" That block is " + color.niceName + ", and it is the right one!", GREEN));
                } else {
                    player.sendMessage(text(" That block is " + color.niceName + ". That's not the right one!", RED));
                }
            }
            return true;
        }
        if (roundState == RoundState.RUNNING) {
            // Dyes.
            Color dyeColor = Color.fromDyeMaterial(hand.getType());
            if (dyeColor != null && block != null && plugin.getGame().getGameMap().isColoredBlock(block)) {
                Color blockColor = Color.fromBlockData(block.getBlockData());
                // Don't bother if block is already same color as the dye.
                if (blockColor == dyeColor) {
                    return true;
                }
                // Only register the original color once, in case a block is dyed multiple times.
                if (!paintedBlocks.contains(block)) {
                    block.setMetadata("org-color", new FixedMetadataValue(plugin, block.getBlockData()));
                    paintedBlocks.add(block);
                }
                block.setBlockData(dyeColor.stain(block.getBlockData()));
                player.getWorld().playSound(block.getLocation(), Sound.ENTITY_SHEEP_SHEAR, SoundCategory.MASTER, 0.2f, 1f);
                hand.subtract(1);
                plugin.getGamePlayer(player).addDye();
                if (plugin.saveState.event) {
                    plugin.saveState.addScore(player.getUniqueId(), 1);
                    plugin.computeHighscore();
                }
                return true;
            } else {
                return tryUseItemInHand(player);
            }
        }
        return true;
    }

    public int countActivePlayers() {
        int count = 0;
        for (GamePlayer gp : plugin.getGamePlayers().values()) {
            if (gp.isPlayer()) count += 1;
        }
        return count;
    }
}
