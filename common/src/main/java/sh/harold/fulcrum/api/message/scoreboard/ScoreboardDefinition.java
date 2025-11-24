package sh.harold.fulcrum.api.message.scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Immutable definition of a scoreboard layout.
 */
public class ScoreboardDefinition {

    private final String scoreboardId;
    private final String title;
    private final List<ScoreboardModule> modules;
    private final Supplier<String> headerLabel;
    private final Supplier<String> footerLabel;
    private final long createdTime;

    public ScoreboardDefinition(String scoreboardId, String title,
                                List<ScoreboardModule> modules, Supplier<String> headerLabel, Supplier<String> footerLabel) {
        this.scoreboardId = validateScoreboardId(scoreboardId);
        this.modules = new ArrayList<>(Objects.requireNonNull(modules, "Modules cannot be null"));
        this.title = title;
        this.headerLabel = headerLabel;
        this.footerLabel = footerLabel;
        this.createdTime = System.currentTimeMillis();
    }

    public String getScoreboardId() {
        return scoreboardId;
    }

    public String getTitle() {
        return title;
    }

    public String getHeaderLabel() {
        return headerLabel == null ? null : headerLabel.get();
    }

    public String getFooterLabel() {
        return footerLabel == null ? null : footerLabel.get();
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

    private String validateScoreboardId(String scoreboardId) {
        String validatedId = Objects.requireNonNull(scoreboardId, "Scoreboard ID cannot be null");
        if (validatedId.isBlank()) {
            throw new IllegalArgumentException("Scoreboard ID cannot be blank");
        }
        return validatedId;
    }
}
