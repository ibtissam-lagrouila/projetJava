package com.fitwell.core.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

public final class HttpServerApp {

    private static final Logger log = LoggerFactory.getLogger(HttpServerApp.class);

    private final int port;
    private final Router router;
    private final Set<String> trustedProxies;
    private HttpServer server;

    public HttpServerApp(int port, Router router, Set<String> trustedProxies) {
        this.port = port;
        this.router = router;
        this.trustedProxies = trustedProxies;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        // Use Java 21 Virtual Threads for high-throughput I/O
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/", exchange -> {
            try {
                RequestContext context = new RequestContext(exchange, null, trustedProxies);
                HttpResponse response = router.dispatch(context);
                sendResponse(exchange, response);
            } catch (Throwable t) {
                log.error("Unhandled top-level error during request dispatch", t);
                sendResponse(exchange, HttpResponse.internalError("An unexpected server error occurred"));
            } finally {
                exchange.close();
            }
        });

        server.start();
        log.info("Fitwell Java 21 SE HttpServer started successfully on port {} with virtual threads executor", port);
    }

    public void stop() {
        if (server != null) {
            log.info("Stopping Fitwell HttpServer...");
            server.stop(2);
            log.info("Fitwell HttpServer stopped");
        }
    }

    public int getPort() {
        return server != null ? server.getAddress().getPort() : port;
    }

    private void sendResponse(HttpExchange exchange, HttpResponse response) throws IOException {
        for (Map.Entry<String, String> header : response.getHeaders().entrySet()) {
            exchange.getResponseHeaders().set(header.getKey(), header.getValue());
        }

        byte[] body = response.getBody();
        int statusCode = response.getStatus().getCode();

        if (statusCode == HttpStatus.NO_CONTENT.getCode() || body.length == 0) {
            exchange.sendResponseHeaders(statusCode, -1);
        } else {
            exchange.sendResponseHeaders(statusCode, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
                os.flush();
            }
        }
    }
}

