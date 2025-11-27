package sh.harold.fulcrum.plugin.discordbot;

import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.Document;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class OsuLookupService {

    private final DataApi dataApi;

    public OsuLookupService(DataApi dataApi) {
        this.dataApi = dataApi;
    }

    public CompletionStage<Optional<OsuInfo>> fromTicket(UUID playerId) {
        return dataApi.collection("link_requests").load(playerId.toString())
            .thenApply(this::toOsuInfo);
    }

    public CompletionStage<Optional<OsuInfo>> fromPlayer(UUID playerId) {
        return dataApi.collection("players").load(playerId.toString())
            .thenApply(this::toOsuInfo);
    }

    private Optional<OsuInfo> toOsuInfo(Document document) {
        if (document == null || !document.exists()) {
            return Optional.empty();
        }
        Optional<Number> userId = document.get("osu.userId", Number.class);
        Optional<String> username = document.get("osu.username", String.class);
        Optional<Number> rank = document.get("osu.rank", Number.class);
        Optional<String> country = document.get("osu.country", String.class);
        if (userId.isEmpty() || username.isEmpty() || rank.isEmpty() || country.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new OsuInfo(userId.get().longValue(), username.get(), rank.get().intValue(), country.get()));
    }

    public record OsuInfo(long userId, String username, int rank, String country) {
    }
}
