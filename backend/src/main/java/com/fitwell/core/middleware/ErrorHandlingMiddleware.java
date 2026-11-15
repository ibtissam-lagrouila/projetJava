package com.fitwell.core.middleware;

import com.fitwell.core.http.*;
import com.fitwell.model.dto.AuthDtos.ValidationErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ErrorHandlingMiddleware implements Middleware {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandlingMiddleware.class);

    @Override
    public HttpResponse apply(RequestContext context, MiddlewareChain chain) {
        try {
            return chain.proceed(context);
        } catch (ValidationException e) {
            log.warn("Validation failure on {} {}: {}", context.getMethod(), context.getPath(), e.getMessage());
            return HttpResponse.json(HttpStatus.BAD_REQUEST, new ValidationErrorResponse(e.getMessage(), e.getErrors()));
        } catch (IllegalArgumentException e) {
            log.warn("Bad request on {} {}: {}", context.getMethod(), context.getPath(), e.getMessage());
            return HttpResponse.badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("Illegal state on {} {}: {}", context.getMethod(), context.getPath(), e.getMessage());
            return HttpResponse.error(HttpStatus.CONFLICT, e.getMessage());
        } catch (SecurityException e) {
            log.warn("Security rejection on {} {}: {}", context.getMethod(), context.getPath(), e.getMessage());
            return HttpResponse.forbidden(e.getMessage());
        } catch (Throwable t) {
            log.error("Unhandled exception processing {} {}", context.getMethod(), context.getPath(), t);
            return HttpResponse.internalError("An internal server error occurred");
        }
    }
}
