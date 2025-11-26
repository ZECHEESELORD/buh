package sh.harold.fulcrum.plugin.accountlink;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AccountLinkHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService executor;

    public AccountLinkHttpServer(
        AccountLinkService linkService,
        AccountLinkConfig config,
        StateCodec stateCodec,
        OsuOAuthClient osuClient,
        Logger logger
    ) {
        Objects.requireNonNull(linkService, "linkService");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(stateCodec, "stateCodec");
        Objects.requireNonNull(osuClient, "osuClient");
        Objects.requireNonNull(logger, "logger");

        try {
            server = HttpServer.create(new InetSocketAddress(config.bindAddress(), config.port()), 0);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start account link HTTP server", exception);
        }
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.createContext("/callback", new CallbackHandler(linkService, stateCodec, osuClient, config.stateTtl(), logger));
        server.setExecutor(executor);
    }

    public void start() {
        server.start();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.close();
    }

    private static final class CallbackHandler implements HttpHandler {

        private final AccountLinkService linkService;
        private final StateCodec stateCodec;
        private final OsuOAuthClient osuClient;
        private final Duration stateTtl;
        private final Logger logger;

        private CallbackHandler(AccountLinkService linkService, StateCodec stateCodec, OsuOAuthClient osuClient, Duration stateTtl, Logger logger) {
            this.linkService = linkService;
            this.stateCodec = stateCodec;
            this.osuClient = osuClient;
            this.stateTtl = stateTtl;
            this.logger = logger;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "Method Not Allowed");
                return;
            }
            Map<String, String> params = queryParams(exchange.getRequestURI());
            String code = params.get("code");
            String stateToken = params.get("state");
            if (code == null || stateToken == null) {
                respond(exchange, 400, "Missing code or state.");
                return;
            }

            Optional<LinkState> decoded = stateCodec.decode(stateToken);
            if (decoded.isEmpty()) {
                respond(exchange, 400, "Invalid state.");
                return;
            }
            LinkState state = decoded.get();
            if (isExpired(state)) {
                respond(exchange, 400, "Link expired. Please try again.");
                return;
            }

            try {
                AccountLinkService.OsuProfile profile = osuClient.exchangeForProfile(code)
                    .toCompletableFuture()
                    .join();

                AccountLinkService.LinkResult result = linkService.createLink(
                    new AccountLinkService.LinkRequest(state.uuid(), state.discordId(), state.username(), profile)
                ).toCompletableFuture().join();

                if (result instanceof AccountLinkService.LinkResult.Success) {
                    respond(exchange, 200, "Linked! You may now join the server.");
                } else if (result instanceof AccountLinkService.LinkResult.Rejected rejected) {
                    respond(exchange, 400, rejected.reason());
                } else {
                    respond(exchange, 500, "Unknown link result.");
                }
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Link callback failed for state " + state, exception);
                respond(exchange, 500, "Link failed; please try again.");
            }
        }

        private boolean isExpired(LinkState state) {
            Instant cutoff = Instant.now().minus(stateTtl);
            return state.issuedAt().isBefore(cutoff);
        }

        private Map<String, String> queryParams(URI uri) {
            String raw = uri.getRawQuery();
            if (raw == null || raw.isBlank()) {
                return Map.of();
            }
            String[] pairs = raw.split("&");
            java.util.Map<String, String> params = new java.util.LinkedHashMap<>();
            for (String pair : pairs) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2) {
                    params.put(urlDecode(parts[0]), urlDecode(parts[1]));
                }
            }
            return params;
        }

        private String urlDecode(String value) {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
        }

        private void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
