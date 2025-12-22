package sh.harold.fulcrum.plugin.playerdata;

public record LevelingCurve(
    long baseXp,
    double linearGrowth,
    double exponentialGrowth,
    double exponent,
    double milestoneDiscount,
    int milestoneInterval
) {

    public LevelingCurve {
        if (baseXp < 1L) {
            throw new IllegalArgumentException("baseXp must be >= 1");
        }
        if (linearGrowth < 0.0 || exponentialGrowth < 0.0) {
            throw new IllegalArgumentException("growth values must be >= 0");
        }
        if (exponent <= 0.0) {
            throw new IllegalArgumentException("exponent must be > 0");
        }
        if (milestoneInterval < 0) {
            throw new IllegalArgumentException("milestoneInterval must be >= 0");
        }
        if (milestoneDiscount <= 0.0) {
            throw new IllegalArgumentException("milestoneDiscount must be > 0");
        }
    }

    public static LevelingCurve addictive() {
        // Quick early levels with gentle milestone relief to keep momentum.
        return new LevelingCurve(120L, 18.0, 12.0, 1.6, 0.9, 5);
    }

    public long xpForNextLevel(int level) {
        int safeLevel = Math.max(1, level);
        int step = safeLevel - 1;
        double raw = baseXp + (linearGrowth * step) + (exponentialGrowth * Math.pow(step, exponent));
        if (milestoneInterval > 0 && safeLevel % milestoneInterval == 0) {
            raw *= milestoneDiscount;
        }
        return Math.max(1L, Math.round(raw));
    }

    public LevelProgress progressFor(long xp) {
        long clamped = Math.max(0L, xp);
        int level = 1;
        long levelStartXp = 0L;
        long xpForNext = xpForNextLevel(level);
        long remaining = clamped;
        while (remaining >= xpForNext && level < Integer.MAX_VALUE) {
            remaining -= xpForNext;
            levelStartXp += xpForNext;
            level++;
            xpForNext = xpForNextLevel(level);
        }
        return new LevelProgress(level, clamped, levelStartXp, remaining, xpForNext);
    }
}
