package sh.harold.fulcrum.plugin.accountlink;

import org.bukkit.configuration.file.YamlConfiguration;
import sh.harold.fulcrum.plugin.config.FeatureConfigDefinition;
import sh.harold.fulcrum.plugin.config.FeatureConfigOption;
import sh.harold.fulcrum.plugin.config.FeatureConfigOptions;
import sh.harold.fulcrum.plugin.config.FeatureConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public record SourcesConfig(List<Source> sources, Optional<Long> linkedRoleId) {

    private static final FeatureConfigOption<List<String>> SOURCES = FeatureConfigOptions.stringListOption(
        "sources",
        List.of("6WC Canada", "6USC Server", "6WC USA")
    );
    private static final FeatureConfigOption<Long> LINKED_ROLE_ID = FeatureConfigOptions.longOption("discord.linked-role-id", 0L);

    public static final FeatureConfigDefinition CONFIG_DEFINITION = FeatureConfigDefinition.feature("account-link/sources")
        .option(SOURCES)
        .option(LINKED_ROLE_ID)
        .build();

    public static SourcesConfig from(FeatureConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        YamlConfiguration raw = configuration.raw();
        List<Source> parsed = new ArrayList<>();
        raw.getMapList("sources").forEach(entry -> {
            Object nameValue = entry.get("name");
            if (!(nameValue instanceof String name) || name.isBlank()) {
                return;
            }
            Long roleId = parseRoleId(entry.get("role-id"));
            parsed.add(new Source(name, roleId));
        });
        if (parsed.isEmpty()) {
            List<String> values = configuration.value(SOURCES);
            if (values != null) {
                for (String value : values) {
                    if (value == null || value.isBlank()) {
                        continue;
                    }
                    String[] parts = value.split("\\|", 2);
                    String name = parts[0].trim();
                    Long roleId = parts.length > 1 ? parseRoleId(parts[1]) : null;
                    parsed.add(new Source(name, roleId));
                }
            }
        }
        Optional<Long> linkedRoleId = Optional.ofNullable(parseRoleId(configuration.value(LINKED_ROLE_ID)));
        return new SourcesConfig(List.copyOf(parsed), linkedRoleId);
    }

    public List<String> sourceNames() {
        return sources.stream().map(Source::name).toList();
    }

    public Optional<Long> roleIdFor(String source) {
        if (source == null) {
            return Optional.empty();
        }
        return sources.stream()
            .filter(entry -> entry.name().equalsIgnoreCase(source))
            .findFirst()
            .map(Source::roleId)
            .filter(Objects::nonNull);
    }

    private static Long parseRoleId(Object raw) {
        if (raw instanceof Number number) {
            long value = number.longValue();
            return value > 0 ? value : null;
        }
        if (raw instanceof String string) {
            try {
                long value = Long.parseLong(string.trim());
                return value > 0 ? value : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public record Source(String name, Long roleId) {
    }
}
