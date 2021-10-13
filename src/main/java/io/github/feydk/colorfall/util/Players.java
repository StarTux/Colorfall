package io.github.feydk.colorfall.util;

import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

public final class Players {
    private Players() { }

    public static void reset(Player player) {
        heal(player);
        player.getInventory().clear();
        player.setArrowsInBody(0);
        player.setGameMode(GameMode.ADVENTURE);
        player.setInvisible(false);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    public static void heal(Player player) {
        player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(20f);
    }
}
