package io.github.feydk.colorfall.util;

import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.item.WardrobeItem;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public final class Players {
    private Players() { }

    public static void reset(Player player) {
        heal(player);
        clearInventory(player);
        player.setArrowsInBody(0);
        player.setGameMode(GameMode.ADVENTURE);
        player.setInvisible(false);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    public static void heal(Player player) {
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(20f);
    }

    public static void clearInventory(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i += 1) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType().isAir()) continue;
            Mytems mytems = Mytems.forItem(item);
            if (mytems != null && mytems.getMytem() instanceof WardrobeItem) {
                continue;
            }
            player.getInventory().clear(i);
        }
    }
}
