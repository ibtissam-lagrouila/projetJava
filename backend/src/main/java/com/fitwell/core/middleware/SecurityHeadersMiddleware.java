package com.fitwell.core.middleware;

import com.fitwell.core.http.HttpResponse;
import com.fitwell.core.http.Middleware;
import com.fitwell.core.http.MiddlewareChain;
import com.fitwell.core.http.RequestContext;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SecurityHeadersMiddleware implements Middleware {

    @Override
    public HttpResponse apply(RequestContext context, MiddlewareChain chain) throws Exception {
        HttpResponse response = chain.proceed(context);

        // Add security headers to every response
        Map<String, String> headers = new LinkedHashMap<>(response.getHeaders());
        headers.putIfAbsent("X-Content-Type-Options", "nosniff");
        headers.putIfAbsent("X-Frame-Options", "DENY");
        headers.putIfAbsent("X-XSS-Protection", "1; mode=block");
        headers.putIfAbsent("Referrer-Policy", "strict-origin-when-cross-origin");
        headers.putIfAbsent("Content-Security-Policy", "default-src 'self'");

        return new HttpResponse(response.getStatus(), headers, response.getBody());
    }
}

