package sh.harold.fulcrum.plugin.unlockable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import sh.harold.fulcrum.plugin.permissions.LuckPermsTextFormat;
import sh.harold.fulcrum.plugin.playerdata.PlayerDirectoryEntry;
import sh.harold.fulcrum.plugin.playerdata.PlayerDirectoryService;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ChatCosmeticPrefixService {

    private final Supplier<Optional<UnlockableService>> unlockableServiceSupplier;
    private final Supplier<Optional<CosmeticRegistry>> cosmeticRegistrySupplier;
    private final Supplier<Optional<PlayerDirectoryService>> playerDirectoryServiceSupplier;
    private final Logger logger;

    private static final Component OSU_OPEN = Component.text("[", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false);
    private static final Component OSU_CLOSE = Component.text("]", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false);

    public static Component renderOsuRankBadge(Integer rank) {
        if (rank == null || rank <= 0) {
            return Component.empty();
        }
        Component rankComponent = OsuRankTier.forRank(rank).render("#" + rank);
        return OSU_OPEN.append(rankComponent).append(OSU_CLOSE);
    }

    public ChatCosmeticPrefixService(
        Supplier<Optional<UnlockableService>> unlockableServiceSupplier,
        Supplier<Optional<CosmeticRegistry>> cosmeticRegistrySupplier,
        Supplier<Optional<PlayerDirectoryService>> playerDirectoryServiceSupplier,
        Logger logger
    ) {
        this.unlockableServiceSupplier = Objects.requireNonNull(unlockableServiceSupplier, "unlockableServiceSupplier");
        this.cosmeticRegistrySupplier = Objects.requireNonNull(cosmeticRegistrySupplier, "cosmeticRegistrySupplier");
        this.playerDirectoryServiceSupplier = Objects.requireNonNull(playerDirectoryServiceSupplier, "playerDirectoryServiceSupplier");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public CompletableFuture<Component> prefix(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");

        Optional<UnlockableService> unlockableService = safe(unlockableServiceSupplier, "unlockable service");
        Optional<CosmeticRegistry> cosmeticRegistry = safe(cosmeticRegistrySupplier, "cosmetic registry");
        Optional<PlayerDirectoryService> directoryService = safe(playerDirectoryServiceSupplier, "player directory service");
        if (unlockableService.isEmpty() || cosmeticRegistry.isEmpty()) {
            return CompletableFuture.completedFuture(Component.empty());
        }

        UnlockableService unlockables = unlockableService.get();
        CosmeticRegistry cosmetics = cosmeticRegistry.get();
        PlayerDirectoryService directory = directoryService.orElse(null);
        CompletionStage<PlayerUnlockableState> stateStage = unlockables.cachedState(playerId)
            .<CompletionStage<PlayerUnlockableState>>map(CompletableFuture::completedFuture)
            .orElseGet(() -> unlockables.loadState(playerId));

        return stateStage
            .thenCompose(state -> resolveFromState(playerId, state, cosmetics, directory))
            .exceptionally(throwable -> {
                logger.log(Level.WARNING, "Failed to resolve chat cosmetic prefix for " + playerId, throwable);
                return Component.empty();
            })
            .toCompletableFuture();
    }

    public Component prefixNow(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");

        Optional<UnlockableService> unlockableService = safe(unlockableServiceSupplier, "unlockable service");
        Optional<CosmeticRegistry> cosmeticRegistry = safe(cosmeticRegistrySupplier, "cosmetic registry");
        Optional<PlayerDirectoryService> directoryService = safe(playerDirectoryServiceSupplier, "player directory service");
        if (unlockableService.isEmpty() || cosmeticRegistry.isEmpty()) {
            return Component.empty();
        }

        PlayerUnlockableState cached = unlockableService.get().cachedState(playerId).orElse(null);
        if (cached == null) {
            return Component.empty();
        }
        PlayerDirectoryService directory = directoryService.orElse(null);
        CosmeticRegistry cosmetics = cosmeticRegistry.get();
        return resolveFromCached(playerId, cached, cosmetics, directory);
    }

    public static Component combinePrefixes(Component leading, Component trailing) {
        Component left = leading == null ? Component.empty() : leading;
        Component right = trailing == null ? Component.empty() : trailing;
        if (left.equals(Component.empty())) {
            return right;
        }
        if (right.equals(Component.empty())) {
            return left;
        }
        return Component.text()
            .append(left)
            .append(Component.space())
            .append(right)
            .build();
    }

    private CompletionStage<Component> resolveFromState(
        UUID playerId,
        PlayerUnlockableState state,
        CosmeticRegistry cosmeticRegistry,
        PlayerDirectoryService directoryService
    ) {
        if (state == null) {
            return CompletableFuture.completedFuture(Component.empty());
        }
        return equippedPrefixCosmetic(state, cosmeticRegistry)
            .map(cosmetic -> resolveCosmetic(playerId, cosmetic, directoryService))
            .orElseGet(() -> CompletableFuture.completedFuture(Component.empty()));
    }

    private Component resolveFromCached(
        UUID playerId,
        PlayerUnlockableState state,
        CosmeticRegistry cosmeticRegistry,
        PlayerDirectoryService directoryService
    ) {
        return equippedPrefixCosmetic(state, cosmeticRegistry)
            .map(cosmetic -> resolveCosmeticCached(playerId, cosmetic, directoryService))
            .orElse(Component.empty());
    }

    private Optional<Cosmetic> equippedPrefixCosmetic(PlayerUnlockableState state, CosmeticRegistry cosmeticRegistry) {
        if (state == null) {
            return Optional.empty();
        }
        UnlockableId equipped = state.equippedCosmetics(CosmeticSection.CHAT_PREFIX).stream()
            .min(Comparator.naturalOrder())
            .orElse(null);
        if (equipped == null) {
            return Optional.empty();
        }
        return cosmeticRegistry.cosmetic(equipped);
    }

    private CompletionStage<Component> resolveCosmetic(UUID playerId, Cosmetic cosmetic, PlayerDirectoryService directoryService) {
        if (cosmetic instanceof ChatPrefixCosmetic prefixCosmetic) {
            Component prefix = LuckPermsTextFormat.deserializePrefix(prefixCosmetic.prefix());
            return CompletableFuture.completedFuture(applyCosmeticTooltip(prefix, prefixCosmetic));
        }
        if (cosmetic instanceof OsuRankChatPrefixCosmetic) {
            if (directoryService == null) {
                return CompletableFuture.completedFuture(Component.empty());
            }
            return directoryService.loadEntry(playerId)
                .thenApply(entry -> applyCosmeticTooltip(renderOsuRank(entry), cosmetic))
                .exceptionally(throwable -> {
                    logger.log(Level.WARNING, "Failed to resolve osu! rank chat prefix for " + playerId, throwable);
                    return Component.empty();
                });
        }
        return CompletableFuture.completedFuture(Component.empty());
    }

    private Component resolveCosmeticCached(UUID playerId, Cosmetic cosmetic, PlayerDirectoryService directoryService) {
        if (cosmetic instanceof ChatPrefixCosmetic prefixCosmetic) {
            return applyCosmeticTooltip(LuckPermsTextFormat.deserializePrefix(prefixCosmetic.prefix()), prefixCosmetic);
        }
        if (cosmetic instanceof OsuRankChatPrefixCosmetic) {
            if (directoryService == null) {
                return Component.empty();
            }
            try {
                return applyCosmeticTooltip(
                    renderOsuRank(directoryService.loadEntry(playerId).toCompletableFuture().getNow(null)),
                    cosmetic
                );
            } catch (RuntimeException runtimeException) {
                logger.log(Level.WARNING, "Failed to resolve osu! rank chat prefix for " + playerId, runtimeException);
                return Component.empty();
            }
        }
        return Component.empty();
    }

    private Component renderOsuRank(PlayerDirectoryEntry entry) {
        if (entry == null || !entry.hasOsuRank()) {
            return Component.empty();
        }
        int rank = entry.osuRank();
        return renderOsuRankBadge(rank);
    }

    private Component applyCosmeticTooltip(Component prefix, Cosmetic cosmetic) {
        Component safePrefix = prefix == null ? Component.empty() : prefix;
        if (safePrefix.equals(Component.empty()) || cosmetic == null) {
            return safePrefix;
        }
        UnlockableDefinition definition = cosmetic.definition();
        if (definition == null) {
            return safePrefix;
        }
        return safePrefix.hoverEvent(HoverEvent.showText(cosmeticTooltip(definition)));
    }

    private Component cosmeticTooltip(UnlockableDefinition definition) {
        Component newline = Component.newline();
        return Component.text()
            .append(Component.text(definition.name(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false))
            .append(newline)
            .append(Component.text(definition.description(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
            .build();
    }

    private enum OsuRankTier {
        LUSTROUS(100, RankStyle.gradient("#FFE600", "#ED82FF")),
        RADIANT(1_663, RankStyle.gradient("#97DCFF", "#ED82FF")),
        RHODIUM(8_330, RankStyle.gradient("#D9F8D3", "#A0CF96")),
        PLATINUM(16_640, RankStyle.gradient("#A8F0EF", "#52E0DF")),
        GOLD(83_120, RankStyle.gradient("#F0E4A8", "#E0C952")),
        SILVER(166_210, RankStyle.gradient("#E0E0EB", "#A3A3C2")),
        BRONZE(831_500, RankStyle.gradient("#B88F7A", "#855C47")),
        IRON(Integer.MAX_VALUE, RankStyle.solid("#BAB3AB"));

        private final int maxRank;
        private final RankStyle style;

        OsuRankTier(int maxRank, RankStyle style) {
            this.maxRank = maxRank;
            this.style = style;
        }

        static OsuRankTier forRank(int rank) {
            int safeRank = Math.max(1, rank);
            for (OsuRankTier tier : values()) {
                if (safeRank <= tier.maxRank) {
                    return tier;
                }
            }
            return IRON;
        }

        Component render(String text) {
            return style.render(text);
        }
    }

    private record RankStyle(TextColor start, TextColor end) {

        RankStyle {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
        }

        static RankStyle solid(String hex) {
            TextColor color = requireHexColor(hex);
            return new RankStyle(color, color);
        }

        static RankStyle gradient(String startHex, String endHex) {
            return new RankStyle(requireHexColor(startHex), requireHexColor(endHex));
        }

        Component render(String text) {
            if (text == null || text.isBlank()) {
                return Component.empty();
            }
            if (start.equals(end)) {
                return Component.text(text, start).decoration(TextDecoration.ITALIC, false);
            }
            return gradient(text, start, end);
        }

        private static Component gradient(String text, TextColor start, TextColor end) {
            int length = text.codePointCount(0, text.length());
            if (length <= 0) {
                return Component.empty();
            }
            if (length == 1) {
                return Component.text(text, start).decoration(TextDecoration.ITALIC, false);
            }
            var builder = Component.text();
            int index = 0;
            for (int offset = 0; offset < text.length(); ) {
                int codePoint = text.codePointAt(offset);
                double t = (double) index / (length - 1);
                TextColor color = interpolate(start, end, t);
                builder.append(Component.text(new String(Character.toChars(codePoint)), color).decoration(TextDecoration.ITALIC, false));
                offset += Character.charCount(codePoint);
                index++;
            }
            return builder.build();
        }

        private static TextColor requireHexColor(String raw) {
            String value = raw == null ? "" : raw.trim();
            if (!value.startsWith("#")) {
                value = "#" + value;
            }
            TextColor parsed = TextColor.fromHexString(value);
            if (parsed == null) {
                throw new IllegalArgumentException("Invalid hex color: " + raw);
            }
            return parsed;
        }

        private static TextColor interpolate(TextColor start, TextColor end, double t) {
            int s = start.value();
            int e = end.value();
            int sr = (s >> 16) & 0xFF;
            int sg = (s >> 8) & 0xFF;
            int sb = s & 0xFF;
            int er = (e >> 16) & 0xFF;
            int eg = (e >> 8) & 0xFF;
            int eb = e & 0xFF;

            int r = clamp((int) Math.round(sr + (er - sr) * t));
            int g = clamp((int) Math.round(sg + (eg - sg) * t));
            int b = clamp((int) Math.round(sb + (eb - sb) * t));
            return TextColor.color((r << 16) | (g << 8) | b);
        }

        private static int clamp(int value) {
            return Math.max(0, Math.min(255, value));
        }
    }

    private <T> Optional<T> safe(Supplier<Optional<T>> supplier, String label) {
        try {
            return supplier.get();
        } catch (RuntimeException runtimeException) {
            logger.log(Level.WARNING, "Failed to resolve " + label, runtimeException);
            return Optional.empty();
        }
    }
}
