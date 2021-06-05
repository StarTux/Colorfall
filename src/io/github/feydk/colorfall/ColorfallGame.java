package io.github.feydk.colorfall;

import io.github.feydk.colorfall.util.Msg;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
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
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

@RequiredArgsConstructor @Getter
public final class ColorfallGame {
    private final ColorfallPlugin plugin;
    // Stuff for keeping track of the game loop and ticks.
    GameState state = GameState.INIT;
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
    private GameMap gameMap;
    // Level config, sent from the framework.
    private String mapID = "Classic";
    boolean debug = false;
    // Debug stuff.
    protected final List<String> debugStrings = new ArrayList<>();;
    protected boolean denyStart = false;
    private boolean moreThanOnePlayed;
    private GamePlayer winner;
    private int currentRoundIdx;
    private Round currentRound;
    private long currentRoundDuration;
    private boolean currentRoundRandomized;
    private ColorBlock currentColor;
    private List<Block> paintedBlocks = new ArrayList<Block>();
    private boolean obsolete = false;

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
            if (aliveCount == 1 && survivor != null && moreThanOnePlayed) {
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
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "titles unlockset " + winner.getName() + " Colorful Technicolor Prismatic Pastel");
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
            plugin.getScoreboard().setTitle(ChatColor.GREEN + "Waiting");
            break;
        case COUNTDOWN_TO_START:
            plugin.getScoreboard().setTitle(ChatColor.GREEN + "Get ready...");
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
                player.getInventory().clear();
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
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.getGamePlayer(player).setSpectator();
                player.playSound(player.getEyeLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, SoundCategory.MASTER, 1f, 1f);
            }
            // Restore the map if this state was entered in the removing blocks round state.
            if (roundState == RoundState.REMOVING_BLOCKS) {
                gameMap.restoreBlocks(paintedBlocks);
            }
            plugin.getScoreboard().setTitle("Game over");
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
            plugin.getScoreboard().setTitle(ChatColor.GREEN + "Round " + currentRoundIdx);
            setColorForRound();
            disallowRandomize = false;
            Round round = getRound(currentRoundIdx);
            currentRoundDuration = round.getDuration();
            plugin.getScoreboard().setCollision(round.getCollision());
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
                //Color color = Color.fromBlockData(currentColor.blockData);
                //showTitle(player, (world.getPVP() ? ChatColor.DARK_RED + "PVP is on!" : ""), ChatColor.WHITE + "The color of this round is " + color.toChatColor() + color.niceName);
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
                    if (player.isOnGround() || player.isSwimming() || playerBlock.isLiquid()) {
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
            plugin.getScoreboard().setTitle(ChatColor.GREEN + "Get ready.. ");
            break;
        default:
            break;
        }
    }

    private void setColorForRound() {
        ColorBlock pick;
        // Pick a color for this round. Which has to be different than the one from the previous round.
        do {
            pick = gameMap.getRandomFromColorPool();
            // do nothing
        } while (pick.equals(currentColor));
        // Item for the new color block.
        // ItemStack stack = new ItemStack(pick.blockData.getMaterial());
        // Remove all color blocks from player inventories and give them the new one.
        // for (Player player : Bukkit.getOnlinePlayers())
        //     {
        //         Inventory inv = player.getInventory();
        //         if (currentColor != null)
        //             inv.remove(currentColor.blockData.getMaterial());
        //         inv.addItem(stack);
        //     }
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
            plugin.getScoreboard().setPlayerScore(player, 1);
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
            plugin.getScoreboard().refreshTitle(timeLeft);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (seconds == 0) {
                    Msg.showTitle(player, ChatColor.GREEN + "Go!", "", 0, 20, 0);
                    player.playSound(player.getEyeLocation(), Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.MASTER, 0.2f, 1f);
                } else if (seconds == plugin.getCountdownToStartDuration()) {
                    Msg.showTitle(player, ChatColor.GREEN + "Get ready!", ChatColor.GREEN + "Game starts in " + plugin.getCountdownToStartDuration() + " seconds");
                    Msg.send(player, ChatColor.AQUA + " Game starts in %d seconds", seconds);
                } else {
                    Msg.showTitle(player, ChatColor.GREEN + "Get ready!", "" + ChatColor.GREEN + seconds);
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
                Msg.send(player, ChatColor.RED + " Not starting game, due to missing configuration.");
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
        World world = gameMap.getWorld();
        if (roundState == RoundState.RUNNING && roundTimeLeft % 20L == 0) {
            Round round = getRound(currentRoundIdx);
            plugin.getScoreboard().refreshTitle(roundTimeLeft);
            plugin.getScoreboard().updatePlayers();
            //String actionMsg = (world.getPVP() ? ChatColor.DARK_RED + "PVP is on " + ChatColor.WHITE + "- " : "");
            // If it's night time or if we're in the end, use white color.
            // if (world.getTime() >= 13000 || world.getEnvironment() == World.Environment.THE_END)
            //     actionMsg += ChatColor.WHITE;
            // else
            //     actionMsg += ChatColor.BLACK;
            Color color = Color.fromBlockData(currentColor.blockData);
            //actionMsg += "The color of this round is " + color.toChatColor() + color.niceName;
            plugin.getBossBar().setTitle("" + color.toChatColor() + ChatColor.BOLD + color.niceName
                             + (world.getPVP() ? ChatColor.DARK_RED + " PVP" : ""));
            plugin.getBossBar().setProgress(currentRoundDuration == 0.0 ? 0.0 : (double) roundTimeLeft / (double) currentRoundDuration);
            long seconds = roundTimeLeft / 20;
            if (seconds <= 3) disallowRandomize = true;
            for (Player player : Bukkit.getOnlinePlayers()) {
                //Msg.sendActionBar(player, actionMsg);
                // Countdown 3 seconds before round ends.
                if (seconds > 0 && seconds <= 3) {
                    Msg.showTitle(player, "", "" + ChatColor.RED + seconds, 0, 20, 0);
                    player.playNote(player.getEyeLocation(), Instrument.PIANO, new Note((int) seconds));
                    if (seconds <= 2) disallowPistons = true;
                } else if (seconds == 0) {
                    // Reset title so we don't have the '1' from the countdown hanging too long.
                    Msg.showTitle(player, "", "");
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
                if (roundTimeLeft - 40 <= Math.round(currentRoundDuration / 2) && roundTimeLeft + 40 >= Math.round(currentRoundDuration / 2) && randomizeCooldown <= 0) {
                    gameMap.randomizeBlocks();
                    gameMap.highlightBlocks(currentColor);
                    currentRoundRandomized = true;
                    randomizeCooldown = 5 * 20;
                    String title = ChatColor.WHITE + "" + ChatColor.DARK_AQUA + "R" + ChatColor.DARK_PURPLE + "a" + ChatColor.GOLD + "n" + ChatColor.GREEN + "d" + ChatColor.AQUA + "o" + ChatColor.RED + "m";
                    title += ChatColor.WHITE + "i" + ChatColor.LIGHT_PURPLE + "z" + ChatColor.AQUA + "e" + ChatColor.GOLD + "d" + ChatColor.WHITE + "!";
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        Msg.showTitle(player, "", title);
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
                    Msg.showTitle(player, "" + seconds, ChatColor.GREEN + "Round " + (currentRoundIdx + 1), 0, 20, 0);
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
        // Every second, update the sidebar timer.
        if (timeLeft % 20L == 0) {
            plugin.getScoreboard().refreshTitle(timeLeft);
        }
        // Every 5 seconds, show/refresh the winner title announcement.
        if (timeLeft % (20 * 5) == 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (winner != null) {
                    Msg.showTitle(player, "&a" + winner.getName(), "&aWins the Game!");
                } else {
                    Msg.showTitle(player, "&cDraw!", "&cNobody wins");
                }
            }
        }
        if (timeLeft <= 0) {
            obsolete = true;
        }
        return null;
    }

    private void giveStartingItems(Player player) {
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
        player.getInventory().addItem(gameMap.getDye());
    }

    public void onPlayerDeath(Player player) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(player)) {
                Msg.send(p, " " + ChatColor.RED + player.getName() + " had bad timing and lost a life.");
            }
        }
        if (roundState == RoundState.RUNNING || roundState == RoundState.REMOVING_BLOCKS) {
            Msg.send(player, ChatColor.RED + " You lost a life and are put in spectator mode until this round is over.");
        }
    }

    public void onPlayerElimination(Player player) {
        plugin.getGamePlayer(player).setEndTime(new Date());
        for (Player p : Bukkit.getOnlinePlayers()) {
            Msg.showTitle(p, "", ChatColor.RED + player.getName() + " died and is out of the game");
        }
    }

    public boolean tryUseItemInHand(Player p) {
        // The clock.
        if (p.getItemInHand().getType() == Material.CLOCK) {
            for (Player pp : Bukkit.getOnlinePlayers()) {
                Msg.send(pp, " " + ChatColor.GOLD + p.getName() + " used a clock to extend the round!");
            }
            currentRoundDuration += 100;
            // Allow pistons again since there is now at least 5 seconds left.
            disallowPistons = false;
            reduceItemInHand(p);
            plugin.getGamePlayer(p).addClock();
            return true;
        } else if (p.getItemInHand().getType() == Material.EMERALD) {
            if (disallowRandomize) {
                Msg.send(p, " " + ChatColor.RED + "You can't use the randomizer this late in the round!");
            } else {
                if (randomizeCooldown <= 0) {
                    randomizeCooldown = 5 * 20;
                    gameMap.randomizeBlocks();
                    gameMap.highlightBlocks(currentColor);
                    reduceItemInHand(p);
                    plugin.getGamePlayer(p).addRandomizer();
                    for (Player pp : Bukkit.getOnlinePlayers()) {
                        Msg.send(pp, " " + ChatColor.WHITE + p.getName() + " " + ChatColor.DARK_AQUA + "r" + ChatColor.DARK_PURPLE + "a" + ChatColor.GOLD + "n" + ChatColor.GREEN + "d" + ChatColor.AQUA + "o" + ChatColor.RED + "m" + ChatColor.WHITE + "i" + ChatColor.LIGHT_PURPLE + "z" + ChatColor.AQUA + "e" + ChatColor.GOLD + "d" + ChatColor.WHITE + " the colors!");
                        pp.playSound(pp.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.2f, 1f);
                    }
                } else {
                    Msg.send(p, " " + ChatColor.RED + "Randomize is on cooldown for another " + (randomizeCooldown / 20) + " seconds.");
                }
            }
            return true;
        }
        return false;
    }

    private void reduceItemInHand(Player player) {
        ItemStack item = player.getItemInHand();
        if (item.getAmount() <= 1) {
            player.setItemInHand(null);
        } else {
            item.setAmount(item.getAmount() - 1);
            player.setItemInHand(item);
        }
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
        world.setGameRuleValue("doTileDrops", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setGameRuleValue("doFireTick", "false");
        world.setWeatherDuration(Integer.MAX_VALUE);
        world.setStorm(false);
        WorldBorder worldBorder = world.getWorldBorder();
        worldBorder.setSize(8192);
        gameMap = new GameMap(plugin.getConfig().getInt("general.chunkRadius"), this, world);
        gameMap.process();
        if (gameMap.getStartingTime() == -1) {
            world.setTime(1000L);
        } else {
            world.setTime(gameMap.getStartingTime());
        }
        if (gameMap.getLockTime()) {
            world.setGameRuleValue("doDaylightCycle", "false");
        } else {
            world.setGameRuleValue("doDaylightCycle", "true");
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
                if (block.getType() != Material.AIR && block.getBlockData().equals(currentColor.blockData)) {
                    Msg.send(player, ChatColor.GREEN + " That block is " + color.niceName + ", and it is the right one!");
                } else {
                    Msg.send(player, ChatColor.RED + " That block is " + color.niceName + ". That's not the right one!");
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
                reduceItemInHand(player);
                plugin.getGamePlayer(player).addDye();
                return true;
            } else {
                return tryUseItemInHand(player);
            }
        }
        return true;
    }
}
