package sh.harold.fulcrum.plugin.mob.pdc;

import sh.harold.fulcrum.stats.core.StatId;

import java.util.HashMap;
import java.util.Map;

final class MobStatPdcCodec {

    private static final char ENTRY_SEPARATOR = ';';
    private static final char VALUE_SEPARATOR = '=';

    String encode(Map<StatId, Double> stats) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<StatId, Double> entry : stats.entrySet()) {
            if (!first) {
                builder.append(ENTRY_SEPARATOR);
            }
            builder.append(entry.getKey().value())
                .append(VALUE_SEPARATOR)
                .append(entry.getValue());
            first = false;
        }
        return builder.toString();
    }

    Map<StatId, Double> decode(String raw) {
        Map<StatId, Double> result = new HashMap<>();
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
                double value = Double.parseDouble(valuePart);
                result.put(new StatId(idPart), value);
            } catch (NumberFormatException ignored) {
            }
        }
        return Map.copyOf(result);
    }
}

