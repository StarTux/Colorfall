package io.github.feydk.colorfall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

@RequiredArgsConstructor
public final class Round {
    private final ColorfallPlugin plugin;
    private long durationTicks;
    private boolean pvp;
    private boolean collision;
    private boolean randomize;
    private Map<ItemStack, Double> powerups = new HashMap<ItemStack, Double>();
    private double pvpChance;
    private double randomizeChance;

    public void setDuration(long ticks) {
        durationTicks = ticks;
    }

    public void setPvp(boolean pvp) {
        this.pvp = pvp;
    }

    public void setCollision(boolean collision) {
        this.collision = collision;
    }

    public void setRandomize(boolean randomize) {
        this.randomize = randomize;
    }

    public void addPowerup(ItemStack stack, double percentage) {
        powerups.put(stack, percentage);
    }

    public long getDuration() {
        return durationTicks;
    }

    public boolean getPvp() {
        return pvp;
    }

    public boolean getCollision() {
        return collision;
    }

    public boolean getRandomize() {
        return randomize;
    }

    public void setPvpChance(double chance) {
        pvpChance = chance;
    }

    public void setRandomizeChance(double chance) {
        randomizeChance = chance;
    }

    public double getPvpChance() {
        return pvpChance;
    }

    public double getRandomizeChance() {
        return randomizeChance;
    }

    public Round copy() {
        Round r = new Round(plugin);
        r.durationTicks = this.durationTicks;
        r.powerups = this.powerups;
        r.pvpChance = this.pvpChance;
        r.randomizeChance = this.randomizeChance;
        r.collision = this.collision;
        return r;
    }

    public List<ItemStack> getDistributedPowerups(ColorfallGame game) {
        List<ItemStack> list = new ArrayList<ItemStack>();
        if (powerups.size() == 0) {
            return list;
        }
        for (Map.Entry<ItemStack, Double> entry : powerups.entrySet()) {
            double chance = entry.getValue();
            double number = Math.random() * 100.0;
            if (number < chance) {
                ItemStack stack = entry.getKey();
                // Special case. Don't give an ink sack, but a random dye of one of the colors in the pool.
                if (stack.getType() == Material.INK_SAC) {
                    list.add(game.getGameMap().getDye());
                } else {
                    list.add(stack);
                }
            }
        }
        return list;
    }
}
