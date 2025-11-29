package sh.harold.fulcrum.plugin.osu;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

final class OsuLinkHttpServer implements AutoCloseable {

    private final OsuLinkService service;
    private final LinkAccountConfig config;
    private final Logger logger;
    private HttpServer server;
    private ExecutorService executor;

    OsuLinkHttpServer(OsuLinkService service, LinkAccountConfig config, Logger logger) {
        this.service = Objects.requireNonNull(service, "service");
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    void start() {
        try {
            executor = Executors.newVirtualThreadPerTaskExecutor();
            server = HttpServer.create(new InetSocketAddress(config.bindAddress(), config.port()), 0);
            server.createContext(config.osu().callbackPath(), new CallbackHandler(OsuLinkService.Provider.OSU));
            server.createContext(config.discord().callbackPath(), new CallbackHandler(OsuLinkService.Provider.DISCORD));
            server.setExecutor(executor);
            server.start();
            logger.info("link account server listening on " + config.bindAddress() + ":" + config.port()
                + " (callbacks osu=" + config.osu().callbackPath() + ", discord=" + config.discord().callbackPath() + ")");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start link account server", exception);
        }
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.close();
            executor = null;
        }
    }

    private final class CallbackHandler implements HttpHandler {

        private final OsuLinkService.Provider provider;

        private CallbackHandler(OsuLinkService.Provider provider) {
            this.provider = provider;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    respond(exchange, 405, "Method not allowed");
                    return;
                }
                Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                if (query.containsKey("error")) {
                    String message = query.getOrDefault("error_description", "Login was cancelled.");
                    respondWithPage(exchange, OsuLinkService.LinkResult.failure("Login failed: " + message));
                    return;
                }

                String state = query.get("state");
                String code = query.get("code");
                if (state == null || state.isBlank() || code == null || code.isBlank()) {
                    respondWithPage(exchange, OsuLinkService.LinkResult.failure("Missing code or state. Run the link command again."));
                    return;
                }

                OsuLinkService.LinkResult result = provider == OsuLinkService.Provider.OSU
                    ? service.completeOsuLink(urlDecode(state), code)
                    : service.completeDiscordLink(urlDecode(state), code);
                respondWithPage(exchange, result);
            } catch (Exception exception) {
                logger.log(Level.WARNING, "link handler failed", exception);
                respond(exchange, 500, "Internal error. Try again.");
            } finally {
                exchange.close();
            }
        }

        private Map<String, String> parseQuery(String raw) {
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

        private String urlDecode(String raw) {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        }

        private void respondWithPage(HttpExchange exchange, OsuLinkService.LinkResult result) throws IOException {
            int status = result.success() ? 200 : 400;
            String title = result.success() ? "Account linked" : "Account link failed";
            String detail = result.success()
                ? "Linked account <strong>%s</strong> to Minecraft user <strong>%s</strong>. You can close this tab and head back to the server."
                .formatted(escape(result.accountUsername()), escape(result.playerUsername()))
                : escape(result.message());
            String body = """
                <html>
                <head><title>%s</title></head>
                <body>
                <h2>%s</h2>
                <p>%s</p>
                </body>
                </html>
                """.formatted(title, title, detail);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            respond(exchange, status, body);
        }

        private String escape(String value) {
            if (value == null) {
                return "";
            }
            return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
        }

        private void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
    }
}
