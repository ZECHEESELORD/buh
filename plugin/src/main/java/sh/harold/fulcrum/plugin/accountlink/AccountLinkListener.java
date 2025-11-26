package sh.harold.fulcrum.plugin.accountlink;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AccountLinkListener implements Listener {

    private static final Duration PRELOGIN_TIMEOUT = Duration.ofSeconds(3);

    private final AccountLinkService linkService;
    private final Logger logger;

    public AccountLinkListener(AccountLinkService linkService, Logger logger) {
        this.linkService = Objects.requireNonNull(linkService, "linkService");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID playerId = event.getUniqueId();
        try {
            AccountLinkService.PreLoginDecision decision = linkService.preLoginDecision(playerId)
                .toCompletableFuture()
                .orTimeout(PRELOGIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .join();

            if (decision.status() == AccountLinkService.PreLoginStatus.DENY) {
                String message = decision.message() != null
                    ? decision.message()
                    : "Link check failed; try again soon.";
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, message);
            }
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "Pre-login link check failed for " + playerId, exception);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "Link check failed; try again soon.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        linkService.consumeTicket(event.getPlayer().getUniqueId(), event.getPlayer().getName())
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to consume link ticket after join for " + event.getPlayer().getUniqueId(), throwable);
                return null;
            });
    }
}
