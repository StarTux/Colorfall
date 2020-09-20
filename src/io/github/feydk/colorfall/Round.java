package io.github.feydk.colorfall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class Round
{
    private ColorfallGame game;

    private long durationTicks;
    private boolean pvp;
    private boolean collision;
    private boolean randomize;
    private Map<ItemStack, Double> powerups = new HashMap<ItemStack, Double>();
    private double pvpChance;
    private double randomizeChance;

    public Round(ColorfallGame game)
    {
        this.game = game;
    }

    public void setDuration(long ticks)
    {
        durationTicks = ticks;
    }

    public void setPvp(boolean pvp)
    {
        this.pvp = pvp;
    }

    public void setCollision(boolean collision)
    {
        this.collision = collision;
    }

    public void setRandomize(boolean randomize)
    {
        this.randomize = randomize;
    }

    public void addPowerup(ItemStack stack, double percentage)
    {
        powerups.put(stack, percentage);
    }

    public long getDuration()
    {
        return durationTicks;
    }

    public boolean getPvp()
    {
        return pvp;
    }

    public boolean getCollision()
    {
        return collision;
    }

    public boolean getRandomize()
    {
        return randomize;
    }

    public void setPvpChance(double chance)
    {
        pvpChance = chance;
    }

    public void setRandomizeChance(double chance)
    {
        randomizeChance = chance;
    }

    public double getPvpChance()
    {
        return pvpChance;
    }

    public double getRandomizeChance()
    {
        return randomizeChance;
    }

    public Round copy()
    {
        Round r = new Round(game);
        r.durationTicks = this.durationTicks;
        r.game = this.game;
        r.powerups = this.powerups;
        r.pvpChance = this.pvpChance;
        r.randomizeChance = this.randomizeChance;
        r.collision = this.collision;

        return r;
    }

    public List<ItemStack> getDistributedPowerups(int lives)
    {
        List<ItemStack> list = new ArrayList<ItemStack>();

        if(powerups.size() == 0)
            return list;

        for(Entry<ItemStack, Double> entry : powerups.entrySet())
            {
                double chance = entry.getValue() * 3 / (double) (lives > 0 ? lives : 1);
                double number = Math.random() * 100;

                if(number < chance)
                    {
                        ItemStack stack = entry.getKey();

                        // Special case. Don't give an ink sack, but a random dye of one of the colors in the pool.
                        if(stack.getType() == Material.INK_SAC)
                            {
                                list.add(game.getMap().getDye());
                            }
                        else
                            list.add(stack);
                    }
            }

        return list;
    }
}
