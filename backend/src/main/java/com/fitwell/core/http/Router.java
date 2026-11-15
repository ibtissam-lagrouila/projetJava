package com.fitwell.core.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Router {

    private static final Logger log = LoggerFactory.getLogger(Router.class);

    private final List<RouteEntry> routes = new ArrayList<>();
    private final List<Middleware> globalMiddlewares = new ArrayList<>();

    public Router use(Middleware middleware) {
        if (middleware != null) {
            globalMiddlewares.add(middleware);
        }
        return this;
    }

    public Router route(HttpMethod method, String pathPattern, HttpHandler handler, Middleware... routeMiddlewares) {
        routes.add(new RouteEntry(method, pathPattern, handler, Arrays.asList(routeMiddlewares)));
        return this;
    }

    public Router get(String pathPattern, HttpHandler handler, Middleware... middlewares) {
        return route(HttpMethod.GET, pathPattern, handler, middlewares);
    }

    public Router post(String pathPattern, HttpHandler handler, Middleware... middlewares) {
        return route(HttpMethod.POST, pathPattern, handler, middlewares);
    }

    public Router put(String pathPattern, HttpHandler handler, Middleware... middlewares) {
        return route(HttpMethod.PUT, pathPattern, handler, middlewares);
    }

    public Router delete(String pathPattern, HttpHandler handler, Middleware... middlewares) {
        return route(HttpMethod.DELETE, pathPattern, handler, middlewares);
    }

    public Router patch(String pathPattern, HttpHandler handler, Middleware... middlewares) {
        return route(HttpMethod.PATCH, pathPattern, handler, middlewares);
    }

    public HttpResponse dispatch(RequestContext context) throws Exception {
        HttpMethod method = context.getMethod();
        String path = context.getPath();

        // Automatic OPTIONS pre-flight handling
        if (method == HttpMethod.OPTIONS) {
            return HttpResponse.builder()
                    .status(HttpStatus.NO_CONTENT)
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With")
                    .header("Access-Control-Allow-Credentials", "true")
                    .build();
        }

        boolean pathMatched = false;
        for (RouteEntry entry : routes) {
            Map<String, String> extractedParams = entry.matchPath(path);
            if (extractedParams != null) {
                pathMatched = true;
                if (entry.method == method) {
                    for (Map.Entry<String, String> p : extractedParams.entrySet()) {
                        context.setPathParam(p.getKey(), p.getValue());
                    }

                    List<Middleware> fullChain = new ArrayList<>(globalMiddlewares);
                    fullChain.addAll(entry.routeMiddlewares);

                    MiddlewareChain chain = new MiddlewareChain(fullChain, entry.handler);
                    return chain.proceed(context);
                }
            }
        }

        if (pathMatched) {
            return HttpResponse.error(HttpStatus.METHOD_NOT_ALLOWED, "Method " + method + " not allowed on " + path);
        }

        return HttpResponse.notFound("No handler found for " + method + " " + path);
    }

    public static final class RouteEntry {
        private final HttpMethod method;
        private final String rawPattern;
        private final Pattern compiledRegex;
        private final List<String> paramNames;
        private final HttpHandler handler;
        private final List<Middleware> routeMiddlewares;

        public RouteEntry(HttpMethod method, String rawPattern, HttpHandler handler, List<Middleware> routeMiddlewares) {
            this.method = method;
            this.rawPattern = rawPattern;
            this.handler = handler;
            this.routeMiddlewares = routeMiddlewares != null ? routeMiddlewares : Collections.emptyList();

            // Convert /api/offers/{id} to regex ^/api/offers/([^/]+)$
            List<String> names = new ArrayList<>();
            StringBuilder regex = new StringBuilder("^");
            String[] segments = rawPattern.split("/");
            for (int i = 0; i < segments.length; i++) {
                String segment = segments[i];
                if (segment.isEmpty()) continue;
                regex.append("/");
                if (segment.startsWith("{") && segment.endsWith("}")) {
                    String paramName = segment.substring(1, segment.length() - 1);
                    names.add(paramName);
                    regex.append("([^/]+)");
                } else {
                    regex.append(Pattern.quote(segment));
                }
            }
            if (rawPattern.endsWith("/")) {
                regex.append("/?");
            }
            regex.append("$");
            this.paramNames = names;
            this.compiledRegex = Pattern.compile(regex.toString());
        }

        public Map<String, String> matchPath(String path) {
            Matcher matcher = compiledRegex.matcher(path);
            if (!matcher.matches()) {
                return null;
            }
            Map<String, String> params = new LinkedHashMap<>();
            for (int i = 0; i < paramNames.size(); i++) {
                params.put(paramNames.get(i), matcher.group(i + 1));
            }
            return params;
        }

        public HttpMethod getMethod() {
            return method;
        }

        public String getRawPattern() {
            return rawPattern;
        }
    }
}

