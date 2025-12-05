package sh.harold.fulcrum.linkservice;

import sh.harold.fulcrum.common.data.impl.MySqlDocumentStore;
import sh.harold.fulcrum.common.data.ledger.MySqlLedgerRepository;
import sh.harold.fulcrum.linkservice.config.ServiceConfig;
import sh.harold.fulcrum.linkservice.http.LinkHttpServer;
import sh.harold.fulcrum.linkservice.link.LinkStateRepository;
import sh.harold.fulcrum.linkservice.link.OAuthServiceFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public final class LinkServiceApplication {

    public static void main(String[] args) {
        Logger logger = Logger.getLogger("link-service");
        ServiceConfig config = ServiceConfig.load();

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        MySqlDocumentStore documentStore = new MySqlDocumentStore(
            config.mysql().jdbcUrl(),
            config.mysql().username(),
            config.mysql().password(),
            config.mysql().maxPoolSize(),
            config.mysql().connectionTimeoutMillis(),
            logger,
            executor
        );
        MySqlLedgerRepository ledgerRepository = new MySqlLedgerRepository(
            config.mysql().jdbcUrl(),
            config.mysql().username(),
            config.mysql().password(),
            config.mysql().maxPoolSize(),
            config.mysql().connectionTimeoutMillis(),
            logger,
            executor
        );
        LinkStateRepository stateRepository = new LinkStateRepository(documentStore, logger);
        OAuthServiceFactory oauthFactory = new OAuthServiceFactory(config.oauth(), logger);

        LinkHttpServer server = new LinkHttpServer(
            config.http(),
            stateRepository,
            oauthFactory,
            logger,
            executor
        );
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            ledgerRepository.close();
            documentStore.close();
            executor.close();
        }));
    }
}
