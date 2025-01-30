package io.github.feydk.colorfall;

import com.cavetale.mytems.util.BlockColor;
import io.github.feydk.colorfall.util.Players;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import lombok.Getter;
import lombok.Value;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

@Getter
public final class GameMap {
    // The blocks to replace with colors (placeholder blocks).
    private Set<BlockData> coloredBlocks = new HashSet<BlockData>();
    // The list of possible colors for the map.
    private List<BlockData> colorPool = new ArrayList<BlockData>();
    // A list of blocks that have been replaced. Ie. the blocks you stand on.
    private List<Block> replacedBlocks = new ArrayList<>();

    // A list of blocks that are removed (and then restored).
    private Set<BlockState> removedBlocksSolid = new HashSet<BlockState>();
    private Set<BlockState> removedBlocksUnsolid = new HashSet<BlockState>();
    private Set<Point2D> processedChunks = new HashSet<Point2D>();
    private List<Location> spawnLocations = new ArrayList<Location>();
    private List<String> credits = new ArrayList<String>();
    private int time;
    private boolean lockTime;
    private int creditSignsFound;
    private int timeSignsFound;

    private World world;
    private ColorfallGame game;
    private final String worldName;
    private final int chunkRadius = 8;
    private boolean spawnLocationsRandomized;
    private int spawnLocationIter = 0;

    // Stuff used for boundaries.
    private List<Location> boundaries = new ArrayList<Location>();
    private double minX;
    private double minZ;
    private double minY;
    private double maxX;
    private double maxZ;
    private double maxY;
    private Map<Block, Entity> highlightEntities = new HashMap<>();

    public GameMap(final String worldName, final ColorfallGame game, final World world) {
        this.worldName = worldName;
        this.world = world;
        this.game = game;
    }

    public int getStartingTime() {
        return time;
    }

    public boolean getLockTime() {
        return lockTime;
    }

    public BlockData getRandomFromColorPool() {
        if (colorPool.isEmpty()) return null;
        Random r = new Random(System.currentTimeMillis());
        return colorPool.get(r.nextInt(colorPool.size()));
    }

    @SuppressWarnings("unused")
    private Block getRandomFromReplaced() {
        Random r = new Random(System.currentTimeMillis());

        return replacedBlocks.get(r.nextInt(replacedBlocks.size()));
    }

    public String getCredits() {
        if (credits.size() > 0) {
            if (credits.size() == 1) {
                return credits.get(0);
            } else {
                String c = "";
                for (int i = 0; i < credits.size(); i++) {
                    c += credits.get(i);
                    int left = credits.size() - (i + 1);
                    if (left == 1) {
                        c += " and ";
                    } else if (left > 1) {
                        c += ", ";
                    }
                }
                return c;
            }
        }
        return "";
    }

    public void animateBlocks(BlockData currentColor) {
        for (Block b : replacedBlocks) {
            if (b.getType() != Material.AIR && b.getBlockData().equals(currentColor)) {
                Color color = Color.fromBlockData(currentColor);
                world.spawnParticle(Particle.DUST, b.getLocation().add(.5, 1.5, .5),
                                    1, .5f, .5f, .5f, .01f,
                                    new Particle.DustOptions(color.toBukkitColor(), 1.0f));
            }
        }
    }

    public void clearHighlightBlocks() {
        for (Entity e : highlightEntities.values()) e.remove();
        highlightEntities.clear();
    }

    public void highlightBlocks(BlockData currentColor) {
        for (Block b : replacedBlocks) {
            Entity entity = highlightEntities.get(b);
            if (b.getType() == Material.AIR || !b.getBlockData().equals(currentColor)) {
                if (entity != null) {
                    entity.remove();
                    highlightEntities.remove(b);
                }
                continue;
            }
            if (entity != null && !entity.isDead()) continue;
            BlockColor blockColor = BlockColor.of(currentColor.getMaterial());
            if (blockColor == null) return;
            entity = world.spawn(b.getLocation().add(0.5, 0.5, 0.5), BlockDisplay.class, e -> {
                    e.setPersistent(false);
                    e.setGlowing(true);
                    e.setBlock(currentColor);
                    e.setGlowColorOverride(blockColor.bukkitColor);
                    e.setBrightness(new BlockDisplay.Brightness(15, 15));
                    e.setTransformation(new Transformation(new Vector3f(-0.5125f, -0.5125f, -0.5125f),
                                                           new AxisAngle4f(0f, 0f, 0f, 0f),
                                                           new Vector3f(1.05f, 1.05f, 1.05f),
                                                           new AxisAngle4f(0f, 0f, 0f, 0f)));
                });
            highlightEntities.put(b, entity);
        }
    }

    public boolean isColoredBlock(Block block) {
        if (block == null) return false;
        for (BlockData blockData : colorPool) {
            if (blockData.equals(block.getBlockData())) {
                return true;
            }
        }
        return false;
    }

    private void warn(String msg) {
        game.getPlugin().getLogger().warning("[" + worldName + "] " + msg);
    }

    private void info(String msg) {
        game.getPlugin().getLogger().info("[" + worldName + "] " + msg);
    }

    public Location dealSpawnLocation() {
        if (spawnLocations.isEmpty()) {
            warn("No [SPAWN] points were set. Falling back to world spawn.");
            game.debugStrings.add("No [SPAWN] points were set.");
            return world.getSpawnLocation();
        }
        if (!spawnLocationsRandomized) {
            Random random = new Random(System.currentTimeMillis());
            spawnLocationsRandomized = true;
            Collections.shuffle(spawnLocations, random);
        }
        if (spawnLocationIter >= spawnLocations.size()) {
            spawnLocationIter = 0;
        }
        int i = spawnLocationIter++;
        return spawnLocations.get(i);
    }

    public void process() {
        info("Processing...");
        Chunk startingChunk = world.getSpawnLocation().getChunk();
        int cx = startingChunk.getX();
        int cz = startingChunk.getZ();
        // Crawl the map in a <chunkRadius> chunk radius in all directions.
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                // Find signs to register the blocks used in the map.
                findChunkSigns(x, z);
            }
        }
        // Determine boundaries.
        if (boundaries.size() == 2) {
            Location b1 = boundaries.get(0);
            Location b2 = boundaries.get(1);
            if (b1.getX() >= b2.getX()) {
                minX = b2.getX();
                maxX = b1.getX() + 1.0;
            } else {
                minX = b1.getX();
                maxX = b2.getX() + 1.0;
            }
            if (b1.getY() >= b2.getY()) {
                minY = b2.getY();
                maxY = b1.getY() + 1.0;
            } else {
                minY = b1.getY();
                maxY = b2.getY() + 1.0;
            }
            if (b1.getZ() >= b2.getZ()) {
                minZ = b2.getZ();
                maxZ = b1.getZ() + 1.0;
            } else {
                minZ = b1.getZ();
                maxZ = b2.getZ() + 1.0;
            }
        }
        // Then crawl the map again, this time finding all the blocks that needs to be replaced with color blocks.
        for (Point2D point : processedChunks) {
            findBlocksToBeReplaced((int) point.getX(), (int) point.getY());
        }
        info("Done processing:"
             + " [block]=" + coloredBlocks.size()
             + " [color]=" + colorPool.size()
             + " [spawn]=" + spawnLocations.size()
             + " [boundary]=" + boundaries.size()
             + " [credits]=" + creditSignsFound
             + " [time]=" + timeSignsFound);
    }

    // Searches a chunk for map configuration signs.
    private void findChunkSigns(int x, int z) {
        Point2D cc = new Point2D(x, z);
        if (processedChunks.contains(cc)) return;
        processedChunks.add(cc);
        // Process the chunk.
        Chunk chunk = world.getChunkAt(x, z);
        chunk.addPluginChunkTicket(game.getPlugin());
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Sign) {
                Sign signBlock = (Sign) state;
                Block attachedBlock;
                if (state.getBlockData() instanceof org.bukkit.block.data.type.Sign) {
                    attachedBlock = state.getBlock().getRelative(BlockFace.DOWN);
                } else if (state.getBlockData() instanceof org.bukkit.block.data.type.WallSign) {
                    org.bukkit.block.data.type.WallSign signBlockData = (org.bukkit.block.data.type.WallSign) state.getBlockData();
                    attachedBlock = state.getBlock().getRelative(signBlockData.getFacing().getOppositeFace());
                } else {
                    continue;
                }
                List<String> lines = new ArrayList<>();
                for (Component line : signBlock.getSide(Side.FRONT).lines()) {
                    lines.add(PlainTextComponentSerializer.plainText().serialize(line).toLowerCase());
                }
                String firstLine = lines.get(0);
                if (firstLine != null && firstLine.startsWith("[") && firstLine.endsWith("]")) {
                    // A sign with [BLOCK] defines that this block is
                    // a valid color block in the map. These are the
                    // blocks that will be replaced with a block from
                    // the color pool.
                    if (firstLine.equals("[block]")) {
                        BlockData data = attachedBlock.getBlockData();
                        coloredBlocks.add(data);
                        state.getBlock().setType(Material.AIR);
                        attachedBlock.setType(Material.AIR);
                        // A sign with [COLOR] defines that this block
                        // goes into the color pool. These are the
                        // colors that players will have to find and
                        // stand on during the game.
                    } else if (firstLine.equals("[color]")) {
                        BlockData data = attachedBlock.getBlockData();
                        colorPool.add(data);
                        state.getBlock().setType(Material.AIR);
                        attachedBlock.setType(Material.AIR);
                        // Spawn locations.
                    } else if (firstLine.equals("[spawn]")) {
                        Location location = state.getBlock().getLocation().add(.5, .5, .5);
                        Vector lookAt = world.getSpawnLocation().toVector().subtract(location.toVector());
                        location.setDirection(lookAt);
                        spawnLocations.add(location);
                        state.getBlock().setType(Material.AIR);
                        // Boundaries.
                    } else if (firstLine.equals("[boundary]")) {
                        Location location = state.getBlock().getLocation();
                        boundaries.add(location);
                        state.getBlock().setType(Material.AIR);
                        attachedBlock.setType(Material.AIR);
                        // Credits.
                    } else if (firstLine.equals("[credits]")) {
                        for (int i = 1; i < 4; ++i) {
                            String credit = lines.get(i);
                            if (credit != null && !credit.isEmpty()) {
                                credits.add(credit);
                            }
                        }
                        state.getBlock().setType(Material.AIR);
                        creditSignsFound += 1;
                    } else if (firstLine.equals("[time]")) {
                        String t = lines.get(1);
                        if (t != null && !t.isEmpty()) {
                            try {
                                time = Integer.parseInt(t);
                            } catch (NumberFormatException e) { }
                        }
                        if (time > -1) {
                            String l = lines.get(2);
                            if (l != null && !l.isEmpty()) {
                                if (l.toLowerCase().equals("lock")) lockTime = true;
                            }
                        }
                        state.getBlock().setType(Material.AIR);
                        timeSignsFound += 1;
                    }
                }
            }
        }
    }

    // Searches a chunk for blocks that needs to be replaced.
    private void findBlocksToBeReplaced(int x, int z) {
        Chunk chunk = world.getChunkAt(x, z);
        for (int cx = 0; cx < 16; cx++) {
            for (int cy = 0; cy < 256; cy++) {
                for (int cz = 0; cz < 16; cz++) {
                    Block b = chunk.getBlock(cx, cy, cz);
                    // Check if b (a block in the chunk) is the same type as any of the colored blocks that needs to be replaced and add it to the list if so.
                    COLOR_BLOCKS: for (BlockData blockData : coloredBlocks) {
                        if (b.getBlockData().equals(blockData)) {
                            replacedBlocks.add(b);
                            break COLOR_BLOCKS;
                        }
                    }
                }
            }
        }
    }

    public void randomizeBlocks() {
        replaceBlocks();
    }

    // Replaces blocks with color blocks.
    // This is a part of the map preparation logic that one potentially could have a little fun with, using different algorithms and such.
    // For now it's real simple by distributing every color from the pool evenly(-ish) throughout the map.
    // This makes sure that every color is represented.
    private void replaceBlocks() {
        final int numberOfBlocks = replacedBlocks.size();
        final int numberOfColors = colorPool.size();
        if (numberOfColors == 0 || numberOfBlocks == 0) {
            game.denyStart = true;
            warn("No [BLOCK] and/or [COLOR] configured. Skipping the part that replaces blocks in the map");
            if (numberOfBlocks == 0) {
                game.debugStrings.add("No [BLOCK] blocks were configured.");
            }
            if (numberOfColors == 0) {
                game.debugStrings.add("No [COLOR] blocks were configured.");
            }
            return;
        }
        // Need to have exactly two boundaries.
        if (boundaries.size() > 0 && boundaries.size() != 2) {
            game.denyStart = true;
            warn("Map boundaries misconfigured. Skipping the part that replaces blocks in the map");
            game.debugStrings.add("There must be two and only two [BOUNDARY] signs.");
            return;
        }
        // Determine correct and wrong colors
        final BlockData correctColor = game.getCurrentColor();
        final List<BlockData> wrongColors = new ArrayList<>();
        for (BlockData color : colorPool) {
            if (!color.equals(correctColor)) wrongColors.add(color);
        }
        if (wrongColors.isEmpty()) wrongColors.add(correctColor); // failsafe
        Collections.shuffle(wrongColors);
        // Calculate new color distribution.  There is always a
        // certain number of correct colors plus some wrong colors.
        double correctFraction = 0.5;
        for (int i = 0; i < game.getCurrentRoundIdx(); i += 1) {
            correctFraction *= 0.9;
        }
        final int correctColorCount = Math.max(1, (int) ((double) numberOfBlocks * correctFraction));
        final int wrongColorCount = numberOfBlocks - correctColorCount;
        game.getPlugin().getLogger().info("Correct Colors: "
                                          + Math.round(correctFraction * 100.0) + "% "
                                          + correctColorCount + "/" + numberOfBlocks);
        final List<BlockData> newColors = new ArrayList<>(numberOfBlocks);
        for (int i = 0; i < correctColorCount; i += 1) {
            newColors.add(correctColor);
        }
        for (int i = 0; i < wrongColorCount; i += 1) {
            newColors.add(wrongColors.get(i % wrongColors.size()));
        }
        Collections.shuffle(newColors);
        for (int i = 0; i < numberOfBlocks; i += 1) {
            replacedBlocks.get(i).setBlockData(newColors.get(i), false);
        }
    }

    // Remove blocks that are not the currently active color.
    public void removeBlocks(BlockData currentColor) {
        removedBlocksSolid.clear();
        removedBlocksUnsolid.clear();
        // I know, I know. Point has X and Y which is conceptually totally wrong in this context since Y is actually the Z coordinate, but it works ;)
        for (Point2D point : processedChunks) {
            Chunk chunk = world.getChunkAt((int) point.getX(), (int) point.getY());
            for (int cx = 0; cx < 16; cx++) {
                for (int cy = 0; cy < 256; cy++) {
                    // Do this twice; first time for unsolid blocks, second time for solid blocks.
                    for (int cz = 0; cz < 16; cz++) {
                        Block b = chunk.getBlock(cx, cy, cz);
                        if (b.getType().isSolid()) continue;
                        boolean isOk = true;
                        if (boundaries.size() > 0) {
                            isOk = isBlockWithinCuboid(b);
                        }
                        if (isOk && b.getType() != Material.AIR && !(b.getBlockData().equals(currentColor))) {
                            removedBlocksUnsolid.add(b.getState());
                            b.setType(Material.AIR, false);
                        }
                    }
                    for (int cz = 0; cz < 16; cz++) {
                        Block b = chunk.getBlock(cx, cy, cz);
                        if (!b.getType().isSolid()) continue;
                        boolean isOk = true;
                        if (boundaries.size() > 0) {
                            isOk = isBlockWithinCuboid(b);
                        }
                        if (isOk && b.getType() != Material.AIR && !(b.getBlockData().equals(currentColor))) {
                            removedBlocksSolid.add(b.getState());
                            b.setType(Material.AIR, false);
                        }
                    }
                }
            }
        }
    }

    public void restoreBlocks(List<Block> paintedBlocks) {
        for (BlockState entry : removedBlocksSolid) {
            entry.update(true, false);
        }
        for (BlockState entry : removedBlocksUnsolid) {
            entry.update(true, false);
        }
        // If any blocks were painted by players, restore their original color.
        if (paintedBlocks.size() > 0) {
            for (Block b : paintedBlocks) {
                List<MetadataValue> data = b.getMetadata("org-color");
                if (data != null && data.size() > 0) {
                    b.setBlockData((BlockData) data.get(0).value());
                    b.removeMetadata("org-color", game.getPlugin());
                }
            }
        }
    }

    public boolean isBlockWithinCuboid(Block b) {
        if (boundaries.size() > 0) {
            double x = b.getX();
            double y = b.getY();
            double z = b.getZ();
            return new Vector(x, y, z).isInAABB(new Vector(minX, minY, minZ), new Vector(maxX, maxY, maxZ));
        }
        return true;
    }

    public boolean isWithinBoundariesOrBelow(Location location) {
        if (boundaries.size() == 0) return true;
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ && y <= maxY;
    }

    public ItemStack getDye() {
        BlockData data = getRandomFromColorPool();
        Color color = data != null
            ? Color.fromBlockData(data)
            : Color.BLACK;
        ItemStack newStack = new ItemStack(color.getDyeMaterial());
        newStack.editMeta(meta -> {
                meta.displayName(Component.text(color.niceName, color.toTextColor()));
                meta.lore(List.of(Component.text("Turn a colored block", NamedTextColor.GRAY),
                                  Component.text("into this color.", NamedTextColor.GRAY),
                                  Component.text("The block will reset", NamedTextColor.GRAY),
                                  Component.text("when the round is over.", NamedTextColor.GRAY)));
            });
        return newStack;
    }

    /**
     * Remove ender pearls still in the air to prevent cheating.
     */
    public void removeEnderPearls() {
        for (Entity e: world.getEntities()) {
            if (e.getType() == EntityType.ENDER_PEARL) {
                e.remove();
            }
        }
    }

    public void cleanUp() {
        world.removePluginChunkTickets(game.getPlugin());
        clearHighlightBlocks();
        for (Player player : world.getPlayers()) {
            Players.heal(player);
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            Players.reset(player);
        }
        File dir = world.getWorldFolder();
        if (!Bukkit.unloadWorld(world, false)) {
            throw new IllegalStateException("Cannot unload world: " + world.getName());
        }
        ColorfallLoader.deleteFiles(dir);
    }
}

@Value
class Point2D {
    public final int x;
    public final int y;
}
