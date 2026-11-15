package com.fitwell.core.middleware;

import com.fitwell.core.http.HttpResponse;
import com.fitwell.core.http.Middleware;
import com.fitwell.core.http.MiddlewareChain;
import com.fitwell.core.http.RequestContext;
import com.fitwell.core.security.AuthContext;
import com.fitwell.core.security.TokenGenerator;
import com.fitwell.dao.LabMemberDao;
import com.fitwell.dao.SessionDao;
import com.fitwell.dao.UserDao;
import com.fitwell.model.entity.LabMember;
import com.fitwell.model.entity.Session;
import com.fitwell.model.entity.User;
import com.fitwell.model.enums.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public final class AuthMiddleware implements Middleware {

    public static final String AUTH_ATTRIBUTE = "auth";
    public static final String SESSION_COOKIE_NAME = "fitwell_session";
    private static final Logger log = LoggerFactory.getLogger(AuthMiddleware.class);

    private final SessionDao sessionDao;
    private final UserDao userDao;
    private final LabMemberDao labMemberDao;

    public AuthMiddleware(SessionDao sessionDao, UserDao userDao, LabMemberDao labMemberDao) {
        this.sessionDao = sessionDao;
        this.userDao = userDao;
        this.labMemberDao = labMemberDao;
    }

    @Override
    public HttpResponse apply(RequestContext context, MiddlewareChain chain) throws Exception {
        String token = extractToken(context);
        if (token != null && !token.isBlank()) {
            String tokenHash = TokenGenerator.hashToken(token);
            Optional<Session> sessionOpt = sessionDao.findByTokenHash(tokenHash);

            if (sessionOpt.isPresent()) {
                Session session = sessionOpt.get();
                Optional<User> userOpt = userDao.findById(session.userId());

                if (userOpt.isPresent() && userOpt.get().isActive()) {
                    User user = userOpt.get();
                    LabMember labMember = null;
                    if (user.role() == Role.LAB) {
                        labMember = labMemberDao.findByUserId(user.id()).orElse(null);
                    }

                    AuthContext authContext = new AuthContext(
                            user,
                            labMember != null ? labMember.labId() : null,
                            labMember != null ? labMember.role() : null
                    );
                    context.setAttribute(AUTH_ATTRIBUTE, authContext);
                }
            }
        }

        return chain.proceed(context);
    }

    private String extractToken(RequestContext context) {
        // 1. Check HttpOnly cookie first (recommended web flow)
        String cookieToken = context.getCookie(SESSION_COOKIE_NAME);
        if (cookieToken != null && !cookieToken.isBlank()) {
            return cookieToken;
        }

        // 2. Check Authorization: Bearer <token> header (API / test flow)
        String authHeader = context.getHeader("Authorization");
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authHeader.substring(7).trim();
        }

        return null;
    }
}

