package sh.harold.fulcrum.plugin.fun.quickmaths;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Coordinates Quick Maths rounds and routes chat answers to the active session.
 */
public final class QuickMathsManager {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final NamedTextColor[] PAREN_COLORS = new NamedTextColor[]{
        NamedTextColor.GRAY,
        NamedTextColor.GOLD,
        NamedTextColor.AQUA
    };
    private static final ThreadLocal<DecimalFormat> DECIMAL_FORMAT = ThreadLocal.withInitial(() -> {
        DecimalFormat format = new DecimalFormat("0.###");
        format.setGroupingUsed(false);
        return format;
    });
    private static final int MAX_WINNERS = 10;

    private final JavaPlugin plugin;
    private final SecureRandom random = new SecureRandom();
    private final AtomicReference<QuickMathsSession> activeSession = new AtomicReference<>();

    public QuickMathsManager(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public int maxWinnersPerRound() {
        return MAX_WINNERS;
    }

    public boolean startRound(CommandSender initiator, Difficulty difficulty, int winners) {
        Objects.requireNonNull(initiator, "initiator");
        Objects.requireNonNull(difficulty, "difficulty");
        if (winners < 1 || winners > MAX_WINNERS) {
            initiator.sendMessage(Component.text("Pick between 1 and " + MAX_WINNERS + " winners.", NamedTextColor.RED));
            return false;
        }

        QuickMathEquation equation = generateEquation(difficulty);
        QuickMathsSession session = new QuickMathsSession(equation, winners, System.currentTimeMillis());
        if (!activeSession.compareAndSet(null, session)) {
            initiator.sendMessage(Component.text("Quick Maths already running.", NamedTextColor.RED));
            return false;
        }

        session.scheduleTimeout(plugin, difficulty.timeLimit(), () -> timeoutSession(session));
        broadcastStart(session, winners, initiator);
        Component answerReveal = Component.text("Answer: ", NamedTextColor.GRAY)
            .append(Component.text(formatAnswer(equation.answer()), NamedTextColor.YELLOW));
        initiator.sendMessage(prefix(answerReveal));
        return true;
    }

    public boolean cancelRound(CommandSender sender) {
        QuickMathsSession session = activeSession.getAndSet(null);
        if (session == null) {
            sender.sendMessage(Component.text("No active Quick Maths round.", NamedTextColor.RED));
            return false;
        }
        session.cancelTimeout();
        Component body = Component.text()
            .append(Component.text("Quick Maths cancelled; equation was ", NamedTextColor.GRAY))
            .append(session.equation().display())
            .append(Component.text(" = ", NamedTextColor.GRAY))
            .append(Component.text(formatAnswer(session.equation().answer()), NamedTextColor.YELLOW))
            .build();
        broadcast(body, sender);
        return true;
    }

    public void handleChat(Player player, String plainMessage) {
        if (player == null || plainMessage == null) {
            return;
        }
        QuickMathsSession session = activeSession.get();
        if (session == null) {
            return;
        }
        if (session.isExpired()) {
            timeoutSession(session);
            return;
        }

        String trimmed = plainMessage.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        double guess;
        try {
            guess = Double.parseDouble(trimmed.replace(",", ""));
        } catch (NumberFormatException ignored) {
            return;
        }

        if (!session.matchesAnswer(guess)) {
            return;
        }

        WinnerPlacement placement = session.recordWinner(player.getUniqueId());
        if (placement == null) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> announceWinner(session, player, placement));
    }

    public void shutdown() {
        QuickMathsSession session = activeSession.getAndSet(null);
        if (session != null) {
            session.cancelTimeout();
        }
    }

    private void announceWinner(QuickMathsSession session, Player player, WinnerPlacement placement) {
        Component body = Component.text()
            .append(Component.text("#" + placement.position() + " ", NamedTextColor.LIGHT_PURPLE))
            .append(Component.text(player.getName(), NamedTextColor.AQUA))
            .append(Component.space())
            .append(Component.text("answered in ", NamedTextColor.GRAY))
            .append(Component.text(placement.elapsedMillis() + "ms", NamedTextColor.YELLOW))
            .build();
        broadcast(body, null);

        if (session.isComplete() && activeSession.compareAndSet(session, null)) {
            session.cancelTimeout();
            Component finished = Component.text()
                .append(session.equation().display())
                .append(Component.text(" = ", NamedTextColor.GRAY))
                .append(Component.text(formatAnswer(session.equation().answer()), NamedTextColor.YELLOW))
                .build();
            broadcast(finished, null);
        }
    }

    private void timeoutSession(QuickMathsSession session) {
        if (!activeSession.compareAndSet(session, null)) {
            return;
        }
        session.cancelTimeout();
        Component body = Component.text()
            .append(Component.text("Time expired; ", NamedTextColor.GRAY))
            .append(session.equation().display())
            .append(Component.text(" = ", NamedTextColor.GRAY))
            .append(Component.text(formatAnswer(session.equation().answer()), NamedTextColor.YELLOW))
            .build();
        broadcast(body, null);
    }

    private void broadcastStart(QuickMathsSession session, int winners, CommandSender initiator) {
        Component body = Component.text()
            .append(Component.text("First ", NamedTextColor.GRAY))
            .append(Component.text(winners == 1 ? "player" : winners + " players", NamedTextColor.YELLOW))
            .append(Component.text(" to solve ", NamedTextColor.GRAY))
            .append(session.equation().display())
            .append(Component.text(" wins.", NamedTextColor.GRAY))
            .build();
        broadcast(body, initiator);
    }

    private void broadcast(Component body, CommandSender fallback) {
        Component message = prefix(body);
        var recipients = plugin.getServer().getOnlinePlayers();
        if (recipients.isEmpty() && fallback != null) {
            fallback.sendMessage(message);
            return;
        }
        recipients.forEach(player -> player.sendMessage(message));
        if (fallback != null && !(fallback instanceof Player)) {
            fallback.sendMessage(PLAIN.serialize(message));
        }
    }

    private Component prefix(Component body) {
        Component header = Component.text("Quick Maths", NamedTextColor.LIGHT_PURPLE)
            .decorate(TextDecoration.BOLD);
        Component normalized = body.decoration(TextDecoration.BOLD, false);
        return Component.text().append(header).append(Component.text(": ", NamedTextColor.DARK_GRAY)).append(normalized).build();
    }

    private QuickMathEquation generateEquation(Difficulty difficulty) {
        int termCount = difficulty.termCount();
        List<Expression> expressions = new ArrayList<>();
        ThreadLocalRandom threadRandom = ThreadLocalRandom.current();
        for (int i = 0; i < termCount; i++) {
            expressions.add(new ValueExpression(threadRandom.nextInt(difficulty.minOperand(), difficulty.maxOperand() + 1)));
        }
        while (expressions.size() > 1) {
            Expression left = expressions.remove(threadRandom.nextInt(expressions.size()));
            Expression right = expressions.remove(threadRandom.nextInt(expressions.size()));
            Operation op = difficulty.randomOperation(random);
            boolean wrap = threadRandom.nextDouble() < difficulty.wrapChance();
            expressions.add(new BinaryExpression(left, right, op, wrap));
        }
        Expression root = expressions.getFirst();
        return new QuickMathEquation(root.renderComponent(0, true), root.evaluate(), difficulty.tolerance());
    }

    private static String formatAnswer(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-9) {
            return Long.toString(Math.round(value));
        }
        return DECIMAL_FORMAT.get().format(value);
    }

    public enum Difficulty {
        EASY(2, 6, 18, EnumSet.of(Operation.ADD, Operation.SUBTRACT), 0.0D, 0.0D, Duration.ofSeconds(30)),
        NORMAL(3, 4, 16, EnumSet.of(Operation.ADD, Operation.SUBTRACT, Operation.MULTIPLY), 0.25D, 0.05D, Duration.ofSeconds(30)),
        HARD(4, 3, 14, EnumSet.of(Operation.ADD, Operation.SUBTRACT, Operation.MULTIPLY), 0.4D, 0.1D, Duration.ofSeconds(45)),
        EXTREME(5, 2, 12, EnumSet.of(Operation.ADD, Operation.SUBTRACT, Operation.MULTIPLY, Operation.DIVIDE), 0.5D, 0.15D, Duration.ofSeconds(60));

        private final int termCount;
        private final int minOperand;
        private final int maxOperand;
        private final EnumSet<Operation> operations;
        private final double wrapChance;
        private final double tolerance;
        private final Duration timeLimit;

        Difficulty(int termCount,
                   int minOperand,
                   int maxOperand,
                   EnumSet<Operation> operations,
                   double wrapChance,
                   double tolerance,
                   Duration timeLimit) {
            this.termCount = termCount;
            this.minOperand = minOperand;
            this.maxOperand = maxOperand;
            this.operations = operations;
            this.wrapChance = wrapChance;
            this.tolerance = tolerance;
            this.timeLimit = timeLimit;
        }

        public static java.util.Optional<Difficulty> parse(String input) {
            if (input == null || input.isBlank()) {
                return java.util.Optional.empty();
            }
            try {
                return java.util.Optional.of(Difficulty.valueOf(input.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                return java.util.Optional.empty();
            }
        }

        int termCount() {
            return termCount;
        }

        int minOperand() {
            return minOperand;
        }

        int maxOperand() {
            return maxOperand;
        }

        EnumSet<Operation> operations() {
            return operations;
        }

        double wrapChance() {
            return wrapChance;
        }

        double tolerance() {
            return tolerance;
        }

        Duration timeLimit() {
            return timeLimit;
        }

        Operation randomOperation(SecureRandom random) {
            Operation[] values = operations.toArray(Operation[]::new);
            return values[random.nextInt(values.length)];
        }
    }

    private enum Operation {
        ADD("+") {
            @Override
            double eval(double a, double b) {
                return a + b;
            }
        },
        SUBTRACT("-") {
            @Override
            double eval(double a, double b) {
                return a - b;
            }
        },
        MULTIPLY("*") {
            @Override
            double eval(double a, double b) {
                return a * b;
            }
        },
        DIVIDE("/") {
            @Override
            double eval(double a, double b) {
                double safe = Math.abs(b) < 0.001D ? 1.0D : b;
                return a / safe;
            }
        };

        private final String symbol;

        Operation(String symbol) {
            this.symbol = symbol;
        }

        abstract double eval(double a, double b);
    }

    private sealed interface Expression permits BinaryExpression, ValueExpression {
        double evaluate();

        Component renderComponent(int depth, boolean root);
    }

    private record ValueExpression(double value) implements Expression {
        @Override
        public double evaluate() {
            return value;
        }

        @Override
        public Component renderComponent(int depth, boolean root) {
            return Component.text(formatAnswer(value), NamedTextColor.WHITE);
        }
    }

    private record BinaryExpression(Expression left, Expression right, Operation operation, boolean forceWrap) implements Expression {

        @Override
        public double evaluate() {
            return operation.eval(left.evaluate(), right.evaluate());
        }

        @Override
        public Component renderComponent(int depth, boolean root) {
            TextComponent.Builder builder = Component.text();
            boolean wrap = !root || forceWrap;
            NamedTextColor parenColor = PAREN_COLORS[depth % PAREN_COLORS.length];
            if (wrap) {
                builder.append(Component.text("(", parenColor));
            }
            builder.append(left.renderComponent(depth + 1, false));
            builder.append(Component.text(" " + operation.symbol + " ", NamedTextColor.DARK_GRAY));
            builder.append(right.renderComponent(depth + 1, false));
            if (wrap) {
                builder.append(Component.text(")", parenColor));
            }
            return builder.build();
        }
    }

    private record QuickMathEquation(Component display, double answer, double tolerance) {
    }

    private record WinnerPlacement(int position, long elapsedMillis) {
    }

    private static final class QuickMathsSession {
        private final QuickMathEquation equation;
        private final int maxWinners;
        private final long startedAt;
        private final Set<UUID> winners = java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());
        private BukkitTask timeoutTask;
        private long expiresAt;

        private QuickMathsSession(QuickMathEquation equation, int maxWinners, long startedAt) {
            this.equation = equation;
            this.maxWinners = maxWinners;
            this.startedAt = startedAt;
        }

        private QuickMathEquation equation() {
            return equation;
        }

        private boolean matchesAnswer(double guess) {
            return Math.abs(guess - equation.answer()) <= equation.tolerance();
        }

        private WinnerPlacement recordWinner(UUID playerId) {
            synchronized (winners) {
                if (winners.contains(playerId) || winners.size() >= maxWinners) {
                    return null;
                }
                winners.add(playerId);
                int position = winners.size();
                long elapsed = System.currentTimeMillis() - startedAt;
                return new WinnerPlacement(position, elapsed);
            }
        }

        private boolean isComplete() {
            synchronized (winners) {
                return winners.size() >= maxWinners;
            }
        }

        private void scheduleTimeout(JavaPlugin plugin, Duration limit, Runnable timeout) {
            if (limit == null || limit.isZero() || limit.isNegative()) {
                return;
            }
            long ticks = Math.max(1L, (limit.toMillis() + 49L) / 50L);
            expiresAt = System.currentTimeMillis() + limit.toMillis();
            timeoutTask = plugin.getServer().getScheduler().runTaskLater(plugin, timeout, ticks);
        }

        private boolean isExpired() {
            return expiresAt > 0 && System.currentTimeMillis() >= expiresAt;
        }

        private void cancelTimeout() {
            if (timeoutTask != null) {
                timeoutTask.cancel();
                timeoutTask = null;
            }
            expiresAt = 0L;
        }
    }
}
