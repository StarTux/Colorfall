package io.github.feydk.colorfall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;

@Getter
public final class SaveState {
    protected List<String> worlds = new ArrayList<>();
    protected boolean event = false;
    protected boolean eventAuto = false;
    protected Map<UUID, Integer> scores = new HashMap<>();
    protected Map<UUID, String> votes = new HashMap<>();

    public void addScore(UUID uuid, int value) {
        int old = scores.getOrDefault(uuid, 0);
        scores.put(uuid, Math.max(0, old + value));
    }

    public int getScore(UUID uuid) {
        return scores.getOrDefault(uuid, 0);
    }
}
