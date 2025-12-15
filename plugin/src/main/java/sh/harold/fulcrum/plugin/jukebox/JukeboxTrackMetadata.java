package sh.harold.fulcrum.plugin.jukebox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JukeboxTrackMetadata(
    @JsonProperty("schemaVersion") int schemaVersion,
    @JsonProperty("trackId") String trackId,
    @JsonProperty("ownerUuid") UUID ownerUuid,
    @JsonProperty("status") JukeboxTrackStatus status,
    @JsonProperty("createdAt") String createdAt,
    @JsonProperty("readyAt") String readyAt,
    @JsonProperty("sampleRateHz") int sampleRateHz,
    @JsonProperty("channels") int channels,
    @JsonProperty("sampleFormat") String sampleFormat,
    @JsonProperty("bytesPerSample") int bytesPerSample,
    @JsonProperty("frameSamplesPerChannel") int frameSamplesPerChannel,
    @JsonProperty("frameBytes") int frameBytes,
    @JsonProperty("samplesPerChannel") long samplesPerChannel,
    @JsonProperty("fileByteLength") long fileByteLength,
    @JsonProperty("title") String title
) {

    public JukeboxTrackMetadata {
        Objects.requireNonNull(trackId, "trackId");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(status, "status");
    }

    public Optional<String> titleText() {
        return Optional.ofNullable(title).filter(text -> !text.isBlank());
    }

    public double durationSeconds() {
        if (sampleRateHz <= 0) {
            return 0D;
        }
        return samplesPerChannel / (double) sampleRateHz;
    }
}
