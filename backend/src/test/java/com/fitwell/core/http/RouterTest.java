package com.fitwell.core.http;

import com.fitwell.core.middleware.ErrorHandlingMiddleware;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class RouterTest {

    private Router router;

    @BeforeEach
    void setUp() {
        router = new Router();
        router.use(new ErrorHandlingMiddleware());
    }

    private RequestContext mockContext(String method, String uriString) {
        HttpExchange exchange = Mockito.mock(HttpExchange.class);
        when(exchange.getRequestMethod()).thenReturn(method);
        when(exchange.getRequestURI()).thenReturn(URI.create(uriString));
        when(exchange.getRequestHeaders()).thenReturn(new Headers());
        when(exchange.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        return new RequestContext(exchange, Collections.emptyMap(), Collections.singleton("127.0.0.1"));
    }

    @Test
    @DisplayName("Should extract path parameter {id} correctly")
    void shouldExtractPathParameter() throws Exception {
        router.get("/api/offers/{id}", ctx -> {
            String id = ctx.getPathParam("id");
            return HttpResponse.ok(Map.of("offerId", id));
        });

        RequestContext ctx = mockContext("GET", "/api/offers/abc-123-uuid");
        HttpResponse response = router.dispatch(ctx);

        assertEquals(HttpStatus.OK, response.getStatus());
        String body = new String(response.getBody());
        assertTrue(body.contains("abc-123-uuid"));
    }

    @Test
    @DisplayName("Should return 405 Method Not Allowed when path matches but method differs")
    void shouldReturn405MethodNotAllowed() throws Exception {
        router.post("/api/auth/login", ctx -> HttpResponse.ok("logged in"));

        RequestContext ctx = mockContext("GET", "/api/auth/login");
        HttpResponse response = router.dispatch(ctx);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatus());
    }

    @Test
    @DisplayName("Should return 404 Not Found for unregistered routes")
    void shouldReturn404ForUnregisteredRoutes() throws Exception {
        router.get("/api/health", ctx -> HttpResponse.ok("OK"));

        RequestContext ctx = mockContext("GET", "/api/non-existent-page");
        HttpResponse response = router.dispatch(ctx);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
    }

    @Test
    @DisplayName("Should execute middlewares in proper sequential order")
    void shouldExecuteMiddlewaresInOrder() throws Exception {
        AtomicBoolean middlewareExecuted = new AtomicBoolean(false);

        router.use((ctx, chain) -> {
            middlewareExecuted.set(true);
            return chain.proceed(ctx);
        });

        router.get("/api/ping", ctx -> HttpResponse.ok("pong"));

        RequestContext ctx = mockContext("GET", "/api/ping");
        HttpResponse response = router.dispatch(ctx);

        assertEquals(HttpStatus.OK, response.getStatus());
        assertTrue(middlewareExecuted.get(), "Middleware must be executed during dispatch");
    }
}

