package io.github.feydk.colorfall;

import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;
import static com.cavetale.core.font.Unicode.tiny;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    private final ColorfallPlugin plugin;

    // Called whenever a player joins. This could be after a player disconnect during a game, for instance.
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.enter(player);
        if (plugin.getGame() == null) {
            player.setGameMode(GameMode.ADVENTURE);
            if (plugin.schedulingGame) plugin.remindToVote(player);
            return;
        }
        GamePlayer gp = plugin.getGamePlayer(player);
        gp.setDisconnectedTicks(0);
        if (gp.isSpectator()) {
            gp.setSpectator();
            return;
        }
        if (plugin.getGame() == null) return;
        switch (plugin.getGame().getState()) {
        case STARTED:
            gp.setSpectator();
            break;
        case INIT:
        case WAIT_FOR_PLAYERS:
        case COUNTDOWN_TO_START:
            break;
        default:
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.getPlayer().setWalkSpeed(0.2f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (plugin.getGame() == null) return;
        if (event.getCause() != TeleportCause.ENDER_PEARL) {
            return;
        }
        if (!plugin.getGame().getGameMap().isBlockWithinCuboid(event.getTo().getBlock())) {
            event.setCancelled(true);
        } else {
            final Player player = event.getPlayer();
            plugin.getGamePlayer(player).addEnderpearl();
            if (plugin.saveState.event) {
                plugin.saveState.addScore(player.getUniqueId(), 1);
                plugin.computeHighscore();
            }
        }
    }

    @EventHandler
    public void onProjectileThrownEvent(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Snowball) {
            Snowball snowball = (Snowball) event.getEntity();
            if (snowball.getShooter() instanceof Player) {
                Player playerThrower = (Player) snowball.getShooter();
                plugin.getGamePlayer(playerThrower).addSnowball();
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (event.getDamager() instanceof Snowball) {
            Snowball snowball = (Snowball) event.getDamager();
            if (snowball.getShooter() instanceof Player) {
                Player playerThrower = (Player) snowball.getShooter();
                plugin.getGamePlayer(playerThrower).addSnowballHit();
            }
        }
        Player player = (Player) event.getEntity();
        player.setHealth(20);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof ArmorStand) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        GamePlayer gp = plugin.getGamePlayer(player);
        // Ok, this isn't pretty. But..
        // It seems that when a player is teleported, any fall damage he is due to take is inflicted immediately. Even when falling into the void.
        // This peculiarity leads to the player dying twice, once by falling out of the world and then by taking fall damage.
        // So, to avoid double deaths I check if the player last died less than 500 ms ago.
        if ((gp.getLastDeath() > 0 && System.currentTimeMillis() - gp.getLastDeath() <= 500) || gp.diedThisRound()) {
            event.setCancelled(true);
            return;
        }
        final boolean didFallOut = event.getCause() == DamageCause.VOID
            || (event.getCause() == DamageCause.FALL && !plugin.game.gameMap.isBlockWithinCuboid(player.getLocation().getBlock()));
        if (didFallOut) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                    player.setHealth(20);
                    player.setVelocity(new Vector().zero());
                    Location location = gp.getSpawnLocation();
                    if (location == null) location = player.getWorld().getSpawnLocation();
                    player.teleport(location);
                    player.setHealth(20.0);
                    if (plugin.getGame() != null && plugin.getGame().getState() == GameState.STARTED && gp.isPlayer()) {
                        player.setGameMode(GameMode.SPECTATOR);
                        gp.died();
                    }
                });
        } else {
            player.setHealth(20);
            if (event.getCause() != DamageCause.ENTITY_ATTACK && event.getCause() != DamageCause.PROJECTILE) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        event.setFoodLevel(20);
        Bukkit.getScheduler().runTask(plugin, () -> {
                event.getEntity().setSaturation(20.0f);
            });
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (plugin.getGame() == null) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        switch (event.getAction()) {
        case RIGHT_CLICK_BLOCK:
        case RIGHT_CLICK_AIR:
            final Player player = event.getPlayer();
            if (plugin.getGame().onPlayerRightClick(player, player.getInventory().getItemInMainHand(), event.getClickedBlock())) {
                event.setCancelled(true);
            }
        default: return;
        }
    }
    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (plugin.getGame() == null) return;
        if (plugin.getGame().tryUseItemInHand(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (plugin.getGame() == null) return;
        if (plugin.getGame().tryUseItemInHand(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (plugin.getGame() == null) return;
        if (plugin.getGame().isDisallowPistons()) event.setCancelled(true);
    }

    @EventHandler
    public void onLeavesDecay(LeavesDecayEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerSpawnLocation(PlayerSpawnLocationEvent event) {
        if (plugin.getGame() == null || plugin.getGame().getGameMap() == null) {
            event.setSpawnLocation(Bukkit.getWorlds().get(0).getSpawnLocation());
        } else {
            event.setSpawnLocation(plugin.getGame().getSpawnLocation(event.getPlayer()));
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (plugin.getGame() == null) return;
        if (!(event.getEntity() instanceof Snowball proj)) return;
        if (!(event.getHitEntity() instanceof Player victim)) return;
        event.setCancelled(true);
        if (plugin.getGame().getRoundState() != RoundState.RUNNING) return;
        Vector velo = proj.getVelocity().normalize().setY(0);
        if (velo.length() < 0.01) return;
        velo = velo.setY(0.25).normalize();
        victim.setVelocity(velo.multiply(3.0));
        victim.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, proj.getLocation(), 48, 0.2, 0.2, 0.2, 0.0);
        victim.getWorld().playSound(proj.getLocation(), Sound.BLOCK_SNOW_BREAK, SoundCategory.MASTER, 2.0f, 1.0f);
        if (proj.getShooter() instanceof Player launcher) {
            launcher.playSound(launcher.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, SoundCategory.MASTER, 1.0f, 1.0f);
            victim.sendMessage(text(launcher.getName() + " hit you with a snowball", RED));
            if (plugin.saveState.event) {
                plugin.saveState.addScore(launcher.getUniqueId(), 3);
                plugin.computeHighscore();
            }
        }
    }

    @EventHandler
    void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().clear();
        event.setCancelled(true);
    }

    @EventHandler
    void onHangingBreak(HangingBreakEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    void onPlayerArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    protected void onPlayerHud(PlayerHudEvent event) {
        List<Component> lines = new ArrayList<>();
        lines.add(plugin.TITLE);
        if (plugin.game != null && !plugin.game.obsolete) {
            switch (plugin.game.state) {
            case WAIT_FOR_PLAYERS:
                lines.add(text("Waiting", GREEN));
                break;
            case COUNTDOWN_TO_START:
                lines.add(text("Get ready.. " + plugin.game.secondsLeft, GREEN));
                break;
            case STARTED:
                lines.add(join(noSeparators(), text(tiny("round "), GRAY), text(plugin.game.currentRoundIdx, WHITE)));
                lines.add(join(noSeparators(), text(tiny("time "), GRAY), text(plugin.game.secondsLeft, WHITE)));
                GamePlayer gp = plugin.getGamePlayer(event.getPlayer());
                if (gp != null && gp.isPlayer() && gp.isAlive()) {
                    lines.add(join(noSeparators(), text(tiny("lives "), GRAY), text(gp.getLivesLeft(), WHITE)));
                }
                break;
            case END:
                lines.add(text("Game Over " + plugin.game.secondsLeft, RED));
                break;
            default: break;
            }
            List<GamePlayer> gamePlayers = new ArrayList<>(plugin.gamePlayers.values());
            gamePlayers.removeIf(gp -> !gp.isPlayer());
            Collections.sort(gamePlayers, (a, b) -> Integer.compare(b.livesLeft, a.livesLeft));
            for (GamePlayer gamePlayer : gamePlayers) {
                Player player = gamePlayer.getPlayer();
                lines.add(join(noSeparators(),
                               Mytems.HEART.component,
                               text(gamePlayer.livesLeft, RED),
                               space(),
                               (player != null ? player.displayName() : text(gamePlayer.name))));
            }
        }
        if (plugin.saveState.event) {
            lines.addAll(Highscore.sidebar(plugin.highscore, TrophyCategory.MEDAL));
        }
        if (lines.isEmpty()) return;
        event.sidebar(PlayerHudPriority.HIGHEST, lines);
    }

    /**
     * Prevent falling block spawning.
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    private void onEntityChangeBlock(EntityChangeBlockEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    private void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity().getType() == EntityType.FALLING_BLOCK) {
            event.setCancelled(true);
        }
    }
}
