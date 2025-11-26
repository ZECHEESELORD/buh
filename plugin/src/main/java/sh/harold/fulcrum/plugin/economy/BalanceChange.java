package sh.harold.fulcrum.plugin.economy;

import java.util.Objects;

public record BalanceChange(BalanceSnapshot before, BalanceSnapshot after) {

    public BalanceChange {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
    }
}
