package sh.harold.fulcrum.plugin.vote;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record FeatureVoteState(Map<FeatureVoteOption, Integer> tallies,
                               int totalVotes,
                               FeatureVoteOption selectedOption) {

    private static final Map<FeatureVoteOption, Integer> EMPTY_COUNTS = defaultCounts();

    public FeatureVoteState {
        Map<FeatureVoteOption, Integer> merged = new EnumMap<>(EMPTY_COUNTS);
        if (tallies != null) {
            tallies.forEach((option, count) -> merged.merge(option, Math.max(0, count), Integer::sum));
        }
        tallies = Collections.unmodifiableMap(merged);
        totalVotes = merged.values().stream().mapToInt(Integer::intValue).sum();
    }

    public double percentage(FeatureVoteOption option) {
        if (totalVotes <= 0) {
            return 0.0D;
        }
        int votes = tallies.getOrDefault(option, 0);
        return (votes * 100.0D) / totalVotes;
    }

    public int filledSegments(FeatureVoteOption option, int maxSegments) {
        if (maxSegments <= 0 || totalVotes <= 0) {
            return 0;
        }
        double percent = percentage(option);
        int segments = (int) Math.ceil(percent / 100.0D * maxSegments);
        return Math.max(0, Math.min(maxSegments, segments));
    }

    private static Map<FeatureVoteOption, Integer> defaultCounts() {
        Map<FeatureVoteOption, Integer> defaults = new EnumMap<>(FeatureVoteOption.class);
        for (FeatureVoteOption option : FeatureVoteOption.values()) {
            defaults.put(option, 0);
        }
        return Collections.unmodifiableMap(defaults);
    }
}
