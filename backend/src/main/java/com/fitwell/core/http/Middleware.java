package com.fitwell.core.http;

@FunctionalInterface
public interface Middleware {
    HttpResponse apply(RequestContext context, MiddlewareChain chain) throws Exception;
}

