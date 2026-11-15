package com.fitwell.core.middleware;

import com.fitwell.core.http.HttpResponse;
import com.fitwell.core.http.Middleware;
import com.fitwell.core.http.MiddlewareChain;
import com.fitwell.core.http.RequestContext;
import com.fitwell.core.security.AuthContext;
import com.fitwell.model.enums.Role;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public final class RoleGuardMiddleware implements Middleware {

    private final Set<Role> allowedRoles;

    public RoleGuardMiddleware(Role... allowedRoles) {
        if (allowedRoles == null || allowedRoles.length == 0) {
            this.allowedRoles = EnumSet.allOf(Role.class);
        } else {
            this.allowedRoles = EnumSet.copyOf(Arrays.asList(allowedRoles));
        }
    }

    @Override
    public HttpResponse apply(RequestContext context, MiddlewareChain chain) throws Exception {
        AuthContext auth = context.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        if (auth == null || auth.user() == null) {
            return HttpResponse.unauthorized("Authentication required");
        }

        if (!allowedRoles.contains(auth.getRole())) {
            return HttpResponse.forbidden("Access denied: role " + auth.getRole() + " is not authorized for this resource");
        }

        return chain.proceed(context);
    }
}

