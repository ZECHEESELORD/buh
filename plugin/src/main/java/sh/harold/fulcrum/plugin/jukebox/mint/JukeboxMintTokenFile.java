package sh.harold.fulcrum.plugin.jukebox.mint;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JukeboxMintTokenFile(
    @JsonProperty("schemaVersion") int schemaVersion,
    @JsonProperty("trackId") String trackId,
    @JsonProperty("ownerUuid") UUID ownerUuid,
    @JsonProperty("token") String token,
    @JsonProperty("createdAtEpochSeconds") long createdAtEpochSeconds,
    @JsonProperty("expiresAtEpochSeconds") long expiresAtEpochSeconds,
    @JsonProperty("usedAtEpochSeconds") Long usedAtEpochSeconds
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public JukeboxMintTokenFile {
        Objects.requireNonNull(trackId, "trackId");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(token, "token");
    }

    public boolean isUsed() {
        return usedAtEpochSeconds != null && usedAtEpochSeconds > 0;
    }

    public boolean isExpired(long nowEpochSeconds) {
        return expiresAtEpochSeconds > 0 && nowEpochSeconds >= expiresAtEpochSeconds;
    }
}

