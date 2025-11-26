package sh.harold.fulcrum.plugin.economy;

import java.util.Objects;

public sealed interface TransferResult permits TransferResult.Success, TransferResult.InsufficientFunds, TransferResult.Rejected {

    record Success(BalanceChange source, BalanceChange target) implements TransferResult {

        public Success {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
        }
    }

    record InsufficientFunds(BalanceSnapshot sourceBalance) implements TransferResult {

        public InsufficientFunds {
            Objects.requireNonNull(sourceBalance, "sourceBalance");
        }
    }

    record Rejected(String reason) implements TransferResult {

        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
