package sh.harold.fulcrum.api.message.scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Fluent builder for assembling a ScoreboardDefinition.
 */
public class ScoreboardBuilder {

    private final String scoreboardId;
    private final List<ScoreboardModule> modules = new ArrayList<>();
    private String title;
    private Supplier<String> headerLabel;
    private Supplier<String> footerLabel;

    public ScoreboardBuilder(String scoreboardId) {
        this.scoreboardId = Objects.requireNonNull(scoreboardId, "Scoreboard ID cannot be null");
        if (scoreboardId.isBlank()) {
            throw new IllegalArgumentException("Scoreboard ID cannot be blank");
        }
    }

    public ScoreboardBuilder title(String title) {
        this.title = title;
        return this;
    }

    public ScoreboardBuilder headerLabel(String headerLabel) {
        this.headerLabel = headerLabel == null ? null : () -> headerLabel;
        return this;
    }

    public ScoreboardBuilder headerSupplier(Supplier<String> headerLabel) {
        this.headerLabel = headerLabel;
        return this;
    }

    public ScoreboardBuilder footerLabel(String footerLabel) {
        this.footerLabel = footerLabel == null ? null : () -> footerLabel;
        return this;
    }

    public ScoreboardBuilder footerSupplier(Supplier<String> footerLabel) {
        this.footerLabel = footerLabel;
        return this;
    }

    public ScoreboardBuilder module(ScoreboardModule module) {
        modules.add(Objects.requireNonNull(module, "Module cannot be null"));
        return this;
    }

    public ScoreboardBuilder module(int index, ScoreboardModule module) {
        modules.add(index, Objects.requireNonNull(module, "Module cannot be null"));
        return this;
    }

    public ScoreboardBuilder clearModules() {
        modules.clear();
        return this;
    }

    public ScoreboardDefinition build() {
        return new ScoreboardDefinition(scoreboardId, title, modules, headerLabel, footerLabel);
    }
}
