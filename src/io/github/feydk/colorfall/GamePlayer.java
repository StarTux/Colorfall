package io.github.feydk.colorfall;

import java.util.Date;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class GamePlayer
{
	private final ColorfallGame game;
	private final UUID uuid;
	private PlayerType type;
	private String name;
	private boolean isReady;
	private int lives;
	private boolean isAlive;
	private long lastDeath;
	private long disconnectedTics;
	private Location spawnLocation;
	private boolean highscoreRecorded;
	private boolean didPlay = false;
	private Date startTime;
	private Date endTime;
	private int rounds = 0;
	private boolean winner = false;
	private boolean joinedAsSpectator = false;
	
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
		this.lives = lives;
		
		game.getScoreboard().setPlayerScore(game.getPlayer(uuid).getPlayer(), lives);
	}
	
	public boolean isAlive()
	{
		return isAlive;
	}
	
	// Register that the player died. If the player has used all his lives, he is set as spectator.
	@SuppressWarnings("deprecation")
	public void died()
	{
		// See onEntityDamage for why I keep track of this.
		lastDeath = System.currentTimeMillis();
		
		if(lives > 0)
			lives--;
		
		Player player = game.getPlayer(uuid).getPlayer();
		
		if(lives == 0)
		{
			isAlive = false;
			setSpectator();
			
			game.getScoreboard().setPlayerEliminated(player);
			
			game.onPlayerElimination(player);
		}
		else
		{
			game.getScoreboard().setPlayerScore(player, lives);
			
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
		rounds++;
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
	
	@SuppressWarnings("incomplete-switch")
	public void recordHighscore()
    {
        switch(game.state)
        {
        	case INIT:
        	case WAIT_FOR_PLAYERS:
        		return;
        }
        
        if(!didPlay)
        	return;
        
        if(highscoreRecorded)
        	return;
        
        game.highscore.store(game.gameUuid, uuid, name, startTime, endTime, rounds, winner);
        game.getLogger().info("Stored highscore of " + name);
        
        highscoreRecorded = true;
    }
}