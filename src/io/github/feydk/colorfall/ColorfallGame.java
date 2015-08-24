package io.github.feydk.colorfall;

import io.github.feydk.colorfall.GameMap.ColorBlock;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.winthier.minigames.MinigamesPlugin;
import com.winthier.minigames.event.player.PlayerLeaveEvent;
import com.winthier.minigames.game.Game;
import com.winthier.minigames.player.PlayerInfo;
import com.winthier.minigames.util.BukkitFuture;
import com.winthier.minigames.util.Msg;
import com.winthier.minigames.util.Players;
import com.winthier.minigames.util.Title;
import com.winthier.minigames.util.WorldLoader;

import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class ColorfallGame extends Game implements Listener
{		
	World world;
    
	// Stuff for keeping track of the game loop and ticks.
	GameState state;
	private RoundState roundState;
	BukkitRunnable task;
    long ticks;
    long emptyTicks;
    long stateTicks;
    long roundTicks;
    long winTicks = -1;
            
    private GameScoreboard scoreboard;
    private GameMap map;
    private Map<String, ItemStack> powerups = new HashMap<String, ItemStack>();
    private Map<Integer, Round> rounds = new HashMap<Integer, Round>();
    
    // Config stuff.
    private int disconnectLimit;
    private int minPlayersToStart;
    private int waitForPlayersDuration;
    private int countdownToStartDuration;
    private int startedDuration;
    private int endDuration;
    private int lives;
    
    private boolean didSomeoneJoin;
    private boolean moreThanOnePlayed;
    private String winnerName;
    private int currentRoundIdx;
    private Round currentRound;
    private long currentRoundDuration;
    private boolean currentRoundRandomized;
    private ColorBlock currentColor;
    private List<Block> paintedBlocks = new ArrayList<Block>();
    
    // Highscore stuff.
    final UUID gameUuid = UUID.randomUUID();
    final Highscore highscore = new Highscore();
    
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
    public void onEnable()
    {
    	String mapname = "prototype";
    	
    	FileConfiguration config = getConfigFile("config");
    	
    	map = new GameMap(config.getInt("maps." + mapname + ".chunkRadius"));
    	
    	String worldFile = config.getString("maps." + mapname + ".world");
    	
    	disconnectLimit = config.getInt("general.disconnectLimit");
    	waitForPlayersDuration = config.getInt("general.waitForPlayersDuration");
    	countdownToStartDuration = config.getInt("general.countdownToStartDuration");
    	startedDuration = config.getInt("general.startedDuration");
    	endDuration = config.getInt("general.endDuration");
    	
    	minPlayersToStart = config.getInt("maps." + mapname + ".minPlayersToStart");
    	
    	lives = config.getInt("maps." + mapname + ".lives");
    	
    	loadPowerups();
    	loadRounds();
    	    	    	    	
		// Load the world, with onWorldsLoaded() as callback
    	WorldLoader.loadWorlds(this, new BukkitFuture<WorldLoader>()
		{
			@Override public void run()
			{
				onWorldsLoaded(get());
			}
		}, worldFile);
    }
    
    private void loadPowerups()
    {
    	FileConfiguration config = getConfigFile("powerups");
    	
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
    	FileConfiguration config = getConfigFile("rounds");
    	
    	// Load the default settings. Every round that doesn't define any of the values will take the value from default.
    	long duration = config.getLong("default.duration") * 20;
    	double pvp = config.getDouble("default.pvp");
    	double randomize = config.getDouble("default.randomize");
    	MemorySection powerups = (MemorySection)config.get("default.powerups");
    	MemorySection effects = (MemorySection)config.get("default.effects");
    	    	
    	for(String key : config.getKeys(false))
    	{
    		//debug("Parsing " + key);
    		
    		long roundDuration = duration;
    		double roundPvp = pvp;
    		double roundRandomize = randomize;
    		MemorySection roundPowerups = powerups;
    		MemorySection roundEffects = effects;
    		
    		if(config.get(key + ".duration") != null)
    			roundDuration = config.getLong(key + ".duration") * 20;
    		
    		if(config.get(key + ".pvp") != null)
    			roundPvp = config.getLong(key + ".pvp");
    		
    		if(config.get(key + ".randomize") != null)
    			roundRandomize = config.getLong(key + ".randomize");
    			
    		if(config.get(key + ".powerups") != null)
    			roundPowerups = (MemorySection)config.get(key + ".powerups");
    		
    		if(config.get(key + ".effects") != null)
    			roundEffects = (MemorySection)config.get(key + ".effects");
    		
    		Round round = new Round(this);
    		round.setDuration(roundDuration);
    		round.setPvpChance(roundPvp);
    		round.setRandomizeChance(roundRandomize);
    		
    		// Determine if this round will have pvp enabled.
    		double number = Math.random() * 100;
    		
    		if(number - roundPvp <= 0)
    			round.setPvp(true);
    		
    		// Determine if this round will have randomize enabled.
    		number = Math.random() * 100;
    		
    		if(number - roundRandomize <= 0)
    			round.setRandomize(true);
    		
    		// Parse effects.
    		if(roundEffects != null)
    		{
	    		for(String effecttype : roundEffects.getKeys(true))
	    		{
	    			round.addEffect(PotionEffectType.getByName(effecttype), roundEffects.getInt(effecttype) * 20);
	    		}
    		}
    		
    		// Parse powerups.
    		if(roundPowerups != null)
    		{
	    		for(String powerup : roundPowerups.getKeys(true))
	    		{
	    			round.addPowerup(this.powerups.get(powerup), roundPowerups.getDouble(powerup));
	    		}
    		}
    		
    		if(key.equals("default"))
    			key = "0";
    		
    		rounds.put(Integer.parseInt(key), round);
        }
    }
    
    private Round getRound(int round)
    {
    	if(rounds.containsKey(round))
    	{
    		return rounds.get(round);
    	}
    	else
    	{
    		if(currentRound == null)
    		{
	    		// Round doesn't have a specific config, so use the default config instead.
	    		Round r = rounds.get(0).copy();
	    		
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
    		
    		return currentRound;
    	}
    }

    private void onWorldsLoaded(WorldLoader worldLoader)
    {
    	world = worldLoader.getWorld(0);
    	world.setDifficulty(Difficulty.HARD);
        world.setPVP(false);
        world.setTime(1000L);
        world.setGameRuleValue("doDaylightCycle", "true");
        world.setGameRuleValue("doTileDrops", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setWeatherDuration(Integer.MAX_VALUE);
        world.setStorm(false);
				
		task = new BukkitRunnable()
		{
		    @Override public void run()
		    {
		        onTick();
		    }
		};
		
		map.process(getSpawnLocation().getChunk());
				
		task.runTaskTimer(MinigamesPlugin.getInstance(), 1, 1);
		MinigamesPlugin.getEventManager().registerEvents(this, this);
		
		scoreboard = new GameScoreboard();
		
		highscore.init();
		
		ready();
    }

    @Override
    public void onDisable()
    {
        task.cancel();
    }

    @SuppressWarnings("static-access")
	private void onTick()
    {
    	ticks++;
    	
    	// All players left, shut it down.
    	if(getPlayerUuids().isEmpty())
    	{   
            cancel();
            return;
        }
    	
    	// Check if everyone logged off during the game state.
		if(state != GameState.INIT && state != GameState.WAIT_FOR_PLAYERS)
		{
			if(getOnlinePlayers().isEmpty())
			{
				final long emptyTicks = this.emptyTicks++;
		     
				// If no one was online for 60 seconds, shut it down.
		        if(emptyTicks >= 20 * 60)
		        {
		        	cancel();
		            return;
		        }
			}
		    else
		    {
		    	emptyTicks = 0L;
		    }
		}
		
		GameState newState = null;
		
		
		if(state != GameState.INIT && state != GameState.WAIT_FOR_PLAYERS && state != GameState.END)
		{
			// Check if only one player is left.
			int aliveCount = 0;
			GamePlayer survivor = null;
			
			for(Player player : getOnlinePlayers())
			{
				GamePlayer gp = getGamePlayer(player);
				
				if(gp.isAlive() && !gp.joinedAsSpectator())
				{
					survivor = gp;
					aliveCount++;
				}
			}
			
			// There will only be picked a winner if there were more than one player playing. Meaning that a SP game shouldn't be rewarded with a win.
			if(aliveCount == 1 && survivor != null && moreThanOnePlayed)
			{				
				// Consider this scenario: 2 players left alive, both with 1 life left.
				// Both of them falls about at the same time, but one reaches the void slightly before the other.
				// This should be declared a draw, but without the code below, whoever reaches the void last will win.
				// So I'm trying to prevent that by waiting a few secs, then see if the 'winner' is actually still alive.
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
							winnerName = survivor.getName();
							survivor.setWinner();
							survivor.setEndTime(new Date());
							survivor.recordHighscore();
							newState = GameState.END;
						}
					}
				}
			}
			else if(aliveCount == 0)
			{
				winnerName = null;
				newState = GameState.END;
			}
		}
		
		// Check for disconnects.
        for(PlayerInfo info : getPlayers())
        {
        	GamePlayer gp = getGamePlayer(info.getUuid());
            
        	if(!info.isOnline() && !gp.joinedAsSpectator())
        	{
                // Kick players who disconnect too long.
                long discTicks = gp.getDisconnectedTicks();
                
                if(discTicks > disconnectLimit * 20)
                {
                    getLogger().info("Kicking " + gp.getName() + " because they were disconnected too long");
                    MinigamesPlugin.getInstance().leavePlayer(info.getUuid());
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
    		case WAIT_FOR_PLAYERS:
    			scoreboard.setTitle(ChatColor.GREEN + "Waiting");
    			break;
    		case COUNTDOWN_TO_START:
    			scoreboard.setTitle(ChatColor.GREEN + "Get ready..");
    			
    			// Once the countdown starts, remove everyone who disconnected.
    			for(PlayerInfo info : getPlayers())
    			{
    				if(!info.isOnline())
    				{
    					MinigamesPlugin.leavePlayer(info.getUuid());
    				}
    				
    				GamePlayer gp = getGamePlayer(info.getUuid());
    				
    				if(gp.isPlayer() && !gp.joinedAsSpectator())
    				{
    					gp.setLives(lives);
    				}
    			}
    			
    			break;
    		case STARTED:
    			int count = 0;
    			
    			for(Player player : getOnlinePlayers())
    			{
    				GamePlayer gp = getGamePlayer(player);
    				
    				if(!gp.joinedAsSpectator())
    				{    				
    					makeMobile(player);
    					player.playSound(player.getEyeLocation(), Sound.WITHER_SPAWN, 1f, 1f);
    					count++;
    				}
    			}
    			
    			if(count > 1)
    				moreThanOnePlayed = true;
    			
    			break;
    		case END:
    			for(Player player : getOnlinePlayers())
    			{
    				getGamePlayer(player).setSpectator();
    				
    				//player.setAllowFlight(true);
    				//player.setFlying(true);
    		    	
    				player.playSound(player.getEyeLocation(), Sound.ENDERDRAGON_DEATH, 1f, 1f);
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
    			
    			Round round = getRound(currentRoundIdx);
    			currentRoundDuration = round.getDuration();
    			world.setPVP(round.getPvp());
    			
    			for(Player player : getOnlinePlayers())
    			{
    				GamePlayer gp = getGamePlayer(player);
    				
    				if(gp.isAlive() && gp.isPlayer())
    				{
    					gp.addRound();
    					
    					// Hand out powerups.
    					List<ItemStack> powerups = round.getDistributedPowerups();
    					
    					if(powerups.size() > 0)
    					{
    						for(ItemStack stack : powerups)
    							player.getInventory().addItem(stack);
    					}
    					
    					// Give potion effects.
    					for(PotionEffect effect : round.getDealtEffects())
    	    			{
    						player.addPotionEffect(effect);
    	    			}
    					
    					// Check if player has a feather (he could have thrown it away). If not, give him one.
    					if(!player.getInventory().contains(Material.FEATHER))
    					{
    						ItemStack feather = new ItemStack(Material.FEATHER);
    						ItemMeta meta = feather.getItemMeta();
    						meta.setDisplayName("Color checker");
    						
    						List<String> lore = new ArrayList<String>();
    						lore.add(ChatColor.DARK_AQUA + "Use this feather on a");
    						lore.add(ChatColor.DARK_AQUA + "colored block to check");
    						lore.add(ChatColor.DARK_AQUA + "the color of the block.");
    						
    						meta.setLore(lore);
    						
    						feather.setItemMeta(meta);
    						
    						player.getInventory().addItem(feather);
    					}
    					
    					if(player.getGameMode() == GameMode.SPECTATOR)
    					{
    						player.teleport(gp.getSpawnLocation());
    						gp.setPlayer();
    					}
    				}
    				
    				// Announce pvp and color.
    				Title.show(player, (round.getPvp() ? ChatColor.DARK_RED + "PVP is on!" : ""), ChatColor.WHITE + "The color of this round is " + translateToChatColor(currentColor.DataId) + translateToColor(currentColor.DataId).toUpperCase());
    			}
    			
    			break;
    		// Round time is over, remove blocks.
    		case REMOVING_BLOCKS:
    			map.removeBlocks(currentColor);
    			break;
    		// Restore blocks.
    		case RESTORING_BLOCKS:
    			map.restoreBlocks(paintedBlocks);
    			break;
    		// Round is over, wait for next round.
    		case OVER:
    			scoreboard.setTitle(ChatColor.GREEN + "Get ready.. ");
    			break;
    	}
    }
    
    @SuppressWarnings("deprecation")
	private void setColorForRound()
    {
    	ColorBlock pick;
		
		// Pick a color for this round. Which has to be different than the one from the previous round.
		while((pick = map.getRandomFromColorPool()).equals(currentColor))
		{
			// do nothing
		}
		
		// Item for the new color block.
		ItemStack stack = new ItemStack(pick.TypeId, 1, (short)0, (byte)pick.DataId);
		
		// Remove all color blocks from player inventories and give them the new one.
		for(Player player : getOnlinePlayers())
		{
			Inventory inv = player.getInventory();
			
			if(currentColor != null)
				inv.remove(Material.getMaterial(currentColor.TypeId));
			
			inv.addItem(stack);
		}
		
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
        if(!didSomeoneJoin)
        	return null;
        
        return GameState.WAIT_FOR_PLAYERS;
    }
    

	GameState tickWaitForPlayers(long ticks)
    {
    	// Every 5 seconds, ask players to ready (or leave).
    	if(ticks % (20 * 5) == 0)
    	{
    		for(Player player : getOnlinePlayers())
    		{
    			GamePlayer gp = getGamePlayer(player);
    			
    			if(!gp.isReady() && !gp.joinedAsSpectator())
    			{
    				List<Object> list = new ArrayList<>();
    				list.add(Msg.format("&fClick here when ready: "));
    				list.add(button("&3[Ready]", "&3Mark yourself as ready", "/ready"));
    				list.add(Msg.format("&f or "));
    				list.add(button("&c[Leave]", "&cLeave this game", "/leave"));
    				
    				Msg.sendRaw(player, list);
    			}
    		}
    	}
    	
    	long timeLeft = (waitForPlayersDuration * 20) - ticks;
    	
    	// Every second, update the sidebar timer.
    	if(timeLeft % 20 == 0)
    	{
    		scoreboard.refreshTitle(timeLeft);
    		
    		// Check if all players are ready.
    		boolean allReady = true;
    		int playerCount = 0;
    		
    		for(Player player : getOnlinePlayers())
    		{
    			playerCount++;
    			GamePlayer gp = getGamePlayer(player);
    			
    			if(!gp.isReady() && !gp.joinedAsSpectator())
    			{
    				allReady = false;
    				break;
    			}
    		}
    		
    		// If they are, start the countdown (to start the game).
    		if(allReady && playerCount >= minPlayersToStart)
    			return GameState.COUNTDOWN_TO_START;
    	}
    	
    	// Time ran out, so we force everyone ready.
    	if(timeLeft <= 0)
    	{
    		 if(getOnlinePlayers().size() >= minPlayersToStart)
    			 return GameState.COUNTDOWN_TO_START;
    		 else
    			 cancel();
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
    
    		for(Player player : getOnlinePlayers())
    		{	
    			if(seconds == 0)
    			{
    				Title.show(player, ChatColor.GREEN + "Go!", "");
    				player.playSound(player.getEyeLocation(), Sound.FIREWORK_LARGE_BLAST, 1f, 1f);
    			}
    			else if(seconds == countdownToStartDuration)
    			{
    				Title.show(player, ChatColor.GREEN + "Get ready!", ChatColor.GREEN + "Game starts in " + countdownToStartDuration + " seconds");
    				Msg.send(player, ChatColor.AQUA + "Game starts in %d seconds", seconds);
    			}
    			else
    			{
    				Title.show(player, ChatColor.GREEN + "Get ready!", "" + ChatColor.GREEN + seconds);
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
    	
    	if(roundState == RoundState.RUNNING && roundTimeLeft % 20L == 0)
    	{
    		Round round = getRound(currentRoundIdx);
    		
    		scoreboard.refreshTitle(roundTimeLeft);
    		scoreboard.updatePlayers();
    		
    		long seconds = roundTimeLeft / 20;
    		    		   		
			for(Player player : getOnlinePlayers())
			{
				// Countdown 3 seconds before round ends.
				if(seconds > 0 && seconds <= 3)
				{
					Title.show(player, "", "" + ChatColor.RED + seconds);
					player.playNote(player.getEyeLocation(), Instrument.PIANO, new Note((int)seconds));
				}
				else if(seconds == 0)
				{
					// Reset title so we don't have the '1' from the countdown hanging too long.
					Title.show(player, "", "");
				}
			}
			
			// Show/refresh particle effect above the blocks.
			map.animateBlocks(currentColor);
			
			// Handle randomize events.
			if(round.getRandomize() && !currentRoundRandomized)
			{
				// Fire this about 2 seconds before we're half way through the round.
				// TODO: the 2 seconds works with 15 second rounds. Should probably be made more dynamic or configurable.
				if(roundTimeLeft - 40 <= Math.round(currentRoundDuration / 2))
				{
					map.randomizeBlocks();
					currentRoundRandomized = true;					
					
					String title = ChatColor.WHITE + "" + ChatColor.DARK_AQUA + "R" + ChatColor.DARK_PURPLE + "a" + ChatColor.GOLD + "n" + ChatColor.GREEN + "d" + ChatColor.AQUA + "o" + ChatColor.RED + "m";
					title += ChatColor.WHITE + "i" + ChatColor.LIGHT_PURPLE + "z" + ChatColor.AQUA + "e" + ChatColor.GOLD + "d" + ChatColor.WHITE + "!";
					
					for(Player player : getOnlinePlayers())
					{
						Title.show(player, "", title);
						player.playSound(player.getLocation(), Sound.LEVEL_UP, 1, 1);
					}
				}
			}
    		
    		if(roundTimeLeft <= 0)
        	{
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
    		
    		for(Player player : getOnlinePlayers())
			{    		
	    		if(seconds <= 0)
	    		{
					player.playSound(player.getEyeLocation(), Sound.FIREWORK_LARGE_BLAST, 1f, 1f);
	    		}
	    		else
	    		{
    				Title.show(player, ChatColor.GREEN + "Round " + (currentRoundIdx + 1) + " starts in", seconds + " seconds");
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
    	
    	if(startedDuration > 0 && timeLeft <= 0)
    		return GameState.END;
    	
    	return null;
    }
    
    GameState tickEnd(long ticks)
    {
    	if(ticks == 0)
    	{
    		for(Player player : getOnlinePlayers())
    		{
    			player.setGameMode(GameMode.SPECTATOR);
    			
    			if(winnerName != null)
    			{
    				Msg.send(player, "&b%s wins the game!", winnerName);
    			}
    			else
    			{
    				Msg.send(player, "&bDraw! Nobody wins.");
    			}
    			
    			List<Object> list = new ArrayList<>();
    			list.add("Click here to leave the game: ");
    			list.add(button("&c[Leave]", "&cLeave this game", "/leave"));
    			Msg.sendRaw(player, list);
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
    		for(Player player : getOnlinePlayers())
    		{
    			if(winnerName != null)
    			{
    				Title.show(player, "&a" + winnerName, "&aWins the Game!");
    			}
    			else
    			{
    				Title.show(player, "&cDraw!", "&cNobody wins");
    			}
    		}
    	}
    	
    	if(timeLeft <= 0)
    		cancel();
    	
    	return null;
    }
    
    // Called once, when the player joins for the first time.
    @Override
    public void onPlayerReady(final Player player)
    {
    	didSomeoneJoin = true;
    	
    	Players.reset(player);
    	    	
    	GamePlayer gp = getGamePlayer(player);
    	    	
    	gp.setName(player.getName());
    	gp.setStartTime(new Date());
    	
    	scoreboard.addPlayer(player);
    	
    	if(gp.joinedAsSpectator())
    		return;
    	
    	switch(state)
    	{
    		case INIT:
    		case WAIT_FOR_PLAYERS:
    		case COUNTDOWN_TO_START:
    			gp.setPlayer();
    			scoreboard.setPlayerScore(player, 0);
    			break;
    		default:
    			if(gp.isPlayer())
    			{
    				scoreboard.setPlayerScore(player, 0);
    			}
    			
    			gp.setSpectator();
    	}
    	
    	new BukkitRunnable()
    	{
    		@Override public void run()
    		{
    			showHighscore(player);
    		}
    	}.runTaskLater(MinigamesPlugin.getInstance(), 20 * 3);
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
	Object button(String chat, String tooltip, String command)
    {
    	Map<String, Object> map = new HashMap<>();
    	map.put("text", Msg.format(chat));
    	
    	Map<String, Object> map2 = new HashMap<>();
    	map.put("clickEvent", map2);
    	
    	map2.put("action", "run_command");
    	map2.put("value", command);
    	
    	map2 = new HashMap();
    	map.put("hoverEvent", map2);
    	map2.put("action", "show_text");
    	map2.put("value", Msg.format(tooltip));
    	
    	return map;
    }
    
    // Called whenever a player joins. This could be after a player disconnect during a game, for instance.
    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event)
    {
    	Player player = event.getPlayer();
    	GamePlayer gp = getGamePlayer(player);
    	
    	if(gp.joinedAsSpectator())
    	{
    		gp.setSpectator();
    		player.setAllowFlight(true);
    		player.setFlying(true);
    		return;
    	}
    	
    	gp.setDisconnectedTicks(0);
    	
    	scoreboard.addPlayer(player);
    	
    	if(gp.isSpectator())
    	{
    		gp.setSpectator();
    		player.setAllowFlight(true);
    		player.setFlying(true);
    		return;
    	}
    	
    	switch(state)
    	{
    		case INIT:
    		case WAIT_FOR_PLAYERS:
    		case COUNTDOWN_TO_START:
    			// Someone joins in the early stages, we make sure they are locked in the right place.
    			makeImmobile(player, gp.getSpawnLocation());
    			break;
    		default:
    			// Join later and we make sure you are in the right state.
    			makeMobile(player);
    			gp.setPlayer();
    	}
    }
    
    @EventHandler(ignoreCancelled = true)
    public void onPlayerLeave(PlayerLeaveEvent event)
    {
    	if(event.getPlayer() == null)
    		return;
    	
    	GamePlayer gp = getGamePlayer(event.getPlayer());
    	    	
    	if(gp.joinedAsSpectator())
    		return;
    	
        gp.setEndTime(new Date());        
        gp.recordHighscore();
    }
    
    private void makeImmobile(Player player, Location location)
    {
    	if(!player.getLocation().getWorld().equals(location.getWorld()) || player.getLocation().distanceSquared(location) > 4.0)
    	{
    		player.teleport(location);
    		getLogger().info("Teleported " + player.getName() + " to their spawn location");
    	}
    	
    	player.setAllowFlight(true);
    	player.setFlying(true);
    	player.setFlySpeed(0);
    	player.setWalkSpeed(0);
    }

    private void makeMobile(Player player)
    {
    	player.setWalkSpeed(.2f);
    	player.setFlySpeed(.1f);
    	player.setFlying(false);
    	player.setAllowFlight(false);
    }
    
    @Override
    public boolean joinPlayers(List<UUID> uuids)
    {
    	switch(state)
    	{
    		case INIT:
            case WAIT_FOR_PLAYERS:
                return super.joinPlayers(uuids);
            default:
            	return false;
    	}
    }
    
    @Override
    public boolean joinSpectators(List<UUID> uuids)
    {
    	switch(state)
    	{
            //case INIT:
            //case WAIT_FOR_PLAYERS:
            //    getLogger().info("INIT/WAIT_FOR_PLAYERS: false");
            //    return false;
            default:
                getLogger().info("default");
                
                if(super.joinSpectators(uuids))
                {
                    getLogger().info("super.joinSpectators(): true");
                    
                    for(UUID uuid : uuids)
                    {
                    	getGamePlayer(uuid).setJoinedAsSpectator(true);
                        getGamePlayer(uuid).setSpectator();
                    }
                    
                    return true;
                }
                
                getLogger().info("super.joinSpectators(): false");
    	}
    	
    	return false;
    }
    
    @SuppressWarnings("deprecation")
	@EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event)
    {
    	if(!(event.getEntity() instanceof Player))
    		return;
    	
    	Player player = (Player)event.getEntity();
    	
    	player.setHealth(20);
    }
    
    @SuppressWarnings("deprecation")
	@EventHandler
    public void onEntityDamage(EntityDamageEvent event)
    {
    	if(!(event.getEntity() instanceof Player))
    		return;
    	
    	Player player = (Player)event.getEntity();
    	
    	GamePlayer gp = getGamePlayer(player.getUniqueId());
    	
    	if(gp.joinedAsSpectator())
    	{
    		event.setCancelled(true);
    		return;
    	}
    	
    	// Ok, this isn't pretty. But..
    	// It seems that when a player is teleported, any fall damage he is due to take is inflicted immediately. Even when falling into the void.
    	// This peculiarity leads to the player dying twice, once by falling out of the world and then by taking fall damage.
    	// So, to avoid double deaths I check if the player last died less than 500 ms ago.
    	if(gp.getLastDeath() > 0 && System.currentTimeMillis() - gp.getLastDeath() <= 500)
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
       			player.setGameMode(GameMode.SPECTATOR);
       			player.teleport(gp.getSpawnLocation());
       			//makeImmobile(player, gp.getSpawnLocation());
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
    
    @SuppressWarnings("deprecation")
	@Override
    public boolean onCommand(Player player, String command, String[] args)
    {
        if(command.equalsIgnoreCase("ready") && state == GameState.WAIT_FOR_PLAYERS)
        {
            getGamePlayer(player).setReady(true);
            scoreboard.setPlayerScore(player, 1);
            Msg.send(player, ChatColor.GREEN + "Marked as ready");
        }
        else if(command.equalsIgnoreCase("item") && args.length == 1 && player.isOp())
        {
            String key = args[0];
            
            ItemStack stack = powerups.get(key);
            
            if(stack.getTypeId() == 351)
			{
				ColorBlock cb = map.getRandomFromColorPool();
				byte dataid = 0;
				
				if(cb.DataId == 0)			// white
					dataid = 15;
				else if(cb.DataId == 1)		// orange
					dataid = 14;
				else if(cb.DataId == 2)		// magenta
					dataid = 13;
				else if(cb.DataId == 3)		// light blue
					dataid = 12;
				else if(cb.DataId == 4)		// yellow
					dataid = 11;
				else if(cb.DataId == 5)		// lime
					dataid = 10;
				else if(cb.DataId == 6)		// pink
					dataid = 9;
				else if(cb.DataId == 7)		// gray
					dataid = 8;
				else if(cb.DataId == 8)		// light gray
					dataid = 7;
				else if(cb.DataId == 9)		// cyan
					dataid = 6;
				else if(cb.DataId == 10)	// purple
					dataid = 5;
				else if(cb.DataId == 11)	// blue
					dataid = 4;
				else if(cb.DataId == 12)	// brown
					dataid = 3;
				else if(cb.DataId == 13)	// green
					dataid = 2;
				else if(cb.DataId == 14)	// red
					dataid = 1;
				else if(cb.DataId == 15)	// black
					dataid = 0;
				
				ItemStack newStack = new ItemStack(stack.getTypeId(), 1, (short)0, dataid);
				ItemMeta meta = newStack.getItemMeta();
				
				meta.setLore(stack.getItemMeta().getLore());
				meta.setDisplayName(stack.getItemMeta().getDisplayName());
				
				newStack.setItemMeta(meta);
				
				player.getInventory().addItem(newStack);
			}            
            else
            	player.getInventory().addItem(stack);
            
            Msg.send(player, "&eGiven item %s", key);
        }
        else if(command.equalsIgnoreCase("tp") && getGamePlayer(player).isSpectator())
        {
            if(args.length != 1)
            {
                Msg.send(player, "&cUsage: /tp <player>");
                return true;
            }
            String arg = args[0];
            
            for(Player target : getOnlinePlayers())
            {
                if(arg.equalsIgnoreCase(target.getName()))
                {
                    player.teleport(target);
                    Msg.send(player, "&bTeleported to %s", target.getName());
                    return true;
                }
            }
            
            Msg.send(player, "&cPlayer not found: %s", arg);
            return true;
        }
        else if(command.equalsIgnoreCase("highscore") || command.equalsIgnoreCase("hi"))
        {
            showHighscore(player);
        }
        else
        {
            return false;
        }
        
        return true;
    }
    
    public void onPlayerDeath(Player player)
    {
    	for(Player p : getOnlinePlayers())
    	{
    		if(!p.equals(player))
    			Msg.send(p, ChatColor.RED + player.getName() + " had bad timing and lost a life.");
    	}
    	
    	if(roundState == RoundState.RUNNING || roundState == RoundState.REMOVING_BLOCKS)
    		Msg.send(player, ChatColor.RED + "You lost a life and are put in spectator mode until this round is over.");
    }
    
    public void onPlayerElimination(Player player)
    {
    	// To avoid having spectators holding stuff in their hand.
    	player.getInventory().clear();
    	
    	getGamePlayer(player).setEndTime(new Date());
    	getGamePlayer(player).recordHighscore();
    	
    	for(Player p : getOnlinePlayers())
    	{
    		Title.show(p, "", ChatColor.RED + player.getName() + " died and is out of the game");
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
    
    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event)
    {
        Player p = event.getPlayer();

        if(p.getItemInHand().getType() == Material.FEATHER && event.getAction() == Action.RIGHT_CLICK_BLOCK)
        {
        	Block b = event.getClickedBlock();
        	
        	if(map.isColoredBlock(b))
        	{
        		 if(b.getType() != Material.AIR && b.getTypeId() == currentColor.TypeId && b.getData() == currentColor.DataId)
        		 {
        			 Msg.send(p, ChatColor.GREEN + "That block is " + translateToColor(b.getData()) + ", and it is the right one!");
        		 }
        		 else
        		 {
        			 Msg.send(p, ChatColor.RED + "That block is " + translateToColor(b.getData()) + ". That's not the right one!");
        		 }
        	}
        }
        
        if(roundState == RoundState.RUNNING)
        {        
	        // Dyes.
	        if(p.getItemInHand().getTypeId() == 351 && event.getAction() == Action.RIGHT_CLICK_BLOCK && map.isColoredBlock(event.getClickedBlock()))
	        {
	        	byte dye = p.getItemInHand().getData().getData();
	        	byte dataid = 0;
	        	
	        	if(dye == 15)		// white
					dataid = 0;
				else if(dye == 14)	// orange
					dataid = 1;
				else if(dye == 13)	// magenta
					dataid = 2;
				else if(dye == 12)	// light blue
					dataid = 3;
				else if(dye == 11)	// yellow
					dataid = 4;
				else if(dye == 10)	// lime
					dataid = 5;
				else if(dye == 9)	// pink
					dataid = 6;
				else if(dye == 8)	// gray
					dataid = 7;
				else if(dye == 7)	// light gray
					dataid = 8;
				else if(dye == 6)	// cyan
					dataid = 9;
				else if(dye == 5)	// purple
					dataid = 10;
				else if(dye == 4)	// blue
					dataid = 11;
				else if(dye == 3)	// brown
					dataid = 12;
				else if(dye == 2)	// green
					dataid = 13;
				else if(dye == 1)	// red
					dataid = 14;
				else if(dye == 0)	// black
					dataid = 15;
	        	
	        	// Only register the original color once, in case a block is dyed multiple times.
	        	if(!paintedBlocks.contains(event.getClickedBlock()))
	        	{
	        		event.getClickedBlock().setMetadata("org-color", new FixedMetadataValue(MinigamesPlugin.getInstance(), event.getClickedBlock().getData()));
	        		paintedBlocks.add(event.getClickedBlock());
	        	}
	        	
	        	event.getClickedBlock().setData(dataid);

	        	reduceItemInHand(p);
	        }
	        // The clock.
	        else if(p.getItemInHand().getType() == Material.WATCH && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK))
	        {
	        	for(Player pp : getOnlinePlayers())
	        	{
	        		Msg.send(pp, ChatColor.GOLD + p.getName() + " used a clock to extend the round!");
	        	}
	        	
	        	currentRoundDuration += 100;
	        	
	        	reduceItemInHand(p);
	        }
	        else if(p.getItemInHand().getType() == Material.EMERALD && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK))
	        {
	        	// Can only be used if round isn't already set to have randomize.
	        	if(!getRound(currentRoundIdx).getRandomize())
	        	{
	        		map.randomizeBlocks();
	        		reduceItemInHand(p);
	        		
	        		for(Player pp : getOnlinePlayers())
		        	{
		        		Msg.send(pp, ChatColor.WHITE + p.getName() + " " + ChatColor.DARK_AQUA + "r" + ChatColor.DARK_PURPLE + "a" + ChatColor.GOLD + "n" + ChatColor.GREEN + "d" + ChatColor.AQUA + "o" + ChatColor.RED + "m" + ChatColor.WHITE + "i" + ChatColor.LIGHT_PURPLE + "z" + ChatColor.AQUA + "e" + ChatColor.GOLD + "d" + ChatColor.WHITE + " the colors!");
		        		pp.playSound(pp.getLocation(), Sound.LEVEL_UP, 1, 1);
		        	}
	        	}
	        	else
	        	{
	        		Msg.send(p, "You can't use that right now ;)");
	        	}
	        }
        }
    }
    
    public String translateToColor(byte dataid)
    {
    	if(dataid == 0)				// white
			return "white";
		else if(dataid == 1)		// orange
			return "orange";
		else if(dataid == 2)		// magenta
			return "magenta";
		else if(dataid == 3)		// light blue
			return "light blue";
		else if(dataid == 4)		// yellow
			return "yellow";
		else if(dataid == 5)		// lime
			return "lime";
		else if(dataid == 6)		// pink
			return "pink";
		else if(dataid == 7)		// gray
			return "gray";
		else if(dataid == 8)		// light gray
			return "light gray";
		else if(dataid == 9)		// cyan
			return "cyan";
		else if(dataid == 10)		// purple
			return "purple";
		else if(dataid == 11)		// blue
			return "blue";
		else if(dataid == 12)		// brown
			return "brown";
		else if(dataid == 13)		// green
			return "green";
		else if(dataid == 14)		// red
			return "red";
		else if(dataid == 15)		// black
			return "black";
    	
    	return "";
    }
    
    private ChatColor translateToChatColor(byte dataid)
    {
    	if(dataid == 0)				// white
			return ChatColor.WHITE;
		else if(dataid == 1)		// orange
			return ChatColor.GOLD;
		else if(dataid == 2)		// magenta
			return ChatColor.BLUE;
		else if(dataid == 3)		// light blue
			return ChatColor.AQUA;
		else if(dataid == 4)		// yellow
			return ChatColor.YELLOW;
		else if(dataid == 5)		// lime
			return ChatColor.GREEN;
		else if(dataid == 6)		// pink
			return ChatColor.LIGHT_PURPLE;
		else if(dataid == 7)		// gray
			return ChatColor.DARK_GRAY;
		else if(dataid == 8)		// light gray
			return ChatColor.GRAY;
		else if(dataid == 9)		// cyan
			return ChatColor.DARK_AQUA;
		else if(dataid == 10)		// purple
			return ChatColor.DARK_PURPLE;
		else if(dataid == 11)		// blue
			return ChatColor.DARK_BLUE;
		else if(dataid == 12)		// brown
			return ChatColor.DARK_RED;
		else if(dataid == 13)		// green
			return ChatColor.DARK_GREEN;
		else if(dataid == 14)		// red
			return ChatColor.RED;
		else if(dataid == 15)		// black
			return ChatColor.BLACK;
    	
    	return ChatColor.WHITE;
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
    
    public GamePlayer getGamePlayer(UUID uuid)
    {
        return getPlayer(uuid).<GamePlayer>getCustomData(GamePlayer.class);
    }

    private GamePlayer getGamePlayer(Player player)
    {
        return getGamePlayer(player.getUniqueId());
    }

    public Location getSpawnLocation()
    {
        return new Location(world, 255, 60, 255);
    	//return world.getSpawnLocation();
    }
    
    @Override
    public Location getSpawnLocation(Player player)
    {
        switch (state)
        {
	        case INIT:
	        case WAIT_FOR_PLAYERS:
	        case COUNTDOWN_TO_START:
	            return getGamePlayer(player).getSpawnLocation();
	        default:
	            if(getGamePlayer(player).isSpectator())
	            {
	                return world.getSpawnLocation();
	            }
        }
        
        return null;
    }
    
    void showHighscore(Player player, List<Highscore.Entry> entries)
    {
        int i = 1;
        Msg.send(player, "&b&lColorfall Highscore");
        Msg.send(player, "&3Rank &fGames &aRounds &9&lWins &3Name");
        
        for(Highscore.Entry entry : entries)
        {
            Msg.send(player, "&3#%02d &f%02d &a%d &9&l%d &3%s", i++, entry.getCount(), entry.getRounds(), entry.getWins(), entry.getName());
        }
    }

    void showHighscore(Player player)
    {
        List<Highscore.Entry> entries = highscore.list();
        showHighscore(player, entries);
    }
	
	@SuppressWarnings("unused")
	private void debug(Object o)
	{
		System.out.println(o);
	}
}