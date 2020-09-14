package io.github.feydk.colorfall;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Utility class for world loading
 */
public final class ColorfallLoader
{
    private static int worldId = 0;
    private ColorfallLoader() { }

    public static World loadWorld(ColorfallGame plugin, String worldName)
    {
        // Copy the world
        File source = new File(plugin.getDataFolder(), "maps");
        source = new File(source, worldName);
        if (!source.isDirectory()) throw new IllegalStateException("Not a directory: " + source);
        String newName;
        File dest;
        do {
            newName = String.format("colorfall-%04d", worldId++);
            dest = new File(Bukkit.getWorldContainer(), newName);
            if (!dest.exists()) break;
            dest = null;
        } while (dest == null);
        copyFileStructure(source, dest);
        // Load it
        File file = new File(dest, "config.yml");
        if (!file.isFile()) throw new IllegalStateException("Not a file: " + file);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        return loadWorld(newName, config);
    }

    public static World loadWorld(String worldname, YamlConfiguration config)
    {
        WorldCreator wc = new WorldCreator(worldname);
        wc.environment(World.Environment.valueOf(config.getString("world.Environment")));
        wc.generateStructures(config.getBoolean("world.GenerateStructures"));
        wc.generator(config.getString("world.Generator"));
        wc.type(WorldType.valueOf(config.getString("world.WorldType")));
        World world = wc.createWorld();
        world.setAutoSave(false);
        return world;
    }

    public static void copyFileStructure(File source, File target)
    {
        try
            {
                ArrayList<String> ignore = new ArrayList<>(Arrays.asList("uid.dat", "session.lock"));

                if(!ignore.contains(source.getName()))
                    {
                        if(source.isDirectory())
                            {
                                if(!target.exists())
                                    {
                                        if(!target.mkdirs())
                                            throw new IOException("Couldn't create world directory!");
                                    }

                                String[] files = source.list();

                                for(String file : files)
                                    {
                                        File srcFile = new File(source, file);
                                        File destFile = new File(target, file);
                                        copyFileStructure(srcFile, destFile);
                                    }
                            }
                        else
                            {
                                InputStream in = new FileInputStream(source);
                                OutputStream out = new FileOutputStream(target);

                                byte[] buffer = new byte[1024];
                                int length;

                                while((length = in.read(buffer)) > 0)
                                    out.write(buffer, 0, length);

                                in.close();
                                out.close();
                            }
                    }
            }
        catch (IOException e)
            {
                throw new RuntimeException(e);
            }
    }

    public static void deleteFiles(File path)
    {
        if(path.exists())
            {
                for(File file : path.listFiles())
                    {
                        if(file.isDirectory())
                            deleteFiles(file);
                        else
                            file.delete();
                    }

                path.delete();
            }
    }
}
