package io.github.feydk.colorfall.util;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Msg {
    private Msg() { }

    public static boolean sendRaw(Player player, Object json) {
        return sendJsonMessage(player, new Gson().toJson(json));
    }

    public static boolean sendJsonMessage(Player player, String json) {
        if (player == null) return false;
        final CommandSender console = Bukkit.getConsoleSender();
        final String command = "minecraft:tellraw " + player.getName() + " " + json;
        Bukkit.dispatchCommand(console, command);
        return true;
    }

    public static void debug(Object o) {
        System.out.println(o);
    }

    public static String format(String msg, Object... args) {
        msg = ChatColor.translateAlternateColorCodes('&', msg);
        if (args.length > 0) msg = String.format(msg, args);
        return msg;
    }

    public static void send(Player player, String msg, Object... args) {
        player.sendMessage(format(msg, args));
    }

    public static void sendActionBar(Player player, String msg) {
        player.sendActionBar(format(msg));
    }

    public static void showTitle(Player player, String title, String subtitle) {
        player.sendTitle(format(title), format(subtitle));
    }

    public static void showTitle(Player player, String title, String subtitle, int a, int b, int c) {
        player.sendTitle(format(title), format(subtitle), a, b, c);
    }

    public static void announce(String msg, Object... args) {
        for (Player player: Bukkit.getOnlinePlayers()) {
            send(player, msg, args);
        }
    }

    public static Object button(String chat, String tooltip, String command) {
        Map<String, Object> map = new HashMap<>();
        map.put("text", format(chat));
        Map<String, Object> map2 = new HashMap<>();
        map.put("clickEvent", map2);
        map2.put("action", "run_command");
        map2.put("value", command);
        map2 = new HashMap();
        map.put("hoverEvent", map2);
        map2.put("action", "show_text");
        map2.put("value", format(tooltip));
        return map;
    }
}
