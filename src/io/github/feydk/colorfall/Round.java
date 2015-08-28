package io.github.feydk.colorfall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class Round
{
	private ColorfallGame game;
	
	private long durationTicks;
	private boolean pvp;
	private boolean randomize;
	private Map<EntityType, Double> spawns = new HashMap<EntityType, Double>();
	private Map<ItemStack, Double> powerups = new HashMap<ItemStack, Double>();
	private Map<PotionEffectType, Integer> effects = new HashMap<PotionEffectType, Integer>();
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
	
	public void setRandomize(boolean randomize)
	{
		this.randomize = randomize;
	}
	
	public void setSpawns(Map<EntityType, Double> spawns)
	{
		this.spawns = spawns;
	}
	
	public void addSpawn(EntityType type, double percentage)
	{
		spawns.put(type, percentage);
	}
	
	public void addEffect(PotionEffectType type, int duration)
	{
		effects.put(type, duration);
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
		r.effects = this.effects;
		r.game = this.game;
		r.powerups = this.powerups;
		r.pvpChance = this.pvpChance;
		r.randomizeChance = this.randomizeChance;
		r.spawns = this.spawns;
		
		return r;
	}
	
	public Map<EntityType, Double> getSpawns()
	{
		return spawns;
	}
	
	public List<PotionEffect> getDealtEffects()
	{
		List<PotionEffect> list = new ArrayList<PotionEffect>();
		
		for(Entry<PotionEffectType, Integer> entry : effects.entrySet())
		{
			list.add(new PotionEffect(entry.getKey(), entry.getValue(), 1));
		}
		
		return list;
	}
	
	@SuppressWarnings("deprecation")
	public List<ItemStack> getDistributedPowerups()
	{
		List<ItemStack> list = new ArrayList<ItemStack>();
		
		if(powerups.size() == 0)
			return list;
		
		for(Entry<ItemStack, Double> entry : powerups.entrySet())
		{
			double number = Math.random() * 100;
			
			if(number - entry.getValue() <= 0)
			{
				ItemStack stack = entry.getKey();
				
				// Special case. Don't give an ink sack, but a random dye of one of the colors in the pool.
				if(stack.getTypeId() == 351)
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