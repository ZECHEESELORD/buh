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
        // Quick early levels with steady growth and no milestone discounts.
        return new LevelingCurve(120L, 18.0, 12.0, 1.6, 1.0, 0);
    }

    public long xpForNextLevel(int level) {
        int safeLevel = Math.max(1, level);
        int step = safeLevel - 1;
        double raw = baseXp + (linearGrowth * step) + (exponentialGrowth * Math.pow(step, exponent));
        if (milestoneInterval > 0 && safeLevel % milestoneInterval == 0) {
            raw *= milestoneDiscount;
        }
        return Math.max(1L, (long) Math.ceil(raw));
    }

    public LevelProgress progressFor(long xp) {
        return progressFor(xp, Integer.MAX_VALUE);
    }

    public LevelProgress progressFor(long xp, int maxLevel) {
        long clamped = Math.max(0L, xp);
        int cap = Math.max(0, maxLevel);
        int level = 0;
        long levelStartXp = 0L;
        long remaining = clamped;
        while (level < cap) {
            long xpForNext = xpForNextLevel(level);
            if (remaining < xpForNext) {
                break;
            }
            remaining -= xpForNext;
            levelStartXp += xpForNext;
            level++;
        }
        long xpForNext = level >= cap ? 0L : xpForNextLevel(level);
        return new LevelProgress(level, clamped, levelStartXp, remaining, xpForNext);
    }
}
