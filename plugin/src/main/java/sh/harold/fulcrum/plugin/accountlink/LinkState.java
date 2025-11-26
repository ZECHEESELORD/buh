package sh.harold.fulcrum.plugin.accountlink;

import java.time.Instant;
import java.util.UUID;

public record LinkState(long discordId, UUID uuid, String username, Instant issuedAt) {
}
