package sh.harold.fulcrum.linkservice.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.time.Duration;

final class TimeSuffixDurationDeserializer extends StdDeserializer<Duration> {

    TimeSuffixDurationDeserializer() {
        super(Duration.class);
    }

    @Override
    public Duration deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String raw = parser.getValueAsString();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        raw = raw.trim();
        try {
            return Duration.parse(raw);
        } catch (Exception ignored) {
        }
        try {
            if (raw.endsWith("s")) {
                raw = raw.substring(0, raw.length() - 1);
            }
            long seconds = Long.parseLong(raw);
            return Duration.ofSeconds(seconds);
        } catch (Exception ignored) {
        }
        throw context.weirdStringException(raw, Duration.class, "Unrecognized duration format");
    }
}
