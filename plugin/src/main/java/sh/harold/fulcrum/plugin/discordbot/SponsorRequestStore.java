package sh.harold.fulcrum.plugin.discordbot;

import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class SponsorRequestStore {

    private final DocumentCollection sponsorRequests;

    public SponsorRequestStore(DataApi dataApi) {
        Objects.requireNonNull(dataApi, "dataApi");
        this.sponsorRequests = dataApi.collection("sponsor_requests");
    }

    public CompletionStage<Void> save(SponsorRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("discordId", request.discordId());
        payload.put("discordUsername", request.discordUsername());
        payload.put("source", request.source());
        if (request.invitedBy() != null && !request.invitedBy().isBlank()) {
            payload.put("invitedBy", request.invitedBy());
        }
        payload.put("minecraft", Map.of(
            "uuid", request.minecraftId().toString(),
            "username", request.minecraftUsername()
        ));
        payload.put("osu", Map.of(
            "userId", request.osuUserId(),
            "username", request.osuUsername(),
            "rank", request.osuRank(),
            "country", request.osuCountry()
        ));
        payload.put("sponsorId", request.sponsorId());
        payload.put("createdAt", request.createdAt().toString());
        payload.put("expiresAt", request.sponsorExpiresAt().toString());
        return sponsorRequests.create(Long.toString(request.messageId()), payload).thenApply(ignored -> null);
    }

    public CompletionStage<Optional<SponsorRequest>> load(long messageId) {
        return sponsorRequests.load(Long.toString(messageId)).thenApply(this::toRequest);
    }

    public CompletionStage<List<SponsorRequest>> loadPending() {
        return sponsorRequests.all()
            .thenApply(list -> list.stream()
                .map(this::toRequest)
                .flatMap(Optional::stream)
                .toList());
    }

    public CompletionStage<Void> delete(long messageId) {
        return sponsorRequests.delete(Long.toString(messageId)).thenApply(ignored -> null);
    }

    private Optional<SponsorRequest> toRequest(Document document) {
        if (document == null || !document.exists()) {
            return Optional.empty();
        }
        Optional<Number> discordId = document.get("discordId", Number.class);
        Optional<String> discordUsername = document.get("discordUsername", String.class);
        Optional<String> source = document.get("source", String.class);
        Optional<String> invitedBy = document.get("invitedBy", String.class);
        Optional<String> mcUuid = document.get("minecraft.uuid", String.class);
        Optional<String> mcUsername = document.get("minecraft.username", String.class);
        Optional<Number> osuUserId = document.get("osu.userId", Number.class);
        Optional<String> osuUsername = document.get("osu.username", String.class);
        Optional<Number> osuRank = document.get("osu.rank", Number.class);
        Optional<String> osuCountry = document.get("osu.country", String.class);
        Optional<Number> sponsorId = document.get("sponsorId", Number.class);
        Optional<String> createdAt = document.get("createdAt", String.class);
        Optional<String> expiresAt = document.get("expiresAt", String.class);

        if (discordId.isEmpty() || discordUsername.isEmpty() || source.isEmpty()
            || mcUuid.isEmpty() || mcUsername.isEmpty()
            || osuUserId.isEmpty() || osuUsername.isEmpty() || osuRank.isEmpty() || osuCountry.isEmpty()
            || sponsorId.isEmpty() || createdAt.isEmpty() || expiresAt.isEmpty()) {
            return Optional.empty();
        }

        UUID minecraftId = parseUuid(mcUuid.get());
        if (minecraftId == null) {
            return Optional.empty();
        }

        Instant created;
        Instant expires;
        try {
            created = Instant.parse(createdAt.get());
            expires = Instant.parse(expiresAt.get());
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }

        return Optional.of(new SponsorRequest(
            Long.parseLong(document.key().id()),
            discordId.get().longValue(),
            discordUsername.get(),
            source.get(),
            invitedBy.orElse(null),
            minecraftId,
            mcUsername.get(),
            osuUserId.get().longValue(),
            osuUsername.get(),
            osuRank.get().intValue(),
            osuCountry.get(),
            sponsorId.get().longValue(),
            created,
            expires
        ));
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record SponsorRequest(
        long messageId,
        long discordId,
        String discordUsername,
        String source,
        String invitedBy,
        UUID minecraftId,
        String minecraftUsername,
        long osuUserId,
        String osuUsername,
        int osuRank,
        String osuCountry,
        long sponsorId,
        Instant createdAt,
        Instant sponsorExpiresAt
    ) {
    }
}
