package sh.harold.fulcrum.plugin.permissions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class LuckPermsTextFormat {

    public static final TextColor DEFAULT_COLOR = NamedTextColor.WHITE;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final Pattern HEX_COLOR = Pattern.compile("^#?[0-9a-fA-F]{6}$");

    private LuckPermsTextFormat() {
    }

    public static Component deserializePrefix(String rawPrefix) {
        if (rawPrefix == null || rawPrefix.isBlank()) {
            return Component.empty();
        }
        return LEGACY.deserialize(rawPrefix);
    }

    public static Optional<TextColor> parseColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim();
        if (HEX_COLOR.matcher(value).matches()) {
            try {
                TextColor hex = TextColor.fromHexString(value.startsWith("#") ? value : "#" + value);
                if (hex != null) {
                    return Optional.of(hex);
                }
            } catch (IllegalArgumentException ignored) {
                // fall through to named color lookup
            }
        }
        return Optional.ofNullable(NamedTextColor.NAMES.value(value.toLowerCase(Locale.ROOT)));
    }
}
