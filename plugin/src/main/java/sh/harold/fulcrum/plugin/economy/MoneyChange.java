package sh.harold.fulcrum.plugin.economy;

import java.util.Objects;

public sealed interface MoneyChange permits MoneyChange.Success, MoneyChange.InsufficientFunds {

    record Success(BalanceChange change) implements MoneyChange {

        public Success {
            Objects.requireNonNull(change, "change");
        }
    }

    record InsufficientFunds(BalanceSnapshot balance) implements MoneyChange {

        public InsufficientFunds {
            Objects.requireNonNull(balance, "balance");
        }
    }
}
