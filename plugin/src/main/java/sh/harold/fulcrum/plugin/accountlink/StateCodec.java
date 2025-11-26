package sh.harold.fulcrum.plugin.accountlink;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

public final class StateCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String secret;
    private final String version;

    public StateCodec(String secret, String version) {
        this.secret = Objects.requireNonNull(secret, "secret");
        this.version = Objects.requireNonNull(version, "version");
    }

    public String encode(LinkState state) {
        Objects.requireNonNull(state, "state");
        byte[] payloadBytes = toBytes(new StatePayload(
            state.discordId(),
            state.uuid().toString(),
            state.username(),
            state.issuedAt().toString(),
            version
        ));
        byte[] signature = sign(payloadBytes);
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes);
        String sig = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        return payload + "." + sig;
    }

    public Optional<LinkState> decode(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            return Optional.empty();
        }
        byte[] payloadBytes;
        byte[] signature;
        try {
            payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            signature = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
        if (!verify(payloadBytes, signature)) {
            return Optional.empty();
        }
        StatePayload payload = fromBytes(payloadBytes);
        if (payload == null || !version.equals(payload.secretVersion())) {
            return Optional.empty();
        }
        try {
            return Optional.of(new LinkState(
                payload.discordId(),
                java.util.UUID.fromString(payload.uuid()),
                payload.username(),
                Instant.parse(payload.issuedAt())
            ));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign state", exception);
        }
    }

    private boolean verify(byte[] payload, byte[] signature) {
        byte[] computed = sign(payload);
        return java.security.MessageDigest.isEqual(computed, signature);
    }

    private byte[] toBytes(StatePayload payload) {
        try {
            return MAPPER.writeValueAsBytes(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to encode state", exception);
        }
    }

    private StatePayload fromBytes(byte[] payload) {
        try {
            return MAPPER.readValue(payload, StatePayload.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private record StatePayload(long discordId, String uuid, String username, String issuedAt, String secretVersion) {
    }
}
