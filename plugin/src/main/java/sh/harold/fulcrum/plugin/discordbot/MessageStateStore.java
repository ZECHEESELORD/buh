package sh.harold.fulcrum.plugin.discordbot;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class MessageStateStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path statePath;
    private final Logger logger;
    private final Map<String, Long> state = new ConcurrentHashMap<>();

    public MessageStateStore(Path statePath, Logger logger) {
        this.statePath = Objects.requireNonNull(statePath, "statePath");
        this.logger = Objects.requireNonNull(logger, "logger");
        load();
    }

    public Optional<Long> messageId(String key) {
        return Optional.ofNullable(state.get(key));
    }

    public void put(String key, long messageId) {
        state.put(key, messageId);
        persist();
    }

    private void load() {
        if (!Files.exists(statePath)) {
            return;
        }
        try {
            Map<?, ?> raw = MAPPER.readValue(statePath.toFile(), Map.class);
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getKey() != null && entry.getValue() instanceof Number number) {
                    state.put(entry.getKey().toString(), number.longValue());
                }
            }
        } catch (IOException exception) {
            logger.warning("Failed to read Discord bot state from " + statePath + ": " + exception.getMessage());
        }
    }

    private void persist() {
        try {
            Files.createDirectories(statePath.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(statePath.toFile(), state);
        } catch (IOException exception) {
            logger.warning("Failed to write Discord bot state to " + statePath + ": " + exception.getMessage());
        }
    }
}
