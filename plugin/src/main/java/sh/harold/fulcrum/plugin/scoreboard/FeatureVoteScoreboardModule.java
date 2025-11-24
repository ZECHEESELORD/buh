package sh.harold.fulcrum.plugin.scoreboard;

import org.bukkit.entity.Player;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardModule;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;
import sh.harold.fulcrum.plugin.vote.FeatureVoteOption;
import sh.harold.fulcrum.plugin.vote.FeatureVoteState;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FeatureVoteScoreboardModule implements ScoreboardModule {

    private static final String MODULE_ID = "feature_vote";
    private static final String SELECTION_PATH = "selection";

    private final Logger logger;
    private final DocumentCollection ballots;
    private final AtomicReference<FeatureVoteState> state = new AtomicReference<>(new FeatureVoteState(Map.of(), 0, null));
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    public FeatureVoteScoreboardModule(Logger logger, DocumentCollection ballots) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.ballots = Objects.requireNonNull(ballots, "ballots");
    }

    @Override
    public String getModuleId() {
        return MODULE_ID;
    }

    @Override
    public List<String> renderLines(Player player) {
        FeatureVoteState snapshot = state.get();
        return List.of(
            "&fVote for features!",
            formatLine(FeatureVoteOption.BOUNTIES, snapshot),
            formatLine(FeatureVoteOption.CUSTOM_ITEMS, snapshot),
            formatLine(FeatureVoteOption.SETTLEMENTS, snapshot),
            formatLine(FeatureVoteOption.ECONOMY, snapshot)
        );
    }

    public void refreshTallies() {
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        ballots.all().whenComplete((documents, throwable) -> {
            try {
                if (throwable != null) {
                    logger.log(Level.FINE, "Failed to refresh feature vote scoreboard tallies", throwable);
                    return;
                }
                Map<FeatureVoteOption, Integer> counts = new EnumMap<>(FeatureVoteOption.class);
                for (FeatureVoteOption option : FeatureVoteOption.values()) {
                    counts.put(option, 0);
                }
                for (Document document : documents) {
                    document.get(SELECTION_PATH, String.class)
                        .flatMap(FeatureVoteOption::fromId)
                        .ifPresent(option -> counts.merge(option, 1, Integer::sum));
                }
                state.set(new FeatureVoteState(counts, counts.values().stream().mapToInt(Integer::intValue).sum(), null));
            } finally {
                refreshing.set(false);
            }
        });
    }

    private String formatLine(FeatureVoteOption option, FeatureVoteState snapshot) {
        String color = colorCode(option);
        String percent = formatPercent(snapshot.percentage(option));
        return "&8 ● " + color + option.displayName() + " &8(" + percent + ")";
    }

    private String colorCode(FeatureVoteOption option) {
        return switch (option) {
            case BOUNTIES -> "&c";
            case CUSTOM_ITEMS -> "&d";
            case SETTLEMENTS -> "&a";
            case ECONOMY -> "&6";
        };
    }

    private String formatPercent(double percent) {
        return String.format(Locale.US, "%.0f%%", percent);
    }
}
