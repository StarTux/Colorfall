package io.github.feydk.colorfall;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

enum Color {
    BLACK,
    BLUE,
    BROWN,
    CYAN,
    GRAY,
    GREEN,
    LIGHT_BLUE,
    LIGHT_GRAY,
    LIME,
    MAGENTA,
    ORANGE,
    PINK,
    PURPLE,
    RED,
    WHITE,
    YELLOW;

    final String niceName;

    Color() {
        String[] toks = name().split("_");
        StringBuilder sb = new StringBuilder(toks[0].substring(0, 1)).append(toks[0].substring(1).toUpperCase());
        for (int i = 1; i < toks.length; i += 1) sb.append(" ").append(toks[i].substring(0, 1)).append(toks[i].substring(1).toLowerCase());
        this.niceName = sb.toString();
    }

    org.bukkit.Color toBukkitColor() {
        switch (this) {
        case WHITE: return org.bukkit.Color.WHITE;
        case ORANGE: return org.bukkit.Color.ORANGE;
        case MAGENTA: return org.bukkit.Color.FUCHSIA;
        case LIGHT_BLUE: return org.bukkit.Color.fromRGB(128, 128, 255);
        case YELLOW: return org.bukkit.Color.YELLOW;
        case LIME: return org.bukkit.Color.LIME;
        case PINK: return org.bukkit.Color.fromRGB(255, 128, 128);
        case GRAY: return org.bukkit.Color.GRAY;
        case LIGHT_GRAY: return org.bukkit.Color.SILVER;
        case CYAN: return org.bukkit.Color.AQUA;
        case PURPLE: return org.bukkit.Color.PURPLE;
        case BLUE: return org.bukkit.Color.BLUE;
        case BROWN: return org.bukkit.Color.MAROON;
        case GREEN: return org.bukkit.Color.GREEN;
        case RED: return org.bukkit.Color.RED;
        case BLACK: return org.bukkit.Color.BLACK;
        default: return org.bukkit.Color.TEAL;
        }
    }

    static Color fromEnumString(String str) {
        for (Color color: values()) {
            if (str.startsWith(color.name() + "_")) return color;
        }
        throw new IllegalArgumentException("Color not found: " + str);
    }

    static Color fromBlockData(BlockData blockData) {
        return fromEnumString(blockData.getMaterial().name());
    }

    Material getDyeMaterial() {
        switch (this) {
        case BLACK: return Material.INK_SAC;
        case BLUE: return Material.LAPIS_LAZULI;
        case BROWN: return Material.COCOA_BEANS;
        case CYAN: return Material.CYAN_DYE;
        case GRAY: return Material.GRAY_DYE;
        case GREEN: return Material.CACTUS_GREEN;
        case LIGHT_BLUE: return Material.LIGHT_BLUE_DYE;
        case LIGHT_GRAY: return Material.LIGHT_GRAY_DYE;
        case LIME: return Material.LIME_DYE;
        case MAGENTA: return Material.MAGENTA_DYE;
        case ORANGE: return Material.ORANGE_DYE;
        case PINK: return Material.PINK_DYE;
        case PURPLE: return Material.PURPLE_DYE;
        case RED: return Material.ROSE_RED;
        case WHITE: return Material.BONE_MEAL;
        case YELLOW: return Material.DANDELION_YELLOW;
        default: return Material.POTATO;
        }
    }

    static Color fromDyeMaterial(Material mat) {
        switch (mat) {
        case INK_SAC: return Color.BLACK;
        case LAPIS_LAZULI: return Color.BLUE;
        case COCOA_BEANS: return Color.BROWN;
        case CYAN_DYE: return Color.CYAN;
        case GRAY_DYE: return Color.GRAY;
        case CACTUS_GREEN: return Color.GREEN;
        case LIGHT_BLUE_DYE: return Color.LIGHT_BLUE;
        case LIGHT_GRAY_DYE: return Color.LIGHT_GRAY;
        case LIME_DYE: return Color.LIME;
        case MAGENTA_DYE: return Color.MAGENTA;
        case ORANGE_DYE: return Color.ORANGE;
        case PINK_DYE: return Color.PINK;
        case PURPLE_DYE: return Color.PURPLE;
        case ROSE_RED: return Color.RED;
        case BONE_MEAL: return Color.WHITE;
        case DANDELION_YELLOW: return Color.YELLOW;
        default: return null;
        }
    }

    ChatColor toChatColor() {
        switch (this) {
        case BLACK: return ChatColor.BLACK;
        case BLUE: return ChatColor.DARK_BLUE;
        case BROWN: return ChatColor.GOLD; // Close enough!?
        case CYAN: return ChatColor.DARK_AQUA;
        case GRAY: return ChatColor.DARK_GRAY;
        case GREEN: return ChatColor.DARK_GREEN;
        case LIGHT_BLUE: return ChatColor.BLUE;
        case LIGHT_GRAY: return ChatColor.GRAY;
        case LIME: return ChatColor.GREEN;
        case MAGENTA: return ChatColor.LIGHT_PURPLE;
        case ORANGE: return ChatColor.GOLD;
        case PINK: return ChatColor.RED;
        case PURPLE: return ChatColor.DARK_PURPLE;
        case RED: return ChatColor.DARK_RED;
        case WHITE: return ChatColor.WHITE;
        case YELLOW: return ChatColor.YELLOW;
        default: return ChatColor.MAGIC;
        }
    }

    /**
     * Replace the color part of the material name with the new color.
     */
    BlockData stain(BlockData orig) {
        Color oldColor = fromBlockData(orig);
        String name = name() + orig.getMaterial().name().substring(oldColor.name().length());
        return Material.valueOf(name).createBlockData();
    }
}
