package io.github.feydk.colorfall;

import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

public enum Color {
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

    public final String niceName;

    Color() {
        niceName = Stream.of(name().split("_"))
            .map(s -> s.substring(0, 1) + s.substring(1).toLowerCase())
            .collect(Collectors.joining(" "));
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
        return Material.valueOf(this.name() + "_DYE");
    }

    static Color fromDyeMaterial(Material mat) {
        String name = mat.name();
        if (!name.endsWith("_DYE")) return null;
        name = name.substring(0, name.length() - 4);
        return Color.valueOf(name);
    }

    TextColor toTextColor() {
        return TextColor.color(toBukkitColor().asRGB());
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
