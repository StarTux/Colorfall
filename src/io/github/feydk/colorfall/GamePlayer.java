package io.github.feydk.colorfall;

import java.util.Date;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.avaje.ebean.SqlUpdate;
import com.winthier.minigames.MinigamesPlugin;

public class GamePlayer
{
	private final ColorfallGame game;
	private final UUID uuid;
	private PlayerType type;
	private String name;
	private boolean isReady;
	private boolean isAlive;
	private long lastDeath;
	private long disconnectedTics;
	private Location spawnLocation;
	private boolean statsRecorded;
	private boolean didPlay = false;
	private boolean joinedAsSpectator = false;
	
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
	
	static enum PlayerType
	{
		PLAYER,
		SPECTATOR
	}
	
	public GamePlayer(ColorfallGame game, UUID uuid)
	{
		this.game = game;
		this.uuid = uuid;
		isAlive = true;
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
		game.getPlayer(uuid).getPlayer().setGameMode(GameMode.ADVENTURE);
		didPlay = true;
	}
	
	// Set player as spectator.
	public void setSpectator()
	{
		type = PlayerType.SPECTATOR;
		
		if(game.getPlayer(uuid).isOnline())
			game.getPlayer(uuid).getPlayer().setGameMode(GameMode.SPECTATOR);
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
		if(spawnLocation == null)
			spawnLocation = game.getMap().dealSpawnLocation();
		
		return spawnLocation;
	}
	
	/*public Location getSafeSpawnLocation()
	{
		List<Location> players = new ArrayList<Location>();
		
		for(PlayerInfo info : game.getPlayers())
		{
			players.add(info.getPlayer().getLocation());
		}
		
		
		
		spawnLocation = game.getMap().dealSpawnLocation();
		
		// Check that no players are standing there :)
		
		
		return spawnLocation;
	}*/
	
	// Set amount of lives. Note: should only be called once when the game starts.
	public void setLives(int lives)
	{
		this.livesLeft = lives;
		
		game.getScoreboard().setPlayerScore(game.getPlayer(uuid).getPlayer(), lives);
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
		
		if(livesLeft > 0)
			livesLeft--;
		
		Player player = game.getPlayer(uuid).getPlayer();
		
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
		String sql = "update `colorfall_playerstats` set `player_name` = :name where `player_uuid` = :uuid";
		
		try
		{
			SqlUpdate update = MinigamesPlugin.getInstance().getDatabase().createSqlUpdate(sql);
			update.setParameter("name", getName());
			update.setParameter("uuid", this.uuid);
			update.execute();
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
			" :gameUuid, :playerUuid, :playerName, :startTime, :endTime, :roundsPlayed, :roundsSurvived, :deaths, :livesLeft, :superiorWin, :dyesUsed, :randomizersUsed, :clocksUsed, :enderpearlsUsed, :snowballsUsed, :snowballsHit, :winner, :spGame, :mapId" +
			")";
		
		try
		{
			SqlUpdate update = MinigamesPlugin.getInstance().getDatabase().createSqlUpdate(sql);
			update.setParameter("gameUuid", game.gameUuid);
			update.setParameter("playerUuid", uuid);
			update.setParameter("playerName", name);
			update.setParameter("startTime", startTime);
			update.setParameter("endTime", endTime);
			update.setParameter("roundsPlayed", roundsPlayed);
			update.setParameter("roundsSurvived", roundsSurvived);
			update.setParameter("deaths", deaths);
			update.setParameter("livesLeft", livesLeft);
			update.setParameter("superiorWin", superior);
			update.setParameter("dyesUsed", dyesUsed);
			update.setParameter("randomizersUsed", randomizersUsed);
			update.setParameter("clocksUsed", clocksUsed);
			update.setParameter("enderpearlsUsed", enderpearlsUsed);
			update.setParameter("snowballsUsed", snowballsUsed);
			update.setParameter("snowballsHit", snowballsHit);
			update.setParameter("winner", winner);
			update.setParameter("spGame", !moreThanOnePlayed);
			update.setParameter("mapId", mapId);
			update.execute();
			
			game.getLogger().info("Stored player stats of " + name);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
        
        statsRecorded = true;
    }
}