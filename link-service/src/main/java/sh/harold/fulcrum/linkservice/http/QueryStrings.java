package sh.harold.fulcrum.linkservice.http;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class QueryStrings {

    private QueryStrings() {
    }

    static Map<String, String> parse(String raw) {
        Map<String, String> values = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return values;
        }
        for (String pair : raw.split("&")) {
            int idx = pair.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = urlDecode(pair.substring(0, idx));
            String value = urlDecode(pair.substring(idx + 1));
            values.put(key, value);
        }
        return values;
    }

    private static String urlDecode(String raw) {
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }
}
