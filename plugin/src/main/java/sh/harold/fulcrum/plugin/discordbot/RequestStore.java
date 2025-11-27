package sh.harold.fulcrum.plugin.discordbot;

import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;

import java.time.Instant;
import java.util.HashMap;
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
        String key = request.minecraftId().toString();
        return requests.load(key)
            .thenCompose(document -> {
                boolean exists = document != null && document.exists();
                Map<String, Object> payload = new HashMap<>();
                payload.put("userId", request.discordId());
                payload.put("username", request.discordUsername());
                payload.put("createdAt", request.createdAt().toString());
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
                payload.put("status", request.status().name());
                payload.put("consumed", request.consumed());
                payload.put("canReapply", request.canReapply());
                request.decisionReason().ifPresent(reason -> payload.put("decisionReason", reason));
                request.sponsorId().ifPresent(id -> payload.put("sponsorId", id));
                request.requestMessageId().ifPresent(id -> payload.put("requestMessageId", id));
                if (exists) {
                    document.get("consumed", Boolean.class).ifPresent(consumed -> payload.put("consumed", consumed));
                }
                return exists
                    ? document.overwrite(payload)
                    : requests.create(key, payload).thenApply(ignored -> null);
            });
    }

    public CompletionStage<Optional<Request>> load(String id) {
        return requests.load(id).thenApply(this::toRequest);
    }

    public CompletionStage<Optional<Request>> findByMessageId(long messageId) {
        return requests.all()
            .thenApply(list -> list.stream()
                .map(this::toRequest)
                .flatMap(Optional::stream)
                .filter(request -> request.requestMessageId().isPresent() && request.requestMessageId().get() == messageId)
                .findFirst());
    }

    public CompletionStage<Void> deleteByMessageId(long messageId) {
        return requests.all()
            .thenCompose(list -> {
                Optional<Document> match = list.stream()
                    .filter(Document::exists)
                    .filter(doc -> doc.get("requestMessageId", Number.class).map(Number::longValue).orElse(0L) == messageId)
                    .findFirst();
                if (match.isEmpty()) {
                    return CompletableFuture.completedFuture(null);
                }
                return requests.delete(match.get().key().id()).thenApply(ignored -> null);
            });
    }

    public CompletionStage<Optional<Request>> findLatestByDiscordId(long discordId) {
        return requests.all()
            .thenApply(list -> list.stream()
                .map(this::toRequest)
                .flatMap(Optional::stream)
                .filter(request -> request.discordId() == discordId)
                .max((a, b) -> {
                    Instant aCreated = a.createdAt();
                    Instant bCreated = b.createdAt();
                    return aCreated.compareTo(bCreated);
                }));
    }

    public CompletionStage<Void> updateDecisionByMessageId(long messageId, RequestState status, boolean canReapply, Optional<String> decisionReason) {
        return requests.all()
            .thenCompose(list -> list.stream()
                .filter(Document::exists)
                .filter(doc -> doc.get("requestMessageId", Number.class).map(Number::longValue).orElse(0L) == messageId)
                .findFirst()
                .map(doc -> {
                    CompletionStage<Void> statusStage = doc.set("status", status.name());
                    CompletionStage<Void> reapplyStage = doc.set("canReapply", canReapply);
                    CompletionStage<Void> reasonStage = decisionReason.isPresent()
                        ? doc.set("decisionReason", decisionReason.get())
                        : doc.remove("decisionReason");
                    return CompletableFuture.allOf(
                        statusStage.toCompletableFuture(),
                        reapplyStage.toCompletableFuture(),
                        reasonStage.toCompletableFuture()
                    );
                })
                .orElse(CompletableFuture.completedFuture(null)));
    }

    public CompletionStage<Void> updateCanReapplyByMessageId(long messageId, boolean canReapply) {
        return requests.all()
            .thenCompose(list -> list.stream()
                .filter(Document::exists)
                .filter(doc -> doc.get("requestMessageId", Number.class).map(Number::longValue).orElse(0L) == messageId)
                .findFirst()
                .map(doc -> doc.set("canReapply", canReapply))
                .orElse(CompletableFuture.completedFuture(null)));
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
        Optional<Number> requestMessageId = document.get("requestMessageId", Number.class);
        boolean canReapply = document.get("canReapply", Boolean.class).orElse(true);
        boolean consumed = document.get("consumed", Boolean.class).orElse(false);
        Optional<String> decisionReason = document.get("decisionReason", String.class);
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
            sponsorId.map(Number::longValue),
            requestMessageId.map(Number::longValue),
            canReapply,
            consumed,
            decisionReason
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
        Optional<Long> sponsorId,
        Optional<Long> requestMessageId,
        boolean canReapply,
        boolean consumed,
        Optional<String> decisionReason
    ) {
        public Request {
            sponsorId = sponsorId == null ? Optional.empty() : sponsorId;
            requestMessageId = requestMessageId == null ? Optional.empty() : requestMessageId;
            decisionReason = decisionReason == null ? Optional.empty() : decisionReason;
        }
    }
}
