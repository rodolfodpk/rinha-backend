package com.rinha;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;

import java.util.Arrays;
import java.util.UUID;

import static com.rinha.MainVerticle.RequestValidationRules.*;

public class MainVerticle extends AbstractVerticle {

  private final Logger logger = LoggerFactory.getLogger(MainVerticle.class);
  private HttpServer server;
  private SqlClient sqlClient;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {

    logger.info("Starting...");

    logger.info("Is native transportEnabled " + vertx.isNativeTransportEnabled());

    server = vertx.createHttpServer(httpServerOptions());

    addRequestHandlers();

    sqlClient = sqlClient();

    // Now bind the server:
    server.listen(8080, res -> {
      if (res.succeeded()) {
        startPromise.complete();
        logger.info("Ready, let's go...");
      } else {
        startPromise.fail(res.cause());
        logger.error("Sorry", res.cause());
      }
    });
  }

  private void addRequestHandlers() {

    server.requestHandler(req -> {
        String method = req.method().name();
        String path = req.path();
        String uri = req.uri();
        String query = req.query();
        logger.info("Method="+ method + " Path=" + path + " Uri=" + uri + " Query=" + query);
        if ("GET".equals(method)) {
          if (uri.startsWith("/pessoas?t=") || uri.startsWith("/pessoas/?t=")) {
            String termo = req.getParam("t");
            logger.info("Termo = " + termo);
            if (termo == null) {
              req.response()
                .putHeader("content-type", "application/json")
                .setStatusCode(400)
                .end("Bad request: termo Is required");
              return;
            }
            sqlClient
              .preparedQuery("select json_agg(" +
                "json_build_object('id', id, " +
                "                  'nascimento', nascimento, " +
                "                  'apelido', apelido, " +
                "                  'nome', nome, " +
                "                  'stack', stack)) " +
                " FROM pessoas " +
                " WHERE BUSCA_TRGM like '%' || $1 || '%'" +
                " FETCH FIRST 50 ROWS ONLY")
              .execute(Tuple.of(termo))
              .onSuccess(rowSet -> {
                Row row = rowSet.iterator().next();
                JsonArray jsonArray = row.getJsonArray(0);
                if (jsonArray == null || jsonArray.isEmpty()) {
                  req.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(200)
                    .end("[]");
                } else {
                  req.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(200)
                    .end(jsonArray.toBuffer()); // TODO remove busca field
                }
              }).onFailure(e -> {
                logger.error("Sorry", e);
                req.response()
                  .putHeader("content-type", "application/json")
                  .setStatusCode(500);
              });
          } else if (path.startsWith("/pessoas/")) {
            String id = getLastPathSegment(path);
            logger.info("/pessoas/" + id);
            sqlClient
              .preparedQuery("select json_agg(" +
                "json_build_object('id', id, " +
                "                  'nascimento', nascimento, " +
                "                  'apelido', apelido, " +
                "                  'nome', nome, " +
                "                  'stack', stack)) " +
                " FROM pessoas " +
                " WHERE id = $1")
              .execute(Tuple.of(UUID.fromString(id)))
              .onSuccess(rowSet -> {
                Row row = rowSet.iterator().next();
                JsonArray jsonArray = row.getJsonArray(0);
                logger.info(jsonArray);
                if (jsonArray.isEmpty()) {
                  req.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(404)
                    .end();
                } else {
                  req.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(200)
                    .end(jsonArray.getJsonObject(0).toBuffer());
                }
              })
              .onFailure(e -> {
                logger.error("Sorry", e);
                req.response()
                  .putHeader("content-type", "application/json")
                  .setStatusCode(500);
              });
          } else if (path.startsWith("/contagem-pessoas")) {
            sqlClient
              .preparedQuery("select count(*) as count from pessoas")
              .execute()
              .onSuccess(rowSet -> {
                Row row = rowSet.iterator().next();
                req.response().end(row.getLong("count").toString());
              })
              .onFailure(e -> {
                logger.error("Sorry", e);
                req.response()
                  .putHeader("content-type", "application/json")
                  .setStatusCode(500);
              });
          } else {
            logger.error("Sorry");
            req.response()
              .setStatusCode(404)
              .end("Not found");
          }
        } else if (method.equals("POST")) {
          req.bodyHandler(bodyBuffer -> {
            JsonObject pessoa = bodyBuffer.toJsonObject();
            // validate it
            String nascimento = pessoa.getString("nascimento", null);
            String apelido = pessoa.getString("apelido", null);
            String nome = pessoa.getString("nome", null);
            JsonArray stack = pessoa.getJsonArray("stack", null);
            if (apelido == null || apelido.isBlank() || apelido.length() > MAX_APELIDO
              || nome == null || nome.isBlank() || nome.length() > MAX_NAME
              || stack == null || stack.stream().anyMatch(s -> s.toString().length() > MAX_STACK)
            ) {
              req.response().setStatusCode(400).end("Bad Request");
              return;
            }
            // now let's insert
            UUID id = UUID.randomUUID();
            Tuple tuple = Tuple.of(id, nascimento, apelido, nome, "ARRAY " + Arrays.toString(stack.stream().toArray()));
            logger.info(tuple);
            sqlClient.preparedQuery("insert into pessoas (id, nascimento, apelido, nome, stack)" +
                " values ($1, $2, $3, $4, $5)")
              .execute(tuple)
              .onSuccess(r -> req.response()
                .putHeader("content-type", "application/json")
                .putHeader("Location", "/pessoas/" + id)
                .end())
              .onFailure(e -> {
                logger.error("Sorry", e);
                req.response().setStatusCode(500).end("Sorry: " + e);
              });
          });
        } else {
          req.response()
            .putHeader("content-type", "application/json")
            .end(new JsonObject().put("path", path).put("method", method).toBuffer());
        }
      }

    );
  }

  /**
   * <a href="https://vertx.io/docs/vertx-core/java/#_configuring_an_http2_server">configuring_an_http2_server</a>
   *
   * @return HttpServerOptions
   */
  HttpServerOptions httpServerOptions() {
    // Configure HTTP server options for h2c
    return new HttpServerOptions()
      .setPort(8080) // Set the desired port
      .setHost("localhost") // Set the desired host
      .setUseAlpn(false) // Disable ALPN (Application-Layer Protocol Negotiation)
      .setSsl(false); // Disable SSL/TLS
  }

  PgConnectOptions pgConnectOptions() {
    return new PgConnectOptions()
      .setPort(5432)
      .setHost("localhost")
      .setDatabase("rinhadb")
      .setUser("rinha")
      .setPassword("rinha123");
  }

  SqlClient sqlClient() {
    PoolOptions poolOptions = new PoolOptions().setMaxSize(5);
    return PgPool.client(vertx, pgConnectOptions(), poolOptions);
  }

  static class RequestValidationRules {
    public static final int MAX_APELIDO = 32;
    public static final int MAX_NAME = 100;
    public static final int MAX_STACK = 32;
  }

  private static String getLastPathSegment(String url) {
    int lastSlashIndex = url.lastIndexOf('/');
    if (lastSlashIndex != -1 && lastSlashIndex < url.length() - 1) {
      // The last segment is the substring after the last '/'
      return url.substring(lastSlashIndex + 1);
    }
    return null; // Return null if the URL is invalid or doesn't contain a path.
  }

}
