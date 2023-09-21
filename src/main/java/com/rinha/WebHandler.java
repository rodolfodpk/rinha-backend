package com.rinha;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;

import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

class WebHandler {

    public final static Logger logger = LoggerFactory.getLogger(WebHandler.class);
    public static final int MAX_APELIDO = 32;
    public static final int MAX_NAME = 100;
    public static final int MAX_STACK = 32;

    private final Random random = new Random();
    private final SqlClient sqlClient;

    WebHandler(HttpServer httpServer, SqlClient sqlClient) {
        this.sqlClient = sqlClient;
        httpServer.requestHandler(httpRequestHandler());
    }

    private Handler<HttpServerRequest> httpRequestHandler() {
        return req -> {
            String method = req.method().name();
            String uri = req.uri();
            // logger.info("Method=" + method + " Uri=" + uri);
            if ("GET".equals(method)) {
                if (uri.startsWith("/pessoas?t=") || uri.startsWith("/pessoas/?t=")) {
                    handleGetByTerm(req);
                } else if (uri.startsWith("/pessoas/")) {
                    handleGetById(req, uri);
                } else if (uri.startsWith("/contagem-pessoas")) {
                    handleGetCount(req);
                } else {
                    req.response().setStatusCode(404).end("Not found");
                }
            } else if (method.equals("POST")) {
                handlePost(req);
            } else {
                req.response().setStatusCode(404).end("Not found");
            }
        };
    }

    private void handleGetByTerm(HttpServerRequest req) {
        String termo = req.getParam("t");
        if (termo == null) {
            req.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(400)
                    .end("Bad request: termo Is required");
            return;
        }
        sqlClient
                .preparedQuery("SELECT json_agg(" +
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
    }

    private void handlePost(HttpServerRequest req) {
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
            UUID id = new UUID(random.nextLong(), random.nextLong());
            Tuple tuple = Tuple.of(id, nascimento, apelido, nome, "ARRAY " + Arrays.toString(stack.stream().toArray()));
            sqlClient.preparedQuery("INSERT INTO pessoas (id, nascimento, apelido, nome, stack)" +
                            " VALUES ($1, $2, $3, $4, $5)")
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
    }

    private void handleGetById(HttpServerRequest req, String uri) {
        String id = getLastPathSegment(uri);
        sqlClient
                .preparedQuery("SELECT json_agg(" +
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
    }

    private static String getLastPathSegment(String url) {
        int lastSlashIndex = url.lastIndexOf('/');
        if (lastSlashIndex != -1 && lastSlashIndex < url.length() - 1) {
            // The last segment is the substring after the last '/'
            return url.substring(lastSlashIndex + 1);
        }
        return null; // Return null if the URL is invalid or doesn't contain a path.
    }

    private void handleGetCount(HttpServerRequest req) {
        sqlClient
                .preparedQuery("SELECT count(*) as count FROM pessoas")
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
    }

}
