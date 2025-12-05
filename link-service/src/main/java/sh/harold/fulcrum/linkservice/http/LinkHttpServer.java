package sh.harold.fulcrum.linkservice.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import sh.harold.fulcrum.common.data.impl.OAuthProvider;
import sh.harold.fulcrum.linkservice.config.ServiceConfig;
import sh.harold.fulcrum.linkservice.link.LinkStateRepository;
import sh.harold.fulcrum.linkservice.link.OAuthServiceFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LinkHttpServer implements AutoCloseable {

    private final ServiceConfig.HttpConfig config;
    private final LinkStateRepository stateRepository;
    private final OAuthServiceFactory oauthFactory;
    private final Logger logger;
    private final Executor executor;
    private HttpServer server;

    public LinkHttpServer(
        ServiceConfig.HttpConfig config,
        LinkStateRepository stateRepository,
        OAuthServiceFactory oauthFactory,
        Logger logger,
        Executor executor
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.stateRepository = Objects.requireNonNull(stateRepository, "stateRepository");
        this.oauthFactory = Objects.requireNonNull(oauthFactory, "oauthFactory");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.executor = executor;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(config.bindAddress(), config.port()), 0);
            server.createContext(oauthFactory.provider("osu").callbackPath(), new CallbackHandler("osu"));
            server.createContext(oauthFactory.provider("discord").callbackPath(), new CallbackHandler("discord"));
            server.setExecutor(executor);
            server.start();
            logger.info(() -> "link-service listening on " + config.bindAddress() + ":" + config.port());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start link-service HTTP server", exception);
        }
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
        }
    }

    private final class CallbackHandler implements HttpHandler {

        private final String providerName;

        private CallbackHandler(String providerName) {
            this.providerName = providerName;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            long started = System.nanoTime();
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "Method not allowed");
                return;
            }
            Map<String, String> query = QueryStrings.parse(exchange.getRequestURI().getRawQuery());
            String state = query.get("state");
            String code = query.get("code");
            if (state == null || code == null || state.isBlank() || code.isBlank()) {
                respond(exchange, 400, "Missing code or state. Run the link command again.");
                return;
            }
            try {
                handleCallback(exchange, state, code);
            } catch (TimeoutException timeoutException) {
                respond(exchange, 504, "Login timed out. Run the link command again.");
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Callback failed for provider " + providerName, exception);
                respond(exchange, 500, "Internal error. Try again.");
            } finally {
                long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
                logger.info(() -> "link-service callback " + providerName + " state=" + state + " in " + elapsedMillis + "ms");
                exchange.close();
            }
        }

        private void handleCallback(HttpExchange exchange, String stateToken, String code) throws Exception {
            var stateOpt = stateRepository.load(stateToken).toCompletableFuture().join();
            if (stateOpt.isEmpty()) {
                respond(exchange, 400, "This link request is missing or expired. Run the link command again.");
                return;
            }
            var state = stateOpt.get();
            if (state.completed()) {
                respond(exchange, 400, "This link request is already completed. Run the link command again.");
                return;
            }
            if (state.expiresAt().isBefore(java.time.Instant.now())) {
                stateRepository.markConsumed(stateToken);
                respond(exchange, 400, "This link request expired. Run the link command again.");
                return;
            }

            OAuthProvider provider = oauthFactory.provider(providerName);
            if (provider == null) {
                respond(exchange, 400, "Unknown provider: " + providerName);
                return;
            }

            String redirectUri = "http://" + config.bindAddress() + ":" + config.port() + provider.callbackPath();
            OAuthResult result = OAuthResult.exchange(provider, code, config.timeout(), redirectUri);
            if (!result.success()) {
                stateRepository.persistResult(stateToken, state, LinkStateRepository.LinkResult.failure(result.message())).toCompletableFuture().join();
                respond(exchange, 400, "Login failed: " + result.message());
                return;
            }
            stateRepository.persistResult(
                stateToken,
                state,
                LinkStateRepository.LinkResult.success(result.accountUsername(), result.providerAccountId())
            ).toCompletableFuture().join();
            stateRepository.markConsumed(stateToken).toCompletableFuture().join();
            respond(exchange, 200, "Account linked. You can close this tab and head back to the server.");
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
