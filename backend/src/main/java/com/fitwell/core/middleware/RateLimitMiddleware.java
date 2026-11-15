package com.fitwell.core.middleware;

import com.fitwell.core.http.HttpResponse;
import com.fitwell.core.http.HttpStatus;
import com.fitwell.core.http.Middleware;
import com.fitwell.core.http.MiddlewareChain;
import com.fitwell.core.http.RequestContext;
import com.fitwell.dao.UserDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RateLimitMiddleware implements Middleware {

    private static final Logger log = LoggerFactory.getLogger(RateLimitMiddleware.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int WINDOW_MINUTES = 15;

    private final UserDao userDao;

    public RateLimitMiddleware(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public HttpResponse apply(RequestContext context, MiddlewareChain chain) throws Exception {
        String clientIp = context.getClientIp();

        // Check recent failed attempts from this IP
        int failedAttempts = userDao.countRecentFailedAttempts(null, clientIp, WINDOW_MINUTES);
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            log.warn("Rate limit exceeded for IP {} ({} failed attempts in {} minutes)", clientIp, failedAttempts, WINDOW_MINUTES);
            return HttpResponse.error(HttpStatus.TOO_MANY_REQUESTS, "Too many failed login attempts. Please try again in 15 minutes.");
        }

        return chain.proceed(context);
    }
}

