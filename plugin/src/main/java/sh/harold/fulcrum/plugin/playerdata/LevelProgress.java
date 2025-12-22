package sh.harold.fulcrum.plugin.playerdata;

public record LevelProgress(
    int level,
    long totalXp,
    long levelStartXp,
    long xpIntoLevel,
    long xpForNextLevel
) {

    public long xpToNextLevel() {
        return Math.max(0L, xpForNextLevel - xpIntoLevel);
    }

    public long totalXpForNextLevel() {
        return levelStartXp + xpForNextLevel;
    }

    public double progressRatio() {
        if (xpForNextLevel <= 0L) {
            return 1.0;
        }
        double ratio = (double) xpIntoLevel / (double) xpForNextLevel;
        return Math.max(0.0, Math.min(1.0, ratio));
    }
}
