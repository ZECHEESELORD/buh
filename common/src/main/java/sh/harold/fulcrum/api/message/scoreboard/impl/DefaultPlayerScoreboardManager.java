package sh.harold.fulcrum.api.message.scoreboard.impl;

import sh.harold.fulcrum.api.message.scoreboard.player.PlayerScoreboardManager;
import sh.harold.fulcrum.api.message.scoreboard.player.PlayerScoreboardState;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultPlayerScoreboardManager implements PlayerScoreboardManager {

    private final ConcurrentHashMap<UUID, PlayerScoreboardState> states = new ConcurrentHashMap<>();

    @Override
    public PlayerScoreboardState stateFor(UUID playerId) {
        if (playerId == null) {
            throw new IllegalArgumentException("Player ID cannot be null");
        }
        return states.computeIfAbsent(playerId, PlayerScoreboardState::new);
    }

    @Override
    public Optional<PlayerScoreboardState> getState(UUID playerId) {
        if (playerId == null) {
            throw new IllegalArgumentException("Player ID cannot be null");
        }
        return Optional.ofNullable(states.get(playerId));
    }

    @Override
    public void clear(UUID playerId) {
        if (playerId == null) {
            throw new IllegalArgumentException("Player ID cannot be null");
        }
        states.remove(playerId);
    }

    @Override
    public int getActivePlayerCount() {
        return states.size();
    }
}
