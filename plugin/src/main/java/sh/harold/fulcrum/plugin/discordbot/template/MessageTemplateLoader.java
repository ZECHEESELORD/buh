package sh.harold.fulcrum.plugin.discordbot.template;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public final class MessageTemplateLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MessageTemplateLoader() {
    }

    public static MessageTemplate load(Path path, MessageTemplate fallback, Logger logger) {
        try {
            if (!Files.exists(path)) {
                if (fallback != null) {
                    writeDefault(path, fallback, logger);
                }
                return fallback;
            }
            return MAPPER.readValue(path.toFile(), MessageTemplate.class);
        } catch (Exception exception) {
            if (logger != null) {
                logger.warning("Failed to load message template from " + path + ": " + exception.getMessage());
            }
            return fallback;
        }
    }

    private static void writeDefault(Path path, MessageTemplate template, Logger logger) {
        try {
            Files.createDirectories(path.getParent());
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(template);
            Files.writeString(path, json, StandardCharsets.UTF_8);
            if (logger != null) {
                logger.info("Wrote default Discord message template to " + path);
            }
        } catch (IOException exception) {
            if (logger != null) {
                logger.warning("Failed to write default template to " + path + ": " + exception.getMessage());
            }
        }
    }
}
