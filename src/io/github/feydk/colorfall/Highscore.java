package io.github.feydk.colorfall;

import com.avaje.ebean.SqlRow;
import com.avaje.ebean.SqlUpdate;
import com.winthier.minigames.MinigamesPlugin;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class Highscore
{
	// For some reason eclipse complains about lombok, so I just implemented this like normal.
	class Entry
	{
		private String name;
		private int count;
		private int rounds;
		private int wins;
		
		public Entry(String name, int count, int rounds, int wins)
		{
			this.name = name;
			this.count = count;
			this.rounds = rounds;
			this.wins = wins;
		}
		
		public int getCount()
		{
			return count;
		}
		
		public int getRounds()
		{
			return rounds;
		}
		
		public int getWins()
		{
			return wins;
		}
		
		public String getName()
		{
			return name;
		}
	}

	public void init()
	{
		System.out.println("Setting up Colorfall highscore");
		
		final String sql =
		"CREATE TABLE IF NOT EXISTS `Colorfall` (" +
		" `id` INT(11) NOT NULL AUTO_INCREMENT," +
		" `game_uuid` VARCHAR(40) NOT NULL," +
		" `player_uuid` VARCHAR(40) NOT NULL," +
		" `player_name` VARCHAR(16) NOT NULL," +
		" `start_time` DATETIME NOT NULL," +
		" `end_time` DATETIME NOT NULL," +
		" `rounds` INT(11) NOT NULL," +
		" `winner` BOOLEAN NOT NULL," +
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
		
		System.out.println("Done setting up Colorfall highscore");
	}

	public void store(UUID gameUuid, UUID playerUuid, String playerName, Date startTime, Date endTime, int rounds, boolean winner)
	{
		if(endTime == null)
			endTime = new Date();
		
		final String sql =
		"INSERT INTO `Colorfall` (" +
		" `game_uuid`, `player_uuid`, `player_name`, `start_time`, `end_time`, `rounds`, `winner`" +
		") VALUES (" +
		" :gameUuid, :playerUuid, :playerName, :startTime, :endTime, :rounds, :winner" +
		")";
		
		try
		{
			SqlUpdate update = MinigamesPlugin.getInstance().getDatabase().createSqlUpdate(sql);
			update.setParameter("gameUuid", gameUuid);
			update.setParameter("playerUuid", playerUuid);
			update.setParameter("playerName", playerName);
			update.setParameter("startTime", startTime);
			update.setParameter("endTime", endTime);
			update.setParameter("rounds", rounds);
			update.setParameter("winner", winner);
			update.execute();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	List<Entry> list()
	{
		final String sql =
		"SELECT player_name, COUNT(*) AS count, SUM(rounds) AS rounds, SUM(winner) AS wins FROM Colorfall GROUP BY player_uuid ORDER BY wins DESC, rounds DESC LIMIT 10";
		
		List<Entry> result = new ArrayList<>();
		
		for(SqlRow row : MinigamesPlugin.getInstance().getDatabase().createSqlQuery(sql).findList())
		{
			String name = row.getString("player_name");
			int count = row.getInteger("count");
			int rounds = row.getInteger("rounds");
			int wins = row.getInteger("wins");
			
			result.add(new Entry(name, count, rounds, wins));
		}
		
		return result;
	}
}