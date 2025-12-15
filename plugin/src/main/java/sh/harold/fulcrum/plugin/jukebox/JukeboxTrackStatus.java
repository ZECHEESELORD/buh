package sh.harold.fulcrum.plugin.jukebox;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum JukeboxTrackStatus {
    WAITING_UPLOAD,
    PROCESSING,
    READY,
    REJECTED,
    FAILED;

    @JsonCreator
    public static JukeboxTrackStatus fromWire(String raw) {
        if (raw == null || raw.isBlank()) {
            return FAILED;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "WAITING_UPLOAD", "WAITING", "UPLOAD_PENDING" -> WAITING_UPLOAD;
            case "PROCESSING", "TRANSCODING" -> PROCESSING;
            case "READY" -> READY;
            case "REJECTED" -> REJECTED;
            case "FAILED", "ERROR" -> FAILED;
            default -> FAILED;
        };
    }
}

