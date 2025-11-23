package sh.harold.fulcrum.common.permissions;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface FormattedUsernameService {

    CompletionStage<FormattedUsername> username(UUID playerId, String username);

    default CompletionStage<FormattedUsername> username(Player player) {
        Objects.requireNonNull(player, "player");
        return username(player.getUniqueId(), player.getName());
    }

    record FormattedUsername(Component prefix, Component name) {

        public FormattedUsername {
            prefix = prefix == null ? Component.empty() : prefix;
            name = Objects.requireNonNull(name, "name");
        }

        public Component displayName() {
            if (prefix.equals(Component.empty())) {
                return name;
            }
            return prefix.append(Component.space()).append(name);
        }
    }
}
