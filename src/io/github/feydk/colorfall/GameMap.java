package io.github.feydk.colorfall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import javafx.geometry.Point2D;

import org.bukkit.Chunk;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;

public class GameMap
{
	private Set<ColorBlock> coloredBlocks = new HashSet<>();
	private List<ColorBlock> colorPool = new ArrayList<>();
	private Set<Point2D> processedChunks = new HashSet<>();
	private List<Block> replacedBlocks = new ArrayList<>();
	private Map<Location, ColorBlock> removedBlocks = new HashMap<Location, ColorBlock>();
	private List<Location> spawnLocations = new ArrayList<>();
	private List<String> credits = new ArrayList<>();
	private int time;
	private boolean lockTime;
	
	private World world;
	private ColorfallGame game;
	private int chunkRadius;
	private boolean spawnLocationsRandomized;
	private int spawnLocationIter = 0;
	
	class ColorBlock
    {
    	int TypeId;
    	byte DataId;
    }
	
	public GameMap(int chunkRadius, ColorfallGame game)
	{
		this.chunkRadius = chunkRadius;
		this.game = game;
	}
	
	public int getStartingTime()
	{
		return time;
	}
	
	public boolean getLockTime()
	{
		return lockTime;
	}
	
	public ColorBlock getRandomFromColorPool()
	{
		Random r = new Random(System.currentTimeMillis());
		
		return colorPool.get(r.nextInt(colorPool.size()));
	}
	
	@SuppressWarnings("unused")
	private Block getRandomFromReplaced()
	{
		Random r = new Random(System.currentTimeMillis());
		
		return replacedBlocks.get(r.nextInt(replacedBlocks.size()));
	}
	
	public String getCredits()
	{
		if(credits.size() > 0)
		{
			if(credits.size() == 1)
			{
				return credits.get(0);
			}
			else
			{
				String c = "";
				
				for(int i = 0; i < credits.size(); i++)
				{
					c += credits.get(i);
					
					int left = credits.size() - (i + 1);
					
					if(left == 1)
						c += " and ";
					else if(left > 1)
						c += ", ";
				}
				
				return c;
			}
		}
		
		return "";
	}
	
	@SuppressWarnings("deprecation")
	public void animateBlocks(ColorBlock currentColor)
	{
		for(Block b : replacedBlocks)
		{
			if(b.getType() != Material.AIR && b.getTypeId() == currentColor.TypeId && b.getData() == currentColor.DataId)
            {
				world.spigot().playEffect(b.getLocation().add(.5, 1.5, .5), Effect.COLOURED_DUST, 0, 0, .5f, .5f, .5f, .01f, 5, 50);
				//(Location location, Effect effect, int id, int data, float offsetX, float offsetY, float offsetZ, float speed, int particleCount, int radius)
            }
		}
	}
	
	/*public void spawnMobs(Map<EntityType, Double> mobs)
	{
		int numberOfBlocks = replacedBlocks.size();
		List<Block> spawnedBlocks = new ArrayList<Block>(); 
		
		for(Entry<EntityType, Double> entry : mobs.entrySet())
		{
			double numberOfSpawns = ((double)numberOfBlocks / 100D) * entry.getValue();
			
			Block spawnBlock;
			
			for(int i = 1; i <= numberOfSpawns; i++)
			{
				// Pick a random colored block to spawn on. Must be unique.
				while(spawnedBlocks.contains((spawnBlock = getRandomFromReplaced())))
				{
					// do nothing
				}
												
				world.spawnEntity(spawnBlock.getLocation().add(0, 1, 0), entry.getKey());
				 
				spawnedBlocks.add(spawnBlock);
			}
		}
	}*/
	
	@SuppressWarnings("deprecation")
	public boolean isColoredBlock(Block block)
	{
		if(block == null)
			return false;
		
		for(ColorBlock cb : colorPool)
		{
			if(cb.DataId == block.getData() && cb.TypeId == block.getTypeId())
				return true;
		}
		
		return false;
	}
	
	public Location dealSpawnLocation()
    {
    	if(spawnLocations.isEmpty())
    	{
    		if(game.debug)
    		{
    			game.getLogger().warning("No [SPAWN] points were set. Falling back to world spawn.");
    			game.debugStrings.add("No [SPAWN] points were set.");
    		}
    		
    		return world.getSpawnLocation();
    	}
    	
        if(!spawnLocationsRandomized)
        {
        	Random random = new Random(System.currentTimeMillis());
            spawnLocationsRandomized = true;
            Collections.shuffle(spawnLocations, random);
        }
        
        if(spawnLocationIter >= spawnLocations.size())
        	spawnLocationIter = 0;
        
        int i = spawnLocationIter++;
        
        return spawnLocations.get(i);
    }
	
	public void process(Chunk startingChunk)
    {
		world = startingChunk.getWorld();
		
    	int cx = startingChunk.getX();
    	int cz = startingChunk.getZ();
    	
    	// Crawl the map in a <chunkRadius> chunk radius in all directions.
        for(int dx = -chunkRadius; dx <= chunkRadius; dx++)
        {
            for(int dz = -chunkRadius; dz <= chunkRadius; dz++)
            {
                int x = cx + dx;
                int z = cz + dz;
                
                // Find signs to register the blocks used in the map.
                findChunkSigns(x, z);
            }
        }
        
        // Then crawl the map again, this time finding all the blocks that needs to be replaced with color blocks.
        for(Point2D point : processedChunks)
        {
        	//debug("chunk " + point.getX() + ", " + point.getY());
        	findBlocksToBeReplaced((int)point.getX(), (int)point.getY());
        }
        
        // Finally replace the blocks we found in the step above.
        // The reason I don't do all this in one step is that I want to guarantee a certain "quality" of the block replacement across all chunks.
        replaceBlocks();
    }
	
	// Searches a chunk for map configuration signs. 
    @SuppressWarnings("deprecation")
	private void findChunkSigns(int x, int z)
    {
    	Point2D cc = new Point2D(x, z);
    	
    	if(processedChunks.contains(cc))
    		return;
    	
        processedChunks.add(cc);
        
        // Process the chunk.
        Chunk chunk = world.getChunkAt(x, z);
        chunk.load();
        
        for(BlockState state : chunk.getTileEntities())
        {
            if(state instanceof Sign)
            {
            	org.bukkit.material.Sign signMaterial = (org.bukkit.material.Sign)state.getData();
            	Sign signBlock = (Sign)state;
                Block attachedBlock = state.getBlock().getRelative(signMaterial.getAttachedFace());
                
                String firstLine = signBlock.getLine(0).toLowerCase();
                
                if(firstLine != null && firstLine.startsWith("[") && firstLine.endsWith("]"))
                {
                	// A sign with [BLOCK] defines that this block is a valid color block in the map. These are the blocks that will be replaced with a block from the color pool.
                    if(firstLine.equals("[block]"))
                    {
                    	ColorBlock cb = new ColorBlock();
                    	cb.TypeId = attachedBlock.getTypeId();
                    	cb.DataId = attachedBlock.getData();
                    	coloredBlocks.add(cb);
                    	
                        state.getBlock().setType(Material.AIR);
                        attachedBlock.setType(Material.AIR);
                    }
                    // A sign with [COLOR] defines that this block goes into the color pool. These are the colors that players will have to find and stand on during the game.
                    else if(firstLine.equals("[color]"))
                    {
                    	ColorBlock cb = new ColorBlock();
                    	cb.TypeId = attachedBlock.getTypeId();
                    	cb.DataId = attachedBlock.getData();
                    	colorPool.add(cb);
                    	
                        state.getBlock().setType(Material.AIR);
                        attachedBlock.setType(Material.AIR);
                    }
                    else if(firstLine.equals("[spawn]"))
                    {
                    	Location location = state.getBlock().getLocation().add(.5, .5, .5);
                        Vector lookAt = world.getSpawnLocation().toVector().subtract(location.toVector());
                        location.setDirection(lookAt);
                        spawnLocations.add(location);
                    	
                        state.getBlock().setType(Material.AIR);
                    }
                    else if(firstLine.equals("[credits]"))
                    {
                    	for(int i = 1; i < 4; ++i)
                    	{
                    		String credit = signBlock.getLine(i);
                            
                    		if(credit != null && !credit.isEmpty())
                    			credits.add(credit);
                        }
                    	
                        state.getBlock().setType(Material.AIR);
                        attachedBlock.setType(Material.AIR);
                    }
                    else if(firstLine.equals("[time]"))
                    {
                    	String t = signBlock.getLine(1);
                    	
                    	if(t != null && !t.isEmpty())
                    	{
                    		try
                    		{
                    			time = Integer.parseInt(t);
                    		}
                    		catch(NumberFormatException e)
                    		{}
                    	}
                    	
                    	if(time > -1)
                    	{
                    		String l = signBlock.getLine(2);
                    		
                    		if(l != null && !l.isEmpty())
                    		{
                    			if(l.toLowerCase().equals("lock"))
                    				lockTime = true;
                    		}
                    	}
                    	
                        state.getBlock().setType(Material.AIR);
                        attachedBlock.setType(Material.AIR);
                    }
                }
            }
        }
    }
    
    // Searches a chunk for blocks that needs to be replaced.
    @SuppressWarnings("deprecation")
	private void findBlocksToBeReplaced(int x, int z)
    {
    	Chunk chunk = world.getChunkAt(x, z);
    	//chunk.load();
    	
    	for(int cx = 0; cx <= 16; cx++)
    	{
    	    for(int cy = 0; cy <= 256; cy++)
    	    {
    	        for(int cz = 0; cz <= 16; cz++)
    	        {
    	            Block b = chunk.getBlock(cx, cy, cz);
    	            
    	            // Check if b (a block in the chunk) is the same type as any of the colored blocks that needs to be replaced and add it to the list if so.
    	            for(ColorBlock block : coloredBlocks)
    	            {
    	            	if(b.getTypeId() == block.TypeId && b.getData() == block.DataId)
    	            	{
    	            		replacedBlocks.add(b);
    	            	}
    	            }
    	        }
    	    }
    	}
    }
    
    public void randomizeBlocks()
    {
    	replaceBlocks();
    }
    
    // Replaces blocks with color blocks.
    // This is a part of the map preparation logic that one potentially could have a little fun with, using different algorithms and such.
    // For now it's real simple by distributing every color from the pool evenly(-ish) throughout the map.
    // This makes sure that every color is represented.
    @SuppressWarnings("deprecation")
	private void replaceBlocks()
    {
    	int numberOfBlocks = replacedBlocks.size();
    	int numberOfColors = colorPool.size();
    	
    	if(numberOfColors == 0 || numberOfBlocks == 0)
    	{
    		game.denyStart = true;
    		
    		if(game.debug)
    		{
    			game.getLogger().warning("No [BLOCK] and/or [COLOR] configured. Skipping the part that replaces blocks in the map");
    			
    			if(numberOfBlocks == 0)
    				game.debugStrings.add("No [BLOCK] blocks were configured.");
    			
    			if(numberOfColors == 0)
    				game.debugStrings.add("No [COLOR] blocks were configured.");
    		}
    		
    		return;
    	}
    	
    	int ofEachColor = numberOfBlocks / numberOfColors;
    	
    	// Shuffle the list of blocks a bit to get some randomization.
    	Collections.shuffle(replacedBlocks);
    	int count = 0;
    	
    	for(ColorBlock color : colorPool)
    	{
    		for(int i = 1; i <= ofEachColor; i++)
    		{
    			// Color the next block.
    			Block toColor = replacedBlocks.get(count);
    			toColor.setTypeId(color.TypeId);
    			toColor.setData((byte)color.DataId);
    			
    			count++;
    		}
    	}
    	
    	// Handle uneven ofEachColor.
    	if(count < numberOfBlocks)
    	{
    		Random r = new Random(System.currentTimeMillis());
    		
    		for(int i = count; i < numberOfBlocks; i++)
    		{
    			// Pick a random color.
    			ColorBlock color = colorPool.get(r.nextInt(numberOfColors));
    			
    			Block toColor = replacedBlocks.get(i);
    			toColor.setTypeId(color.TypeId);
    			toColor.setData((byte)color.DataId);
    			
    			count++;
    		}
    	}
    }
    
    // Remove blocks that are not the currently active color.
    @SuppressWarnings("deprecation")
	public void removeBlocks(ColorBlock currentColor)
    {
    	removedBlocks.clear();
    	
    	// I know, I know. Point has X and Y which is conceptually totally wrong in this context since Y is actually the Z coordinate, but it works ;)
    	for(Point2D point : processedChunks)
    	{
    		Chunk chunk = world.getChunkAt((int)point.getX(), (int)point.getY());
    		
    		for(int cx = 0; cx <= 16; cx++)
        	{
        	    for(int cy = 0; cy <= 256; cy++)
        	    {
        	        for(int cz = 0; cz <= 16; cz++)
        	        {
        	            Block b = chunk.getBlock(cx, cy, cz);
        	            
        	            if(b.getType() != Material.AIR && !(b.getTypeId() == currentColor.TypeId && b.getData() == currentColor.DataId))
        	            {
        	            	ColorBlock cb = new ColorBlock();
        	            	cb.TypeId = b.getTypeId();
                        	cb.DataId = b.getData();
        	            	
        	            	removedBlocks.put(b.getLocation(), cb);
        	            	b.setType(Material.AIR);
        	            }
        	        }
        	    }
        	}
        }
    }
    
    @SuppressWarnings("deprecation")
	public void restoreBlocks(List<Block> paintedBlocks)
    {
    	for(Entry<Location, ColorBlock> entry : removedBlocks.entrySet())
    	{
    		Block b = world.getBlockAt(entry.getKey());
    		
    		b.setTypeId(entry.getValue().TypeId);
    		b.setData(entry.getValue().DataId);
    	}
    	
    	// If any blocks were painted by players, restore their original color.
    	if(paintedBlocks.size() > 0)
    	{
    		for(Block b : paintedBlocks)
    		{
    			List<MetadataValue> data = b.getMetadata("org-color");
    			
    			if(data != null && data.size() > 0)
    				b.setData(data.get(0).asByte());
    		}
    	}
    }
}