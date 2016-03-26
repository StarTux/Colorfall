package io.github.feydk.colorfall;

import java.util.ArrayList;
import java.util.List;

import com.avaje.ebean.SqlQuery;
import com.avaje.ebean.SqlRow;
import com.winthier.minigames.MinigamesPlugin;

public class PlayerStats
{
	private String name;
	private int gamesPlayed;
	private int gamesWon;
	private int deaths;
	private int roundsPlayed;
	private int roundsSurvived;
	private int superiorWins;
	private int dyesUsed;
	private int randomizersUsed;
	private int clocksUsed;
	private int enderpearlsUsed;
	private int snowballsUsed;
	private int snowballsHit;
	private String mapId;
	
	public int getGamesPlayed()
	{
		return gamesPlayed;
	}
	
	public void setGamesPlayed(int games)
	{
		this.gamesPlayed = games;
	}
	
	public int getGamesWon()
	{
		return gamesWon;
	}
	
	public void setGamesWon(int won)
	{
		this.gamesWon = won;
	}

	public int getDeaths()
	{
		return deaths;
	}
	
	public void setDeaths(int rips)
	{
		this.deaths = rips;
	}

	public int getRoundsPlayed()
	{
		return roundsPlayed;
	}
	
	public void setRoundsPlayed(int rounds)
	{
		this.roundsPlayed = rounds;
	}

	public int getRoundsSurvived()
	{
		return roundsSurvived;
	}

	public int getSuperiorWins()
	{
		return superiorWins;
	}
	
	public void setSuperiorWins(int wins)
	{
		this.superiorWins = wins;
	}

	public int getDyesUsed()
	{
		return dyesUsed;
	}
	
	public void setDyesUsed(int dyes)
	{
		this.dyesUsed = dyes;
	}

	public int getRandomizersUsed()
	{
		return randomizersUsed;
	}
	
	public void setRandomizersUsed(int randomizers)
	{
		this.randomizersUsed = randomizers;
	}

	public int getClocksUsed()
	{
		return clocksUsed;
	}
	
	public void setClocksUsed(int clocks)
	{
		this.clocksUsed = clocks;
	}

	public int getEnderpearlsUsed()
	{
		return enderpearlsUsed;
	}
	
	public void setEnderpearlsUsed(int pearls)
	{
		this.enderpearlsUsed = pearls;
	}

	public int getSnowballsUsed()
	{
		return snowballsUsed;
	}
	
	public void setSnowballsUsed(int balls)
	{
		this.snowballsUsed = balls;
	}

	public int getSnowballsHit()
	{
		return snowballsHit;
	}
	
	public void setSnowballsHit(int hits)
	{
		this.snowballsHit = hits;
	}
	
	public String getName()
	{
		return name;
	}
	
	public void setName(String name)
	{
		this.name = name;
	}
	
	public String getMapId()
	{
		return mapId;
	}

	public void setMapId(String mapId)
	{
		this.mapId = mapId;
	}

	public void loadOverview(String name)
	{
		String sql = 
			"select count(id) as games, sum(winner) as wins, sum(superior_win) as superior_wins, sum(deaths) as deaths, sum(rounds_played) as rounds_played, " + 
			"sum(rounds_survived) as rounds_survived, sum(dyes_used) as dyes_used, sum(randomizers_used) as randomizers_used, sum(clocks_used) as clocks_used, " +
			"sum(enderpearls_used) as enderpearls_used, sum(snowballs_used) as snowballs_used, sum(snowballs_hit) as snowballs_hit " +
			"from colorfall_playerstats " +
			"where player_name = :name";
		
		SqlQuery query = MinigamesPlugin.getInstance().getDatabase().createSqlQuery(sql);
		query.setParameter("name", name);
		
		SqlRow row = query.findUnique();
		
		gamesPlayed = row.getInteger("games");
		
		if(gamesPlayed > 0)
		{
			gamesWon = row.getInteger("wins");
			deaths = row.getInteger("deaths");
			roundsPlayed = row.getInteger("rounds_played");
			roundsSurvived = row.getInteger("rounds_survived");
			superiorWins = row.getInteger("superior_wins");
			dyesUsed = row.getInteger("dyes_used");
			randomizersUsed = row.getInteger("randomizers_used");
			clocksUsed = row.getInteger("clocks_used");
			enderpearlsUsed = row.getInteger("enderpearls_used");
			snowballsUsed = row.getInteger("snowballs_used");
			snowballsHit = row.getInteger("snowballs_hit");
		}
	}
	
	public static List<PlayerStats> loadTopWinners()
	{
		String sql = 
			"select player_name, sum(winner) as wins, sum(superior_win) as superior_wins " +
			"from colorfall_playerstats " +
			"where sp_game = 0 " +
			"group by player_name " +
			"having wins > 0 " +
			"order by wins desc, superior_wins desc limit 0, 3";
		
		List<PlayerStats> list = new ArrayList<PlayerStats>();
		
		for(SqlRow row : MinigamesPlugin.getInstance().getDatabase().createSqlQuery(sql).findList())
		{
			PlayerStats obj = new PlayerStats();
			obj.setName(row.getString("player_name"));
			obj.setGamesWon(row.getInteger("wins"));
			obj.setSuperiorWins(row.getInteger("superior_wins"));
			
			list.add(obj);
		}
		
		return list;
	}
	
	public static List<PlayerStats> loadTopPainters()
	{
		String sql = 
			"select player_name, sum(dyes_used) as dyes " +
			"from colorfall_playerstats " +
			"where sp_game = 0 " +
			"group by player_name " +
			"having dyes > 0 " +
			"order by dyes desc limit 0, 3";
		
		List<PlayerStats> list = new ArrayList<PlayerStats>();
		
		for(SqlRow row : MinigamesPlugin.getInstance().getDatabase().createSqlQuery(sql).findList())
		{
			PlayerStats obj = new PlayerStats();
			obj.setName(row.getString("player_name"));
			obj.setDyesUsed(row.getInteger("dyes"));
			
			list.add(obj);
		}
		
		return list;
	}
	
	public static List<PlayerStats> loadTopClockers()
	{
		String sql = 
			"select player_name, sum(clocks_used) as clocks " +
			"from colorfall_playerstats " +
			"where sp_game = 0 " +
			"group by player_name " +
			"having clocks > 0 " +
			"order by clocks desc limit 0, 3";
		
		List<PlayerStats> list = new ArrayList<PlayerStats>();
		
		for(SqlRow row : MinigamesPlugin.getInstance().getDatabase().createSqlQuery(sql).findList())
		{
			PlayerStats obj = new PlayerStats();
			obj.setName(row.getString("player_name"));
			obj.setClocksUsed(row.getInteger("clocks"));
			
			list.add(obj);
		}
		
		return list;
	}
	
	public static List<PlayerStats> loadTopPearlers()
	{
		String sql = 
			"select player_name, sum(enderpearls_used) as pearls " +
			"from colorfall_playerstats " +
			"where sp_game = 0 " +
			"group by player_name " +
			"having pearls > 0 " +
			"order by pearls desc limit 0, 3";
		
		List<PlayerStats> list = new ArrayList<PlayerStats>();
		
		for(SqlRow row : MinigamesPlugin.getInstance().getDatabase().createSqlQuery(sql).findList())
		{
			PlayerStats obj = new PlayerStats();
			obj.setName(row.getString("player_name"));
			obj.setEnderpearlsUsed(row.getInteger("pearls"));
			
			list.add(obj);
		}
		
		return list;
	}
	
	public static List<PlayerStats> loadTopSnowballers()
	{
		String sql = 
			"select player_name, sum(snowballs_used) as balls, sum(snowballs_hit) as hits " +
			"from colorfall_playerstats " +
			"where sp_game = 0 " +
			"group by player_name " +
			"having balls > 0 " +
			"order by balls desc, hits desc limit 0, 3";
		
		List<PlayerStats> list = new ArrayList<PlayerStats>();
		
		for(SqlRow row : MinigamesPlugin.getInstance().getDatabase().createSqlQuery(sql).findList())
		{
			PlayerStats obj = new PlayerStats();
			obj.setName(row.getString("player_name"));
			obj.setSnowballsUsed(row.getInteger("balls"));
			obj.setSnowballsHit(row.getInteger("hits"));
			
			list.add(obj);
		}
		
		return list;
	}
	
	public static List<PlayerStats> loadTopRandomizers()
	{
		String sql = 
			"select player_name, sum(randomizers_used) as randomizers " +
			"from colorfall_playerstats " +
			"where sp_game = 0 " +
			"group by player_name " +
			"having randomizers > 0 " +
			"order by randomizers desc limit 0, 3";
		
		List<PlayerStats> list = new ArrayList<PlayerStats>();
		
		for(SqlRow row : MinigamesPlugin.getInstance().getDatabase().createSqlQuery(sql).findList())
		{
			PlayerStats obj = new PlayerStats();
			obj.setName(row.getString("player_name"));
			obj.setRandomizersUsed(row.getInteger("randomizers"));
			
			list.add(obj);
		}
		
		return list;
	}
	
	public static List<PlayerStats> loadTopDeaths()
	{
		String sql = 
			"select player_name, sum(deaths) as rips " +
			"from colorfall_playerstats " +
			"where sp_game = 0 " +
			"group by player_name " +
			"having rips > 0 " +
			"order by rips desc limit 0, 3";
		
		List<PlayerStats> list = new ArrayList<PlayerStats>();
		
		for(SqlRow row : MinigamesPlugin.getInstance().getDatabase().createSqlQuery(sql).findList())
		{
			PlayerStats obj = new PlayerStats();
			obj.setName(row.getString("player_name"));
			obj.setDeaths(row.getInteger("rips"));
			
			list.add(obj);
		}
		
		return list;
	}
	
	public static List<PlayerStats> loadTopContestants()
	{
		String sql = 
			"select player_name, count(id) as games, sum(rounds_played) as rounds " +
			"from colorfall_playerstats " +
			"where sp_game = 0 " +
			"group by player_name " +
			"order by games desc, rounds desc limit 0, 3";
		
		List<PlayerStats> list = new ArrayList<PlayerStats>();
		
		for(SqlRow row : MinigamesPlugin.getInstance().getDatabase().createSqlQuery(sql).findList())
		{
			PlayerStats obj = new PlayerStats();
			obj.setName(row.getString("player_name"));
			obj.setGamesPlayed(row.getInteger("games"));
			obj.setRoundsPlayed(row.getInteger("rounds"));
			
			list.add(obj);
		}
		
		return list;
	}
}