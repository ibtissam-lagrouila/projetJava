package com.fitwell.core.http;

@FunctionalInterface
public interface HttpHandler {
    HttpResponse handle(RequestContext context) throws Exception;
}

