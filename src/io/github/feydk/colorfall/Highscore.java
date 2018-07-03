// package io.github.feydk.colorfall;

// import java.sql.PreparedStatement;
// import java.sql.ResultSet;
// import java.util.ArrayList;
// import java.util.Date;
// import java.util.List;
// import java.util.UUID;

// public class Highscore
// {
//     // For some reason eclipse complains about lombok, so I just implemented this like normal.
//     class Entry
//     {
//         private String name;
//         private int count;
//         private int rounds;
//         private int wins;

//         public Entry(String name, int count, int rounds, int wins)
//         {
//             this.name = name;
//             this.count = count;
//             this.rounds = rounds;
//             this.wins = wins;
//         }

//         public int getCount()
//         {
//             return count;
//         }

//         public int getRounds()
//         {
//             return rounds;
//         }

//         public int getWins()
//         {
//             return wins;
//         }

//         public String getName()
//         {
//             return name;
//         }
//     }

//     public void init()
//     {
//         System.out.println("Setting up Colorfall highscore");

//         final String sql =
//             "CREATE TABLE IF NOT EXISTS `Colorfall` (" +
//             " `id` INT(11) NOT NULL AUTO_INCREMENT," +
//             " `game_uuid` VARCHAR(40) NOT NULL," +
//             " `player_uuid` VARCHAR(40) NOT NULL," +
//             " `player_name` VARCHAR(16) NOT NULL," +
//             " `start_time` DATETIME NOT NULL," +
//             " `end_time` DATETIME NOT NULL," +
//             " `rounds` INT(11) NOT NULL," +
//             " `winner` BOOLEAN NOT NULL," +
//             " PRIMARY KEY (`id`)" +
//             ")";

//         try
//             {
//                 plugin.db.executeUpdate(sql);
//             }
//         catch(Exception e)
//             {
//                 e.printStackTrace();
//             }

//         System.out.println("Done setting up Colorfall highscore");
//     }

//     public void store(UUID gameUuid, UUID playerUuid, String playerName, Date startTime, Date endTime, int rounds, boolean winner)
//     {
//         if(endTime == null)
//             endTime = new Date();

//         final String sql =
//             "INSERT INTO `Colorfall` (" +
//             " `game_uuid`, `player_uuid`, `player_name`, `start_time`, `end_time`, `rounds`, `winner`" +
//             ") VALUES (" +
//             " ?, ?, ?, ?, ?, ?, ?" +
//             ")";

//         try (PreparedStatement update = MinigamesPlugin.getInstance().getDb().getConnection().prepareStatement(sql))
//             {
//                 update.setString(1, gameUuid.toString());
//                 update.setString(2, playerUuid.toString());
//                 update.setString(3, playerName);
//                 update.setTimestamp(4, new java.sql.Timestamp(startTime.getTime()));
//                 update.setTimestamp(5, new java.sql.Timestamp(endTime.getTime()));
//                 update.setInt(6, rounds);
//                 update.setBoolean(7, winner);
//                 update.executeUpdate();
//             }
//         catch(Exception e)
//             {
//                 e.printStackTrace();
//             }
//     }

//     List<Entry> list()
//     {
//         final String sql =
//             "SELECT player_name, COUNT(*) AS count, SUM(rounds) AS rounds, SUM(winner) AS wins FROM Colorfall GROUP BY player_uuid ORDER BY wins DESC, rounds DESC LIMIT 10";

//         List<Entry> result = new ArrayList<>();

//         try (ResultSet row = MinigamesPlugin.getInstance().getDb().executeQuery(sql))
//             {
//                 while (row.next())
//                     {
//                         String name = row.getString("player_name");
//                         int count = row.getInt("count");
//                         int rounds = row.getInt("rounds");
//                         int wins = row.getInt("wins");
//                         result.add(new Entry(name, count, rounds, wins));
//                     }
//             }
//         catch (Exception e)
//             {
//                 e.printStackTrace();
//             }
//         return result;
//     }
// }
