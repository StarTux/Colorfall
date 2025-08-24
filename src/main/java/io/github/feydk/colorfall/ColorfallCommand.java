package io.github.feydk.colorfall;

import com.cavetale.core.command.AbstractCommand;

public final class ColorfallCommand extends AbstractCommand<ColorfallPlugin> {
    protected ColorfallCommand(final ColorfallPlugin plugin) {
        super(plugin, "colorfall");
    }

    @Override
    protected void onEnable() {
        rootNode.description("Colorfall command");
    }
}
