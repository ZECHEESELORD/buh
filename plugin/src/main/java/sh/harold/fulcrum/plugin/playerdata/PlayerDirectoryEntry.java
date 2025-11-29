package sh.harold.fulcrum.plugin.playerdata;

import sh.harold.fulcrum.common.data.Document;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PlayerDirectoryEntry(
    UUID id,
    String username,
    Instant firstJoin,
    Instant lastJoin,
    Instant lastLeave,
    long playtimeSeconds,
    boolean pvpEnabled,
    String osuUsername,
    Integer osuRank,
    String osuCountry,
    String discordUsername,
    String discordGlobalName,
    String inviteSource
) {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    public static Optional<PlayerDirectoryEntry> fromDocument(Document document) {
        Objects.requireNonNull(document, "document");
        UUID id = parseUuid(document.key().id());
        if (id == null) {
            return Optional.empty();
        }
        String username = document.get("meta.username", String.class)
            .filter(value -> !value.isBlank())
            .orElse(id.toString());
        Instant firstJoin = parseInstant(document.get("meta.firstJoin", String.class).orElse(null));
        Instant lastJoin = parseInstant(document.get("meta.lastJoin", String.class).orElse(null));
        Instant lastLeave = parseInstant(document.get("meta.lastLeave", String.class).orElse(null));
        long playtimeSeconds = document.get("statistics.playtimeSeconds", Number.class)
            .map(Number::longValue)
            .orElse(0L);
        boolean pvpEnabled = document.get("settings.pvp.enabled", Boolean.class).orElse(false);
        String osuUsername = document.get("linking.osu.username", String.class).filter(value -> !value.isBlank()).orElse(null);
        Integer osuRank = document.get("linking.osu.rank", Number.class).map(Number::intValue).orElse(null);
        String osuCountry = document.get("linking.osu.country", String.class).filter(value -> !value.isBlank()).orElse(null);
        String discordUsername = document.get("linking.discord.username", String.class).filter(value -> !value.isBlank()).orElse(null);
        String discordGlobalName = document.get("linking.discord.globalName", String.class).filter(value -> !value.isBlank()).orElse(null);
        String inviteSource = document.get("linking.source", String.class).filter(value -> !value.isBlank()).orElse(null);
        return Optional.of(new PlayerDirectoryEntry(
            id,
            username,
            firstJoin,
            lastJoin,
            lastLeave,
            playtimeSeconds,
            pvpEnabled,
            osuUsername,
            osuRank,
            osuCountry,
            discordUsername,
            discordGlobalName,
            inviteSource
        ));
    }

    public String shortId() {
        return id.toString().substring(0, 8);
    }

    public String playtimeLabel() {
        Duration duration = Duration.ofSeconds(Math.max(0L, playtimeSeconds));
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    public String firstJoinLabel() {
        return formatDate(firstJoin);
    }

    public String lastSeenLabel(boolean online) {
        if (online) {
            return "NOW";
        }
        Instant lastSeen = lastLeave;
        if (lastSeen == null) {
            lastSeen = lastJoin;
        }
        if (lastSeen == null) {
            lastSeen = firstJoin;
        }
        return formatDateTime(lastSeen);
    }

    public boolean hasOsuUsername() {
        return osuUsername != null && !osuUsername.isBlank();
    }

    public boolean hasOsuRank() {
        return osuRank != null && osuRank > 0;
    }

    public boolean hasOsuCountry() {
        return osuCountry != null && !osuCountry.isBlank();
    }

    public boolean hasDiscord() {
        return (discordUsername != null && !discordUsername.isBlank())
            || (discordGlobalName != null && !discordGlobalName.isBlank());
    }

    public String osuUsernameLabel() {
        return hasOsuUsername() ? osuUsername : "Not linked";
    }

    public String osuRankLabel() {
        if (!hasOsuRank()) {
            return "Not linked";
        }
        return "#" + osuRank;
    }

    public String osuCountryLabel() {
        return hasOsuCountry() ? osuCountry.toUpperCase(Locale.ROOT) : "Not linked";
    }

    public String discordLabel() {
        if (!hasDiscord()) {
            return "Not linked";
        }
        StringBuilder builder = new StringBuilder();
        if (discordGlobalName != null && !discordGlobalName.isBlank()) {
            builder.append(discordGlobalName.trim());
        }
        if (discordUsername != null && !discordUsername.isBlank()) {
            if (builder.length() > 0) {
                builder.append(" ");
            }
            builder.append("(").append(discordUsername.trim()).append(")");
        }
        return builder.toString();
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String formatDate(Instant instant) {
        if (instant == null) {
            return "Unknown";
        }
        return DATE_FORMATTER.format(instant);
    }

    private static String formatDateTime(Instant instant) {
        if (instant == null) {
            return "Unknown";
        }
        return DATE_TIME_FORMATTER.format(instant);
    }
}
