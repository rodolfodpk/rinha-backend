package com.rinha;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlClient;

public class MainVerticle extends AbstractVerticle {

    public final static Logger logger = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start(Promise<Void> startPromise) {

        logger.info("Starting...");
        logger.info("Is native transportEnabled " + vertx.isNativeTransportEnabled());

        try {
            final var httpServer = httpServer();
            new WebHandler(httpServer, sqlClient());

            // Now bind the httpServer:
            httpServer.listen(result -> {
                if (result.succeeded()) {
                    startPromise.complete();
                    logger.info("Ready, let's go!");
                } else {
                    startPromise.fail(result.cause());
                    logger.error("Sorry", result.cause());
                }
            });

        } catch (Exception e) {
            logger.error("Sorry", e);
        }
    }

    private HttpServer httpServer() {
        // https://vertx.io/docs/vertx-core/java/#_configuring_an_http2_server
        var httpServerOptions = new HttpServerOptions()
                .setPort(Integer.parseInt(System.getenv("HTTP_PORT"))) // Set the desired port
                .setHost(System.getenv("HTTP_HOST")) // Set the desired host
                .setUseAlpn("true".equals(System.getenv("HTTP_USE_ALPN"))) // Disable ALPN (Application-Layer Protocol Negotiation)
                .setSsl("true".equals(System.getenv("HTTP_SSL"))); // Disable SSL/TLS
        return vertx.createHttpServer(httpServerOptions);
    }

    private SqlClient sqlClient() {
        PoolOptions poolOptions = new PoolOptions()
                .setPoolCleanerPeriod(-1)
                .setMaxSize(5);
        // using https://vertx.io/docs/vertx-pg-client/java/#_environment_variables
        return PgPool.client(vertx, poolOptions);
    }

}
