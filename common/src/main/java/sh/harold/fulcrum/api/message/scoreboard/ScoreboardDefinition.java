package sh.harold.fulcrum.api.message.scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable definition of a scoreboard layout.
 */
public class ScoreboardDefinition {

    private final String scoreboardId;
    private final String title;
    private final List<ScoreboardModule> modules;
    private final String headerLabel;
    private final long createdTime;

    public ScoreboardDefinition(String scoreboardId, String title,
                                List<ScoreboardModule> modules, String headerLabel) {
        this.scoreboardId = Objects.requireNonNull(scoreboardId, "Scoreboard ID cannot be null");
        if (scoreboardId.isBlank()) {
            throw new IllegalArgumentException("Scoreboard ID cannot be blank");
        }
        this.modules = new ArrayList<>(Objects.requireNonNull(modules, "Modules cannot be null"));
        this.title = title;
        this.headerLabel = headerLabel;
        this.createdTime = System.currentTimeMillis();
    }

    public String getScoreboardId() {
        return scoreboardId;
    }

    public String getTitle() {
        return title;
    }

    public String getHeaderLabel() {
        return headerLabel;
    }

    public List<ScoreboardModule> getModules() {
        return new ArrayList<>(modules);
    }

    public boolean hasTitle() {
        return title != null && !title.isBlank();
    }

    public long getCreatedTime() {
        return createdTime;
    }
}
