package sh.harold.fulcrum.plugin.jukebox;

import java.util.Objects;
import java.util.Optional;

public record JukeboxTrackValidation(boolean valid, String message) {

    public JukeboxTrackValidation {
        message = message == null ? "" : message;
    }

    public static JukeboxTrackValidation ok() {
        return new JukeboxTrackValidation(true, "");
    }

    public static JukeboxTrackValidation invalid(String message) {
        Objects.requireNonNull(message, "message");
        return new JukeboxTrackValidation(false, message);
    }

    public Optional<String> messageText() {
        return message.isBlank() ? Optional.empty() : Optional.of(message);
    }
}

