package sh.harold.fulcrum.plugin.discordbot;

import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class RequestStore {

    private final DocumentCollection requests;

    public RequestStore(DataApi dataApi) {
        Objects.requireNonNull(dataApi, "dataApi");
        this.requests = dataApi.collection("link_requests");
    }

    public CompletionStage<Void> save(Request request) {
        Map<String, Object> payload = Map.of(
            "userId", request.discordId(),
            "username", request.discordUsername(),
            "createdAt", request.createdAt().toString(),
            "source", request.source(),
            "invitedBy", request.invitedBy(),
            "minecraft", Map.of(
                "uuid", request.minecraftId().toString(),
                "username", request.minecraftUsername()
            ),
            "osu", Map.of(
                "userId", request.osuUserId(),
                "username", request.osuUsername(),
                "rank", request.osuRank(),
                "country", request.osuCountry()
            ),
            "status", request.status().name(),
            "sponsorId", request.sponsorId().orElse(null)
        );
        return requests.create(UUID.randomUUID().toString(), payload).thenApply(ignored -> null);
    }

    public CompletionStage<Optional<Request>> load(String id) {
        return requests.load(id).thenApply(this::toRequest);
    }

    private Optional<Request> toRequest(Document document) {
        if (document == null || !document.exists()) {
            return Optional.empty();
        }
        Optional<Number> discordId = document.get("userId", Number.class);
        Optional<String> discordUsername = document.get("username", String.class);
        Optional<String> createdAt = document.get("createdAt", String.class);
        Optional<String> source = document.get("source", String.class);
        Optional<String> invitedBy = document.get("invitedBy", String.class);
        Optional<String> mcUuid = document.get("minecraft.uuid", String.class);
        Optional<String> mcUsername = document.get("minecraft.username", String.class);
        Optional<Number> osuUserId = document.get("osu.userId", Number.class);
        Optional<String> osuUsername = document.get("osu.username", String.class);
        Optional<Number> osuRank = document.get("osu.rank", Number.class);
        Optional<String> osuCountry = document.get("osu.country", String.class);
        Optional<String> status = document.get("status", String.class);
        Optional<Number> sponsorId = document.get("sponsorId", Number.class);
        if (discordId.isEmpty() || discordUsername.isEmpty() || createdAt.isEmpty()
            || source.isEmpty() || mcUuid.isEmpty() || mcUsername.isEmpty()
            || osuUserId.isEmpty() || osuUsername.isEmpty() || osuRank.isEmpty() || osuCountry.isEmpty() || status.isEmpty()) {
            return Optional.empty();
        }
        RequestState state = RequestState.valueOf(status.get());
        return Optional.of(new Request(
            discordId.get().longValue(),
            discordUsername.get(),
            source.get(),
            invitedBy.orElse(null),
            UUID.fromString(mcUuid.get()),
            mcUsername.get(),
            osuUserId.get().longValue(),
            osuUsername.get(),
            osuRank.get().intValue(),
            osuCountry.get(),
            Instant.parse(createdAt.get()),
            state,
            sponsorId.map(Number::longValue)
        ));
    }

    public enum RequestState {
        PENDING,
        APPROVED,
        DENIED
    }

    public record Request(
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
        Instant createdAt,
        RequestState status,
        Optional<Long> sponsorId
    ) {
        public Request {
            sponsorId = sponsorId == null ? Optional.empty() : sponsorId;
        }
    }
}
