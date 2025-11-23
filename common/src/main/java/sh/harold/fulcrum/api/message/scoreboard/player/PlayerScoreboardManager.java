package sh.harold.fulcrum.api.message.scoreboard.player;

import java.util.Optional;
import java.util.UUID;

public interface PlayerScoreboardManager {

    PlayerScoreboardState stateFor(UUID playerId);

    Optional<PlayerScoreboardState> getState(UUID playerId);

    void clear(UUID playerId);

    int getActivePlayerCount();
}
