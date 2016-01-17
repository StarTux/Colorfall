package io.github.feydk.colorfall;

import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.hooks.NCPExemptionManager;
import io.github.feydk.colorfall.GameMap.ColorBlock;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.player.PlayerTeleportEvent;
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
	static String nmsver;
    
	// Stuff for keeping track of the game loop and ticks.
	GameState state;
	private RoundState roundState;
	BukkitRunnable task;
    long ticks;
    long emptyTicks;
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
    
    // Config stuff.
    private int disconnectLimit;
    private int minPlayersToStart;
    private int waitForPlayersDuration;
    private int countdownToStartDuration;
    private int startedDuration;
    private int endDuration;
    private int lives;
    
    // Level config, sent from the framework.
    private String mapID = "Classic";
    private String mapPath;
    boolean debug = false;
    
    // Debug stuff.
    List<String> debugStrings = new ArrayList<String>();
    boolean denyStart = false;
    
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
    //final Highscore highscore = new Highscore();
    
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
    	ConfigurationSection config = getConfigFile("config");
    	
    	mapID = getConfig().getString("MapID", mapID);
        mapPath = getConfig().getString("MapPath", config.getString("general.defaultMapPath"));
        debug = getConfig().getBoolean("Debug", debug);
    	
    	map = new GameMap(config.getInt("general.chunkRadius"), this);
    	
    	disconnectLimit = config.getInt("general.disconnectLimit");
    	waitForPlayersDuration = config.getInt("general.waitForPlayersDuration");
    	countdownToStartDuration = config.getInt("general.countdownToStartDuration");
    	startedDuration = config.getInt("general.startedDuration");
    	endDuration = config.getInt("general.endDuration");
    	lives = config.getInt("general.lives");
    	
    	// Retiring the config value for now.
    	minPlayersToStart = 1;
    	
    	//minPlayersToStart = config.getInt("maps." + mapname + ".minPlayersToStart");
    	
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
			MinigamesPlugin.getInstance().getDatabase().createSqlUpdate(sql).execute();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
		System.out.println("Done setting up Colorfall player stats");
    	    	    	    	
		// Load the world, with onWorldsLoaded() as callback
    	WorldLoader.loadWorlds(this, new BukkitFuture<WorldLoader>()
		{
			@Override public void run()
			{
				onWorldsLoaded(get());
			}
		}, mapPath);
    	
    	nmsver = MinigamesPlugin.getInstance().getServer().getClass().getPackage().getName();
        nmsver = nmsver.substring(nmsver.lastIndexOf(".") + 1);
    }
    
    private void loadPowerups()
    {
    	ConfigurationSection config = getConfigFile("powerups");
    	
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
    	ConfigurationSection config = getConfigFile("rounds");
    	
    	// Load the default settings. Every round that doesn't define any of the values will take the value from default.
    	long duration = config.getLong("default.duration") * 20;
    	double pvp = config.getDouble("default.pvp");
    	double randomize = config.getDouble("default.randomize");
    	MemorySection powerups = (MemorySection)config.get("default.powerups");
    	MemorySection effects = (MemorySection)config.get("default.effects");
    	    	
    	for(String key : config.getKeys(false))
    	{
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
        world.setGameRuleValue("doTileDrops", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setGameRuleValue("doFireTick", "false");
        world.setWeatherDuration(Integer.MAX_VALUE);
        world.setStorm(false);
        
        map.process(getSpawnLocation().getChunk());
        
        if(map.getStartingTime() == -1)
        	world.setTime(1000L);
        else
        	world.setTime(map.getStartingTime());
        
        if(map.getLockTime())
        	 world.setGameRuleValue("doDaylightCycle", "false");
        else
        	 world.setGameRuleValue("doDaylightCycle", "true");
       				
		task = new BukkitRunnable()
		{
		    @Override public void run()
		    {
		        onTick();
		    }
		};
				
		task.runTaskTimer(MinigamesPlugin.getInstance(), 1, 1);
		MinigamesPlugin.getEventManager().registerEvents(this, this);
		
		scoreboard = new GameScoreboard();
		
		//highscore.init();
		
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
							
							if(!debug)
								survivor.recordStats(moreThanOnePlayed, mapID);
							
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
			
			if(randomizeCooldown > 0)
				randomizeCooldown--;
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
    				giveStartingItems(player);
    				
    				GamePlayer gp = getGamePlayer(player);
    				
    				if(!gp.joinedAsSpectator())
    				{
    					makeMobile(player);
    					player.playSound(player.getEyeLocation(), Sound.WITHER_SPAWN, 1f, 1f);
    					count++;
    				}
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
	    				for(Player player : getOnlinePlayers())
	        			{
	        				GamePlayer gp = getGamePlayer(player);
	        				
	        				if(!gp.joinedAsSpectator())
	        				{
	        					gp.setLives(1);
	        				}
	        			}
    				}
    			}
    			
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
    			disallowRandomize = false;
    			    			
    			Round round = getRound(currentRoundIdx);
    			currentRoundDuration = round.getDuration();
    			
    			// If single player game not in debug mode, disable pvp.
    			if(!moreThanOnePlayed && !debug)
    				world.setPVP(false);
    			else
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
    					
    					if(player.getGameMode() == GameMode.SPECTATOR)
    					{
    						player.teleport(gp.getSpawnLocation());
    						gp.setPlayer();
    					}
    				}
    				
    				// Announce pvp and color.
    				Title.show(player, (world.getPVP() ? ChatColor.DARK_RED + "PVP is on!" : ""), ChatColor.WHITE + "The color of this round is " + translateToChatColor(currentColor.DataId) + translateToColor(currentColor.DataId).toUpperCase());
    			}
    			
    			break;
    		// Round time is over, remove blocks.
    		case REMOVING_BLOCKS:
    			world.setPVP(false);
    			map.removeBlocks(currentColor);
    			break;
    		// Restore blocks.
    		case RESTORING_BLOCKS:
    			map.restoreBlocks(paintedBlocks);
    			disallowPistons = false;
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
    				list.add(Msg.format(" &fClick here when ready: "));
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
    				Msg.send(player, ChatColor.AQUA + " Game starts in %d seconds", seconds);
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
    	
    	if(denyStart)
    	{
    		for(Player player : getOnlinePlayers())
			{
    			Msg.send(player, ChatColor.RED + " Not starting game, due to missing configuration.");
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
    	
    	if(roundState == RoundState.RUNNING && roundTimeLeft % 20L == 0)
    	{
    		Round round = getRound(currentRoundIdx);
    		
    		scoreboard.refreshTitle(roundTimeLeft);
    		scoreboard.updatePlayers();
    		
    		String actionMsg = (world.getPVP() ? ChatColor.DARK_RED + "PVP is on " + ChatColor.WHITE + "- " : "");
    		
    		// If it's night time or if we're in the end, use white color.
    		if(world.getTime() >= 13000 || world.getBiome(255, 255) == Biome.SKY)
    			actionMsg += ChatColor.WHITE;
    		else
    			actionMsg += ChatColor.BLACK;
    		
    		actionMsg += "The color of this round is " + translateToChatColor(currentColor.DataId) + translateToColor(currentColor.DataId).toUpperCase();
    		    		
    		long seconds = roundTimeLeft / 20;
    		    		   		
			for(Player player : getOnlinePlayers())
			{
				sendActionBar(player, actionMsg);
				
				// Countdown 3 seconds before round ends.
				if(seconds > 0 && seconds <= 3)
				{
					disallowRandomize = true;
					Title.show(player, "", "" + ChatColor.RED + seconds);
					player.playNote(player.getEyeLocation(), Instrument.PIANO, new Note((int)seconds));
					
					if(seconds <= 2)
						disallowPistons = true;
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
				// Fire this about 2 seconds before we're half way through the round, but no later than 2 seconds after half way.
				// Note: the 2 seconds works with 15 second rounds. Should probably be made more dynamic or configurable.
				if(roundTimeLeft - 40 <= Math.round(currentRoundDuration / 2) && roundTimeLeft + 40 >= Math.round(currentRoundDuration / 2) && randomizeCooldown <= 0)
				{
					map.randomizeBlocks();
					currentRoundRandomized = true;
					randomizeCooldown = 5 * 20;
					
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
    				Title.show(player, "" + seconds, ChatColor.GREEN + "Round " + (currentRoundIdx + 1));
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
    				Msg.send(player, " &b%s wins the game!", winnerName);
    			}
    			else
    			{
    				Msg.send(player, " &bDraw! Nobody wins.");
    			}
    			
    			List<Object> list = new ArrayList<>();
    			list.add(" Click here to leave the game: ");
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
    	gp.updateStatsName();
    	
    	scoreboard.addPlayer(player);
    	
    	if(debug)
    	{
    		if(debugStrings.size() > 0)
    		{
    			Msg.send(player, ChatColor.DARK_RED + " === DEBUG INFO ===");
    			
    			for(String s : debugStrings)
    			{
    				Msg.send(player, " " + ChatColor.RED + s);
    			}
    		}
    	}
    	
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
    	
    	if(joinRandomStat == null)
    	{
    		joinRandomStat = getStatsJson(new Random(System.currentTimeMillis()).nextInt(8));
    	}
    	
    	new BukkitRunnable()
    	{
    		@Override public void run()
    		{    			    			
    			Msg.send(player, " ");
    			
    			String credits = map.getCredits();
    	    	
    	    	if(!credits.isEmpty())
    	    		Msg.send(player, ChatColor.GOLD + " Welcome to Colorfall!" + ChatColor.WHITE + " Map name: " + ChatColor.AQUA + mapID + ChatColor.WHITE + " - Made by: " + ChatColor.AQUA + credits);
    			
    	    	Msg.send(player, " ");
    	    	
    	    	sendJsonMessage(player, joinRandomStat);
    	    	
    			/*List<Object> list = new ArrayList<>();
				list.add(Msg.format(" &2View the highscore by typing "));
				list.add(button("&a/hi", "&3View the highscore", "/hi"));
				Msg.sendRaw(player, list);*/
    			
    			
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
        
        if(!debug)
        	gp.recordStats(moreThanOnePlayed, mapID);
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
    public void onProjectileThrownEvent(ProjectileLaunchEvent event)
    {
    	if(event.getEntity() instanceof Snowball)
    	{
    		Snowball snowball = (Snowball)event.getEntity();
                if (snowball.getShooter() instanceof Player) {
                    Player playerThrower = (Player)snowball.getShooter();
                    getGamePlayer(playerThrower).addSnowball();
                }
    	}
    }
    
    @SuppressWarnings("deprecation")
	@EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event)
    {
    	if(!(event.getEntity() instanceof Player))
    		return;
    	
    	if(event.getDamager() instanceof Snowball)
    	{
    		Snowball snowball = (Snowball)event.getDamager();
                if (snowball.getShooter() instanceof Player) {
                    {
                        Player playerThrower = (Player)snowball.getShooter();
                        getGamePlayer(playerThrower).addSnowballHit();
                    }
                }
    	}
    	
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
    
    private void giveStartingItems(Player player)
    {    	
    	// Give feather.
		ItemStack feather = new ItemStack(Material.FEATHER);
		ItemMeta meta = feather.getItemMeta();
		meta.setDisplayName("Color checker");
		
		List<String> lore = new ArrayList<String>();
		lore.add(ChatColor.DARK_AQUA + "Use this feather on a");
		lore.add(ChatColor.DARK_AQUA + "colored block to check");
		lore.add(ChatColor.DARK_AQUA + "the color of the block.");
		
		meta.setLore(lore);
		
		feather.setItemMeta(meta);
		
		player.getInventory().setItem(1, feather);
		
		// Give one dye.
		player.getInventory().setItem(2, map.getDye());
		
    }
    
    @SuppressWarnings("deprecation")
	@Override
    public boolean onCommand(Player player, String command, String[] args)
    {
        if(command.equalsIgnoreCase("ready") && state == GameState.WAIT_FOR_PLAYERS)
        {
            getGamePlayer(player).setReady(true);
            scoreboard.setPlayerScore(player, 1);
            Msg.send(player, ChatColor.GREEN + " Marked as ready");
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
            
            Msg.send(player, " &eGiven item %s", key);
        }
        else if(command.equalsIgnoreCase("tp") && getGamePlayer(player).isSpectator())
        {
            if(args.length != 1)
            {
                Msg.send(player, " &cUsage: /tp <player>");
                return true;
            }
            String arg = args[0];
            
            for(Player target : getOnlinePlayers())
            {
                if(arg.equalsIgnoreCase(target.getName()))
                {
                    player.teleport(target);
                    Msg.send(player, " &bTeleported to %s", target.getName());
                    return true;
                }
            }
            
            Msg.send(player, " &cPlayer not found: %s", arg);
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
            
        	//showHighscore(player);
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
    	for(Player p : getOnlinePlayers())
    	{
    		if(!p.equals(player))
    			Msg.send(p, " " + ChatColor.RED + player.getName() + " had bad timing and lost a life.");
    	}
    	
    	if(roundState == RoundState.RUNNING || roundState == RoundState.REMOVING_BLOCKS)
    		Msg.send(player, ChatColor.RED + " You lost a life and are put in spectator mode until this round is over.");
    }
    
    public void onPlayerElimination(Player player)
    {
    	getGamePlayer(player).setEndTime(new Date());
    	
    	if(!debug)
    		getGamePlayer(player).recordStats(moreThanOnePlayed, mapID);
    	
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
        final Player p = event.getPlayer();
        
        // If player pushes a button we disable NCP fly protect for 1 second, to cater for slime block launchers. Do a radius because more people may be standing on the same block.
        // Note: may need to add support for other triggers, like pressure plates and stuff.
        if(event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.STONE_BUTTON && event.getAction() == Action.RIGHT_CLICK_BLOCK)
        {
        	List<Entity> jumpers = new ArrayList<Entity>();
        	jumpers.add(p);
        	jumpers.addAll(p.getNearbyEntities(10, 10, 10));
        	
        	for(Entity e : jumpers)
        	{
        		if(e instanceof Player)
        		{
		        	NCPExemptionManager.exemptPermanently(p, CheckType.MOVING_SURVIVALFLY);
		        	
		        	new BukkitRunnable()
		        	{
		        		@Override public void run()
		        		{
		        			NCPExemptionManager.unexempt(p, CheckType.MOVING_SURVIVALFLY);
		        		}
		        	}.runTaskLater(MinigamesPlugin.getInstance(), 20);
        		}
        	}
        }
        
        if(p.getItemInHand().getType() == Material.FEATHER && event.getAction() == Action.RIGHT_CLICK_BLOCK)
        {
        	Block b = event.getClickedBlock();
        	
        	if(map.isColoredBlock(b))
        	{
        		 if(b.getType() != Material.AIR && b.getTypeId() == currentColor.TypeId && b.getData() == currentColor.DataId)
        		 {
        			 Msg.send(p, ChatColor.GREEN + " That block is " + translateToColor(b.getData()) + ", and it is the right one!");
        		 }
        		 else
        		 {
        			 Msg.send(p, ChatColor.RED + " That block is " + translateToColor(b.getData()) + ". That's not the right one!");
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
	        	
	        	// Don't bother if block is already same color as the dye.
	        	if(event.getClickedBlock().getData() == dataid)
	        		return;
	        	
	        	// Only register the original color once, in case a block is dyed multiple times.
	        	if(!paintedBlocks.contains(event.getClickedBlock()))
	        	{
	        		event.getClickedBlock().setMetadata("org-color", new FixedMetadataValue(MinigamesPlugin.getInstance(), event.getClickedBlock().getData()));
	        		paintedBlocks.add(event.getClickedBlock());
	        	}
	        	
	        	event.getClickedBlock().setData(dataid);
	        	
	        	p.getWorld().playSound(event.getClickedBlock().getLocation(), Sound.SHEEP_SHEAR, 1, 1);

	        	reduceItemInHand(p);
	        	getGamePlayer(p).addDye();
	        }
	        // The clock.
	        else if(p.getItemInHand().getType() == Material.WATCH && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK))
	        {
	        	for(Player pp : getOnlinePlayers())
	        	{
	        		Msg.send(pp, " " + ChatColor.GOLD + p.getName() + " used a clock to extend the round!");
	        	}
	        	
	        	currentRoundDuration += 100;
	        	
	        	// Allow pistons again since there is now at least 5 seconds left.
	        	disallowPistons = false;
	        	
	        	reduceItemInHand(p);
	        	getGamePlayer(p).addClock();
	        }
	        // The randomizer.
	        else if(p.getItemInHand().getType() == Material.EMERALD && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK))
	        {
        		if(disallowRandomize)
        		{
        			Msg.send(p, " " + ChatColor.RED + "You can't use the randomizer this late in the round!");
        		}
        		else
        		{
        			if(randomizeCooldown <= 0)
        			{
	        			randomizeCooldown = 5 * 20;
	        			
	        			map.randomizeBlocks();
		        		reduceItemInHand(p);
		        		getGamePlayer(p).addRandomizer();
			        		
		        		for(Player pp : getOnlinePlayers())
			        	{
			        		Msg.send(pp, " " + ChatColor.WHITE + p.getName() + " " + ChatColor.DARK_AQUA + "r" + ChatColor.DARK_PURPLE + "a" + ChatColor.GOLD + "n" + ChatColor.GREEN + "d" + ChatColor.AQUA + "o" + ChatColor.RED + "m" + ChatColor.WHITE + "i" + ChatColor.LIGHT_PURPLE + "z" + ChatColor.AQUA + "e" + ChatColor.GOLD + "d" + ChatColor.WHITE + " the colors!");
			        		pp.playSound(pp.getLocation(), Sound.LEVEL_UP, 1, 1);
			        	}
        			}
        			else
        			{
        				Msg.send(p, " " + ChatColor.RED + "Randomize is on cooldown for another " + (randomizeCooldown / 20) + " seconds.");
        			}
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

    /*@EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event)
    {
    	if(disallowPistons)
    		event.setCancelled(true);
    }*/
    
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
    
    public byte translateToDataId(byte value)
    {
    	if(value == 0)			// white
    		return 15;
    	else if(value == 1)		// orange
    		return 14;
    	else if(value == 2)		// magenta
    		return 13;
    	else if(value == 3)		// light blue
    		return 12;
    	else if(value == 4)		// yellow
    		return 11;
    	else if(value == 5)		// lime
    		return 10;
    	else if(value == 6)		// pink
    		return 9;
    	else if(value == 7)		// gray
    		return 8;
    	else if(value == 8)		// light gray
    		return 7;
    	else if(value == 9)		// cyan
    		return 6;
    	else if(value == 10)	// purple
    		return 5;
    	else if(value == 11)	// blue
    		return 4;
    	else if(value == 12)	// brown
    		return 3;
    	else if(value == 13)	// green
    		return 2;
    	else if(value == 14)	// red
    		return 1;
    	else if(value == 15)	// black
    		return 0;
    		
    	return 0;
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
        /*int i = 1;
        Msg.send(player, " &b&lColorfall Highscore");
        Msg.send(player, " &3Rank &fGames &aRounds &9&lWins &3Name");
        
        for(Highscore.Entry entry : entries)
        {
            Msg.send(player, " &3#%02d &f%02d &a%d &9&l%d &3%s", i++, entry.getCount(), entry.getRounds(), entry.getWins(), entry.getName());
        }*/
    }

    /*void showHighscore(Player player)
    {
        List<Highscore.Entry> entries = highscore.list();
        showHighscore(player, entries);
    }*/
    
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
    		List<PlayerStats> list = PlayerStats.loadTopWinners();
    		    		
    		json += "{text: \" §6»» §bColorfall top dogs §6««\n\"}, ";
    		
    		int i = 0;
    		for(PlayerStats obj : list)
    		{
    			if(i == 0)
    				json += "{text: \" §3Current leader: \"}, ";
    			else
    				json += "{text: \" §3Runner-up: \"}, ";
    			
    			json += "{text: \"§b" + obj.getName() + " §fwith §b" + obj.getGamesWon() + " §fwin" + (obj.getGamesWon() == 1 ? "" : "s") + "\"}, ";
    			
    			if(obj.getSuperiorWins() > 0)
    				json += "{text: \"§f, of which §b" + obj.getSuperiorWins() + " §f" + (obj.getSuperiorWins() == 1 ? "is a" : "are") + " §asuperior win" + (obj.getSuperiorWins() == 1 ? "" : "s") + "\"}, ";
    			
    			json += "{text: \"§f.\n\"}, ";
    			
    			i++;
    		}
    		
    		if(i == 0)
    		{
    			json += "{text: \" §fNothing in this category yet. You can be the first ツ\n\"}, ";
    		}
    	}
    	else if(type == 1)
    	{
    		List<PlayerStats> list = PlayerStats.loadTopPainters();
    		    		
    		json += "{text: \" §6»» §bColorfall top painters §6««\n\"}, ";
    		
    		int i = 0;
    		for(PlayerStats obj : list)
    		{
    			if(i == 0)
    				json += "{text: \" §3Current leader: \"}, ";
    			else
    				json += "{text: \" §3Runner-up: \"}, ";
    			
    			json += "{text: \"§b" + obj.getName() + " §fhas used §b" + obj.getDyesUsed() + " §fdye" + (obj.getDyesUsed() == 1 ? "" : "s") + ".\n\"}, ";
    			
    			i++;
    		}
    		
    		if(i == 0)
    		{
    			json += "{text: \" §fNothing in this category yet. You can be the first ツ\n\"}, ";
    		}
    	}
    	else if(type == 2)
    	{
    		List<PlayerStats> list = PlayerStats.loadTopClockers();
    		    		
    		json += "{text: \" §6»» §bColorfall top manipulators of time §6««\n\"}, ";
    		
    		int i = 0;
    		for(PlayerStats obj : list)
    		{
    			if(i == 0)
    				json += "{text: \" §3Current leader: \"}, ";
    			else
    				json += "{text: \" §3Runner-up: \"}, ";
    			
    			json += "{text: \"§b" + obj.getName() + " §fextended the time §b" + obj.getClocksUsed() + " §ftime" + (obj.getClocksUsed() == 1 ? "" : "s") + ".\n\"}, ";
    			
    			i++;
    		}
    		
    		if(i == 0)
    		{
    			json += "{text: \" §fNothing in this category yet. You can be the first ツ\n\"}, ";
    		}
    	}
    	else if(type == 3)
    	{
    		List<PlayerStats> list = PlayerStats.loadTopPearlers();
    		    		
    		json += "{text: \" §6»» §bColorfall top dislocators §6««\n\"}, ";
    		
    		int i = 0;
    		for(PlayerStats obj : list)
    		{
    			if(i == 0)
    				json += "{text: \" §3Current leader: \"}, ";
    			else
    				json += "{text: \" §3Runner-up: \"}, ";
    			
    			json += "{text: \"§b" + obj.getName() + " §fhas thrown §b" + obj.getEnderpearlsUsed() + " §fender pearl" + (obj.getEnderpearlsUsed() == 1 ? "" : "s") + ".\n\"}, ";
    			
    			i++;
    		}
    		
    		if(i == 0)
    		{
    			json += "{text: \" §fNothing in this category yet. You can be the first ツ\n\"}, ";
    		}
    	}
    	else if(type == 4)
    	{
    		List<PlayerStats> list = PlayerStats.loadTopSnowballers();
    		    		
    		json += "{text: \" §6»» §bColorfall top trigger happy players §6««\n\"}, ";
    		
    		int i = 0;
    		for(PlayerStats obj : list)
    		{
    			if(i == 0)
    				json += "{text: \" §3Current leader: \"}, ";
    			else
    				json += "{text: \" §3Runner-up: \"}, ";
    			
    			json += "{text: \"§b" + obj.getName() + " §fhas thrown §b" + obj.getSnowballsUsed() + " §fsnow ball" + (obj.getSnowballsUsed() == 1 ? "" : "s") + "\"}, ";
    			
    			if(obj.getSnowballsHit() > 0)
    				json += "{text: \"§f, of which §b" + obj.getSnowballsHit() + " §fhit their target\"}, ";
    			
    			json += "{text: \"§f.\n\"}, ";
    			
    			i++;
    		}
    		
    		if(i == 0)
    		{
    			json += "{text: \" §fNothing in this category yet. You can be the first ツ\n\"}, ";
    		}
    	}
    	else if(type == 5)
    	{
    		List<PlayerStats> list = PlayerStats.loadTopRandomizers();
    		    		
    		json += "{text: \" §6»» §bColorfall top randomizers §6««\n\"}, ";
    		
    		int i = 0;
    		for(PlayerStats obj : list)
    		{
    			if(i == 0)
    				json += "{text: \" §3Current leader: \"}, ";
    			else
    				json += "{text: \" §3Runner-up: \"}, ";
    			
    			json += "{text: \"§b" + obj.getName() + " §fhas used §b" + obj.getRandomizersUsed() + " §frandomizer" + (obj.getRandomizersUsed() == 1 ? "" : "s") + ".\n\"}, ";
    			
    			i++;
    		}
    		
    		if(i == 0)
    		{
    			json += "{text: \" §fNothing in this category yet. You can be the first ツ\n\"}, ";
    		}
    	}
    	else if(type == 6)
    	{
    		List<PlayerStats> list = PlayerStats.loadTopDeaths();
    		    		
    		json += "{text: \" §6»» §bColorfall top investigators of the Void §6««\n\"}, ";
    		
    		int i = 0;
    		for(PlayerStats obj : list)
    		{
    			if(i == 0)
    				json += "{text: \" §3Current leader: \"}, ";
    			else
    				json += "{text: \" §3Runner-up: \"}, ";
    			
    			json += "{text: \"§b" + obj.getName() + " §fdied §b" + obj.getDeaths() + " §ftime" + (obj.getDeaths() == 1 ? "" : "s") + ".\n\"}, ";
    			
    			i++;
    		}
    		
    		if(i == 0)
    		{
    			json += "{text: \" §fNothing in this category yet. You can be the first ツ\n\"}, ";
    		}
    	}
    	else if(type == 7)
    	{
    		List<PlayerStats> list = PlayerStats.loadTopContestants();
    		    		
    		json += "{text: \" §6»» §bColorfall top contestants §6««\n\"}, ";
    		
    		int i = 0;
    		for(PlayerStats obj : list)
    		{
    			if(i == 0)
    				json += "{text: \" §3Current leader: \"}, ";
    			else
    				json += "{text: \" §3Runner-up: \"}, ";
    			
    			json += "{text: \"§b" + obj.getName() + " §fhas played §b" + obj.getGamesPlayed() + " §fgame" + (obj.getGamesPlayed() == 1 ? "" : "s") + "\"}, ";
    			
    			if(obj.getRoundsPlayed() > 0)
    				json += "{text: \"§f, and a total of §b" + obj.getRoundsPlayed() + " §fround" + (obj.getRoundsPlayed() == 1 ? "" : "s") + "\"}, ";
    			
    			json += "{text: \"§f.\n\"}, ";
    			
    			i++;
    		}
    		
    		if(i == 0)
    		{
    			json += "{text: \" §fNothing in this category yet. You can be the first ツ\n\"}, ";
    		}
    	}
    	
    	json += "{text: \"\n §eOther stats:\n\"}, ";
    	
    	if(type != 0)
    		json += "{text: \" §f[§bWins§f]\", clickEvent: {action: \"run_command\", value: \"/hi 0\" }, hoverEvent: {action: \"show_text\", value: \"§fSee who won the most games.\"}}, ";
    	
    	if(type != 6)
    		json += "{text: \" §f[§bDeaths§f]\", clickEvent: {action: \"run_command\", value: \"/hi 6\" }, hoverEvent: {action: \"show_text\", value: \"§fSee who died the most.\"}}, ";
    	
    	if(type != 7)
    		json += "{text: \" §f[§bContestants§f]\", clickEvent: {action: \"run_command\", value: \"/hi 7\" }, hoverEvent: {action: \"show_text\", value: \"§fSee who played the most games.\"}}, ";
    	
    	if(type != 1)
    		json += "{text: \" §f[§bPainters§f]\", clickEvent: {action: \"run_command\", value: \"/hi 1\" }, hoverEvent: {action: \"show_text\", value: \"§fSee who used the most dyes.\"}}, ";
    	
    	json += "{text: \"\n\"}, ";
    	
    	if(type != 2)
    		json += "{text: \" §f[§bClockers§f]\", clickEvent: {action: \"run_command\", value: \"/hi 2\" }, hoverEvent: {action: \"show_text\", value: \"§fSee who used the most clocks.\"}}, ";
    	
    	if(type != 3)
    		json += "{text: \" §f[§bPearlers§f]\", clickEvent: {action: \"run_command\", value: \"/hi 3\" }, hoverEvent: {action: \"show_text\", value: \"§fSee who used the most enderpearls.\"}}, ";
    	
    	if(type != 4)
    		json += "{text: \" §f[§bSnowballers§f]\", clickEvent: {action: \"run_command\", value: \"/hi 4\" }, hoverEvent: {action: \"show_text\", value: \"§fSee who used the most snowballs.\"}}, ";
    	
    	if(type != 5)
    		json += "{text: \" §f[§bRandomizers§f]\", clickEvent: {action: \"run_command\", value: \"/hi 5\" }, hoverEvent: {action: \"show_text\", value: \"§fSee who used the most randomizers.\"}}, ";
    	
    	json += "{text: \"\n §f[§6See your own stats§f]\", clickEvent: {action: \"run_command\", value: \"/stats\" }, hoverEvent: {action: \"show_text\", value: \"§fSee your personal stats.\"}}, ";
    	    	    	
    	json += "{text: \" \n\"} ";
		json += "] ";
		
		return json;
    }
    
    void showStats(Player player, String name)
    {
    	PlayerStats stats = new PlayerStats();
    	stats.loadOverview(name);
    	
    	if(stats.getGamesPlayed() > 0)
    	{
    		String json = "[";
    		json += "{text: \" §3§l§m   §3 Stats for §b" + name + " §3§l§m   \n\"}, ";
			/*json += "{text: \" §fGames: §b" + stats.getGamesPlayed() + " §fplayed §7/ §b" + stats.getGamesWon() + " §fwon\n\"}, ";
			json += "{text: \" §fSuperior wins: §b" + stats.getSuperiorWins() + "\n\"}, ";
			json += "{text: \" §fDeaths: §b" + stats.getDeaths() + "\n\"}, ";
			json += "{text: \" §fRounds: §b" + stats.getRoundsPlayed() + " §fplayed §7/ §b" + stats.getRoundsSurvived() + " §fsurvived\n\"}, ";*/
			
    		String who = player.getName().equalsIgnoreCase(name) ? "You have" : name + " has";
    		
			json += "{text: \" §f" + who + " played §b" + stats.getGamesPlayed() + " §fgame" + (stats.getGamesPlayed() == 1 ? "" : "s") + " of Colorfall.\n\"}, ";
			json += "{text: \" §6Notable stats:\n\"}, ";
			json += "{text: \" §b" + stats.getGamesWon() + " §fwin" + (stats.getGamesWon() == 1 ? "" : "s") + " §7/\"}, ";
			json += "{text: \" §b" + stats.getSuperiorWins() + " §fsuperior win" + (stats.getSuperiorWins() == 1 ? "" : "s") + " §7/\"}, ";
			json += "{text: \" §b" + stats.getDeaths() + " §fdeath" + (stats.getDeaths() == 1 ? "" : "s") + " §7/\"}, ";
			json += "{text: \" §b" + stats.getRoundsPlayed() + " §fround" + (stats.getRoundsPlayed() == 1 ? "" : "s") + " played §7/\"}, ";
			json += "{text: \" §b" + stats.getRoundsSurvived() + " §fround" + (stats.getRoundsSurvived() == 1 ? "" : "s") + " survived\n\"}, ";
			
			json += "{text: \" §6Powerups used:\n\"}, ";
			json += "{text: \" §b" + stats.getDyesUsed() + " §fdye" + (stats.getDyesUsed() == 1 ? "" : "s") + " §7/\"}, ";
			json += "{text: \" §b" + stats.getRandomizersUsed() + " §frandomizer" + (stats.getRandomizersUsed() == 1 ? "" : "s") + " §7/\"}, ";
			json += "{text: \" §b" + stats.getClocksUsed() + " §fclock" + (stats.getClocksUsed() == 1 ? "" : "s") + " §7/\"}, ";
			json += "{text: \" §b" + stats.getEnderpearlsUsed() + " §fenderpearl" + (stats.getEnderpearlsUsed() == 1 ? "" : "s") + " §7/\"}, ";
			json += "{text: \" §b" + stats.getSnowballsUsed() + " §fsnowball" + (stats.getSnowballsUsed() == 1 ? "" : "s") + " \n\"}, ";
			
			/*json += "{text: \" §fDyes used: §b" + stats.getDyesUsed() + "\n\"}, ";
			json += "{text: \" §fRandomizers used: §b" + stats.getRandomizersUsed() + "\n\"}, ";
			json += "{text: \" §fClocks used: §b" + stats.getClocksUsed() + "\n\"}, ";
			json += "{text: \" §fEnderpearls used: §b" + stats.getEnderpearlsUsed() + "\n\"}, ";
			json += "{text: \" §fSnowballs: §b" + stats.getSnowballsUsed() + " §fused §7/ §b" + stats.getSnowballsHit() + " §fsuccesful hits\n\"}, ";*/
			json += "{text: \" \n\"} ";
			json += "] ";
			
			sendJsonMessage(player, json);
    	}
    	else
    	{
    		Msg.send(player, " No stats recorded for " + name);
    	}
    }
    
    // Snatched from https://github.com/Webbeh/ActionBarAPI/blob/master/src/com/connorlinfoot/actionbarapi/ActionBarAPI.java
    public static void sendActionBar(Player player, String message)
    {
    	try
    	{
    		Class<?> c1 = Class.forName("org.bukkit.craftbukkit." + nmsver + ".entity.CraftPlayer");
    		Object p = c1.cast(player);
    		Object ppoc = null;
    		Class<?> c4 = Class.forName("net.minecraft.server." + nmsver + ".PacketPlayOutChat");
    		Class<?> c5 = Class.forName("net.minecraft.server." + nmsver + ".Packet");
    		
    		if(nmsver.equalsIgnoreCase("v1_8_R1") || !nmsver.startsWith("v1_8_"))
    		{
    			Class<?> c2 = Class.forName("net.minecraft.server." + nmsver + ".ChatSerializer");
    			Class<?> c3 = Class.forName("net.minecraft.server." + nmsver + ".IChatBaseComponent");
    			Method m3 = c2.getDeclaredMethod("a", new Class<?>[] {String.class});
    			Object cbc = c3.cast(m3.invoke(c2, "{\"text\": \"" + message + "\"}"));
    			ppoc = c4.getConstructor(new Class<?>[] {c3, byte.class}).newInstance(new Object[] {cbc, (byte) 2});
    		}
    		else
    		{
    			Class<?> c2 = Class.forName("net.minecraft.server." + nmsver + ".ChatComponentText");
    			Class<?> c3 = Class.forName("net.minecraft.server." + nmsver + ".IChatBaseComponent");
    			Object o = c2.getConstructor(new Class<?>[] {String.class}).newInstance(new Object[] {message});
    			ppoc = c4.getConstructor(new Class<?>[] {c3, byte.class}).newInstance(new Object[] {o, (byte) 2});
    		}
    		
    		Method m1 = c1.getDeclaredMethod("getHandle", new Class<?>[] {});
    		Object h = m1.invoke(p);
    		Field f1 = h.getClass().getDeclaredField("playerConnection");
    		Object pc = f1.get(h);
    		Method m5 = pc.getClass().getDeclaredMethod("sendPacket", new Class<?>[] {c5});
    		m5.invoke(pc, ppoc);
    	}
    	catch(Exception ex)
    	{
    		// Do nothing. This action bar message is a bonus if it works. If it doesn't, there's no fallback anyway, so just ignore it.
    	}
    }
    
    private boolean sendJsonMessage(Player player, String json)
	{
		if(player == null)
	    	return false;
	    
	    final CommandSender console = MinigamesPlugin.getInstance().getServer().getConsoleSender();
	    final String command = "minecraft:tellraw " + player.getName() + " " + json;
	
	    MinigamesPlugin.getInstance().getServer().dispatchCommand(console, command);
	    
	    return true;
	}
	
	@SuppressWarnings("unused")
	private void debug(Object o)
	{
		System.out.println(o);
	}
}
