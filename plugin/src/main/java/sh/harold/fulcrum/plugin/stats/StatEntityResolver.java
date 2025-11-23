package sh.harold.fulcrum.plugin.stats;

import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import sh.harold.fulcrum.stats.service.EntityKey;

import java.util.Optional;
import java.util.UUID;

public final class StatEntityResolver {

    private final Server server;

    StatEntityResolver(Server server) {
        this.server = server;
    }

    public Optional<LivingEntity> findLiving(EntityKey key) {
        UUID uuid = parseUuid(key);
        if (uuid == null) {
            return Optional.empty();
        }
        Entity entity = server.getEntity(uuid);
        if (entity instanceof LivingEntity living) {
            return Optional.of(living);
        }
        return Optional.empty();
    }

    private UUID parseUuid(EntityKey key) {
        try {
            return UUID.fromString(key.value());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
