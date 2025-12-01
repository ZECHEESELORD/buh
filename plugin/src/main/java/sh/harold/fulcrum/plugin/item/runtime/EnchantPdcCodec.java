package sh.harold.fulcrum.plugin.item.runtime;

import java.util.HashMap;
import java.util.Map;

final class EnchantPdcCodec {

    private static final char ENTRY_SEPARATOR = ';';
    private static final char VALUE_SEPARATOR = '=';

    String encode(Map<String, Integer> enchants) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            if (!first) {
                builder.append(ENTRY_SEPARATOR);
            }
            builder.append(entry.getKey())
                .append(VALUE_SEPARATOR)
                .append(entry.getValue());
            first = false;
        }
        return builder.toString();
    }

    Map<String, Integer> decode(String raw) {
        Map<String, Integer> result = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        String[] entries = raw.split(String.valueOf(ENTRY_SEPARATOR));
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            int separatorIndex = entry.indexOf(VALUE_SEPARATOR);
            if (separatorIndex <= 0 || separatorIndex >= entry.length() - 1) {
                continue;
            }
            String idPart = entry.substring(0, separatorIndex);
            String valuePart = entry.substring(separatorIndex + 1);
            try {
                int level = Integer.parseInt(valuePart);
                result.put(idPart, level);
            } catch (NumberFormatException ignored) {
            }
        }
        return Map.copyOf(result);
    }
}
