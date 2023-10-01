package io.github.feydk.colorfall;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.mytems.util.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import static com.winthier.creative.review.MapReviewMenu.starComponent;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

public final class ColorfallCommand extends AbstractCommand<ColorfallPlugin> {
    protected ColorfallCommand(final ColorfallPlugin plugin) {
        super(plugin, "colorfall");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("vote").arguments("[path]")
            .completers(CommandArgCompleter.supplyList(() -> List.copyOf(plugin.colorfallWorlds.keySet())))
            .description("Vote on a map")
            .hidden(true)
            .playerCaller(this::vote);
    }

    private boolean vote(Player player, String[] args) {
        if (args.length == 0) {
            if (!plugin.schedulingGame) throw new CommandWarn("The vote is over");
            openMapBook(player);
            return true;
        } else if (args.length == 1) {
            if (!plugin.schedulingGame) throw new CommandWarn("The vote is over");
            ColorfallWorld colorfallWorld = plugin.colorfallWorlds.get(args[0]);
            if (colorfallWorld == null) throw new CommandWarn("Map not found!");
            plugin.saveState.votes.put(player.getUniqueId(), colorfallWorld.getPath());
            player.sendMessage(text("You voted for " + colorfallWorld.getDisplayName(), GREEN));
            return true;
        } else {
            return false;
        }
    }

    private static List<Component> toPages(List<Component> lines) {
        final int lineCount = lines.size();
        final int linesPerPage = 10;
        List<Component> pages = new ArrayList<>((lineCount - 1) / linesPerPage + 1);
        for (int i = 0; i < lineCount; i += linesPerPage) {
            List<Component> page = new ArrayList<>(14);
            page.add(textOfChildren(ColorfallPlugin.TITLE, text(" Worlds")));
            page.add(empty());
            page.addAll(lines.subList(i, Math.min(lines.size(), i + linesPerPage)));
            pages.add(join(separator(newline()), page));
        }
        return pages;
    }

    private static void bookLines(Player player, List<Component> lines) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        book.editMeta(m -> {
                if (m instanceof BookMeta meta) {
                    meta.author(text("Cavetale"));
                    meta.title(text("Title"));
                    meta.pages(toPages(lines));
                }
            });
        player.closeInventory();
        player.openBook(book);
    }

    public void openMapBook(Player player) {
        List<ColorfallWorld> colorfallWorlds = new ArrayList<>();
        colorfallWorlds.addAll(plugin.colorfallWorlds.values());
        Collections.sort(colorfallWorlds, Comparator.comparing(ColorfallWorld::getDisplayName,
                                                               String.CASE_INSENSITIVE_ORDER));
        Collections.sort(colorfallWorlds, Comparator.comparing(ColorfallWorld::getScore).reversed());
        List<Component> lines = new ArrayList<>();
        for (ColorfallWorld colorfallWorld : colorfallWorlds) {
            List<Component> tooltip = new ArrayList<>();
            String raw = colorfallWorld.displayName;
            if (raw.length() > 16) raw = raw.substring(0, 16);
            tooltip.add(text(raw, BLUE));
            if (colorfallWorld.score > 0) {
                final int stars = (int) Math.round((double) colorfallWorld.score / 100.0);
                tooltip.add(starComponent(stars));
            }
            tooltip.addAll(Text.wrapLore(colorfallWorld.getDescription(), c -> c.color(GRAY)));
            lines.add(text(colorfallWorld.getDisplayName(), BLUE)
                      .hoverEvent(showText(join(separator(newline()), tooltip)))
                      .clickEvent(runCommand("/colorfall vote " + colorfallWorld.getPath())));
        }
        bookLines(player, lines);
    }
}
