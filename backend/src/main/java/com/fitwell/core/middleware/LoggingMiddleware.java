package com.fitwell.core.middleware;

import com.fitwell.core.http.HttpResponse;
import com.fitwell.core.http.Middleware;
import com.fitwell.core.http.MiddlewareChain;
import com.fitwell.core.http.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoggingMiddleware implements Middleware {

    private static final Logger log = LoggerFactory.getLogger(LoggingMiddleware.class);

    @Override
    public HttpResponse apply(RequestContext context, MiddlewareChain chain) throws Exception {
        long start = System.currentTimeMillis();
        String method = context.getMethod().name();
        String path = context.getPath();
        String clientIp = context.getClientIp();

        try {
            HttpResponse response = chain.proceed(context);
            long elapsed = System.currentTimeMillis() - start;
            log.info("{} {} from {} -> {} ({} ms)", method, path, clientIp, response.getStatus().getCode(), elapsed);
            return response;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("{} {} from {} -> FAILED with exception ({} ms): {}", method, path, clientIp, elapsed, e.getMessage());
            throw e;
        }
    }
}

