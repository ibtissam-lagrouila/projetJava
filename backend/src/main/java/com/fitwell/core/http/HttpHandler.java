package com.fitwell.core.http;

import java.io.IOException;

@FunctionalInterface
public interface HttpHandler {
    HttpResponse handle(RequestContext context) throws Exception;
}

