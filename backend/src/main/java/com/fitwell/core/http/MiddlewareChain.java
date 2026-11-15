package com.fitwell.core.http;

import java.util.List;

public final class MiddlewareChain {

    private final List<Middleware> middlewares;
    private final HttpHandler finalHandler;
    private int index = 0;

    public MiddlewareChain(List<Middleware> middlewares, HttpHandler finalHandler) {
        this.middlewares = middlewares;
        this.finalHandler = finalHandler;
    }

    public HttpResponse proceed(RequestContext context) throws Exception {
        if (index < middlewares.size()) {
            Middleware current = middlewares.get(index++);
            return current.apply(context, this);
        }
        return finalHandler.handle(context);
    }
}

