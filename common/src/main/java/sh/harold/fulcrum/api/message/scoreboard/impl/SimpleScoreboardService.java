package sh.harold.fulcrum.api.message.scoreboard.impl;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardDefinition;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardModule;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardService;
import sh.harold.fulcrum.api.message.scoreboard.player.ModuleOverride;
import sh.harold.fulcrum.api.message.scoreboard.player.PlayerScoreboardManager;
import sh.harold.fulcrum.api.message.scoreboard.player.PlayerScoreboardState;
import sh.harold.fulcrum.api.message.scoreboard.registry.DefaultScoreboardRegistry;
import sh.harold.fulcrum.api.message.scoreboard.registry.ScoreboardRegistry;
import sh.harold.fulcrum.api.message.scoreboard.impl.DefaultPlayerScoreboardManager;

import java.time.Duration;
import java.util.*;

/**
 * Lightweight scoreboard service that renders via the Bukkit scoreboard API only.
 */
public class SimpleScoreboardService implements ScoreboardService {

    private static final int MAX_LINES = 15;
    private static final int MAX_ENTRY_LENGTH = 40;
    private static final String BLANK_LINE = ChatColor.RESET.toString();

    private final Plugin plugin;
    private final ScoreboardRegistry registry;
    private final PlayerScoreboardManager playerManager;

    public SimpleScoreboardService(Plugin plugin, ScoreboardRegistry registry, PlayerScoreboardManager playerManager) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.registry = Objects.requireNonNull(registry, "Scoreboard registry cannot be null");
        this.playerManager = Objects.requireNonNull(playerManager, "Player scoreboard manager cannot be null");
    }

    public SimpleScoreboardService(Plugin plugin) {
        this(plugin, new DefaultScoreboardRegistry(), new DefaultPlayerScoreboardManager());
    }

    @Override
    public void registerScoreboard(String scoreboardId, ScoreboardDefinition definition) {
        registry.register(scoreboardId, definition);
    }

    @Override
    public void unregisterScoreboard(String scoreboardId) {
        registry.unregister(scoreboardId);
    }

    @Override
    public boolean isScoreboardRegistered(String scoreboardId) {
        return registry.exists(scoreboardId);
    }

    @Override
    public void showScoreboard(UUID playerId, String scoreboardId) {
        Player player = requireOnline(playerId);
        ScoreboardDefinition definition = registry.get(scoreboardId)
                .orElseThrow(() -> new IllegalArgumentException("Scoreboard not found: " + scoreboardId));

        PlayerScoreboardState state = playerManager.stateFor(playerId);
        state.setCurrentScoreboardId(scoreboardId);
        render(player, state, definition);
    }

    @Override
    public void hideScoreboard(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        playerManager.clear(playerId);
        if (player != null && player.isOnline()) {
            Optional.ofNullable(Bukkit.getScoreboardManager())
                    .map(org.bukkit.scoreboard.ScoreboardManager::getMainScoreboard)
                    .ifPresent(player::setScoreboard);
        }
    }

    @Override
    public void refreshPlayerScoreboard(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        playerManager.getState(playerId).ifPresent(state -> {
            String scoreboardId = state.getCurrentScoreboardId();
            if (scoreboardId == null) {
                return;
            }
            registry.get(scoreboardId).ifPresent(definition -> render(player, state, definition));
        });
    }

    @Override
    public String getCurrentScoreboardId(UUID playerId) {
        return playerManager.getState(playerId)
                .map(PlayerScoreboardState::getCurrentScoreboardId)
                .orElse(null);
    }

    @Override
    public boolean hasScoreboardDisplayed(UUID playerId) {
        return playerManager.getState(playerId).map(PlayerScoreboardState::hasScoreboard).orElse(false);
    }

    @Override
    public void flashModule(UUID playerId, int moduleIndex, ScoreboardModule module, Duration duration) {
        Objects.requireNonNull(module, "Module cannot be null");
        Objects.requireNonNull(duration, "Duration cannot be null");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Flash duration cannot be negative");
        }
        PlayerScoreboardState state = playerManager.stateFor(playerId);
        state.startFlash(moduleIndex, module, duration);

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline() && state.getCurrentScoreboardId() != null) {
            registry.get(state.getCurrentScoreboardId()).ifPresent(def -> render(player, state, def));
        }

        long delayTicks = Math.max(1L, duration.toMillis() / 50L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            state.stopFlash(moduleIndex);
            Player livePlayer = Bukkit.getPlayer(playerId);
            if (livePlayer != null && livePlayer.isOnline() && state.getCurrentScoreboardId() != null) {
                registry.get(state.getCurrentScoreboardId()).ifPresent(def -> render(livePlayer, state, def));
            }
        }, delayTicks);
    }

    @Override
    public void setPlayerTitle(UUID playerId, String title) {
        playerManager.stateFor(playerId).setCustomTitle(title);
        refreshPlayerScoreboard(playerId);
    }

    @Override
    public String getPlayerTitle(UUID playerId) {
        return playerManager.getState(playerId)
                .map(PlayerScoreboardState::getCustomTitle)
                .orElse(null);
    }

    @Override
    public void clearPlayerTitle(UUID playerId) {
        playerManager.getState(playerId).ifPresent(state -> {
            state.setCustomTitle(null);
            refreshPlayerScoreboard(playerId);
        });
    }

    @Override
    public void setModuleOverride(UUID playerId, String moduleId, boolean enabled) {
        Objects.requireNonNull(moduleId, "Module ID cannot be null");
        playerManager.stateFor(playerId).setOverride(new ModuleOverride(moduleId, enabled));
        refreshPlayerScoreboard(playerId);
    }

    @Override
    public boolean isModuleOverrideEnabled(UUID playerId, String moduleId) {
        Objects.requireNonNull(moduleId, "Module ID cannot be null");
        return playerManager.getState(playerId)
                .flatMap(state -> state.getOverride(moduleId))
                .map(ModuleOverride::enabled)
                .orElse(true);
    }

    @Override
    public void clearPlayerData(UUID playerId) {
        hideScoreboard(playerId);
    }

    private void render(Player player, PlayerScoreboardState state, ScoreboardDefinition definition) {
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }

        Scoreboard scoreboard = manager.getNewScoreboard();
        String objectiveName = objectiveName(definition.getScoreboardId(), player.getUniqueId());
        String title = translate(state.hasCustomTitle() ? state.getCustomTitle() : definition.getTitle());

        Objective objective = scoreboard.registerNewObjective(objectiveName, "dummy", title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = collectLines(player, state, definition);
        int score = lines.size();
        Set<String> usedEntries = new HashSet<>();
        for (String rawLine : lines) {
            String entry = prepareLine(rawLine, usedEntries);
            objective.getScore(entry).setScore(score--);
        }

        player.setScoreboard(scoreboard);
    }

    private List<String> collectLines(Player player, PlayerScoreboardState state, ScoreboardDefinition definition) {
        List<String> lines = new ArrayList<>();

        if (definition.getHeaderLabel() != null && !definition.getHeaderLabel().isBlank()) {
            lines.add(translate(definition.getHeaderLabel()));
            lines.add(BLANK_LINE);
        }

        List<ScoreboardModule> modules = definition.getModules();
        for (int i = 0; i < modules.size(); i++) {
            ScoreboardModule baseModule = modules.get(i);
            ScoreboardModule activeModule = state.activeFlash(i).orElse(baseModule);

            boolean enabled = state.getOverride(activeModule.getModuleId())
                    .map(ModuleOverride::enabled)
                    .orElseGet(() -> activeModule.isEnabled(player));

            if (!enabled) {
                continue;
            }

            List<String> moduleLines = activeModule.renderLines(player);
            if (moduleLines == null || moduleLines.isEmpty()) {
                continue;
            }
            moduleLines.stream()
                    .filter(Objects::nonNull)
                    .map(this::translate)
                    .forEach(lines::add);
        }

        if (definition.getFooterLabel() != null && !definition.getFooterLabel().isBlank()) {
            lines.add(translate(definition.getFooterLabel()));
        }

        if (lines.size() > MAX_LINES) {
            return lines.subList(0, MAX_LINES);
        }
        return lines;
    }

    private String prepareLine(String rawLine, Set<String> usedEntries) {
        String line = rawLine == null ? "" : rawLine;
        if (line.length() > MAX_ENTRY_LENGTH) {
            line = line.substring(0, MAX_ENTRY_LENGTH);
        }
        if (line.isEmpty()) {
            line = ChatColor.RESET.toString();
        }
        String candidate = line;
        int salt = 0;
        while (usedEntries.contains(candidate)) {
            ChatColor color = ChatColor.values()[salt % ChatColor.values().length];
            candidate = line + color;
            salt++;
        }
        usedEntries.add(candidate);
        return candidate;
    }

    private String objectiveName(String scoreboardId, UUID playerId) {
        String base = "flc_" + Integer.toHexString(scoreboardId.hashCode()) + "_" + Integer.toHexString(playerId.hashCode());
        return base.length() > 16 ? base.substring(0, 16) : base;
    }

    private String translate(String text) {
        if (text == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private Player requireOnline(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            throw new IllegalStateException("Player is not online: " + playerId);
        }
        return player;
    }
}
