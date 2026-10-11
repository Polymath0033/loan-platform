package com.loan.apigateway.filter;


import com.loan.apigateway.config.AppConfiguration;
import com.loan.apigateway.utils.JWTService;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * @author Yusuf Olosan
 * @role software engineer
 * @createdOn 11 Sun Oct, 2026
 */

@Component
@Slf4j
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private final JWTService jwtService;
    private final AppConfiguration appConfiguration;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final RouteValidator validator;
    private final RemoteAddressResolver remoteAddressResolver;

    /**
     * Three outcomes a rate-limit check can produce.
     */
    private enum Limit {ALLOWED, LIMITED, UNAVAILABLE}

    public AuthenticationFilter(
            JWTService jwtService,
            AppConfiguration appConfiguration,
            ReactiveStringRedisTemplate redisTemplate,
            RouteValidator validator,
            RemoteAddressResolver remoteAddressResolver) {
        super(Config.class);
        this.jwtService = jwtService;
        this.appConfiguration = appConfiguration;
        this.redisTemplate = redisTemplate;
        this.validator = validator;
        this.remoteAddressResolver = remoteAddressResolver;
    }

    public static class Config {
        // Put the configuration properties for your filter here
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {

            // Strip any client-supplied identity headers before anything else.
            ServerHttpRequest sanitisedRequest = exchange.getRequest().mutate()
                    .headers(h -> {
                        h.remove("X-User-Name");
                        h.remove("X-User-Role");
                    }).build();

            ServerWebExchange sanitisedExchange = exchange.mutate().request(sanitisedRequest).build();
            ServerHttpRequest request = sanitisedExchange.getRequest();

            if (!validator.isSecured().test(request)) {
                return chain.filter(sanitisedExchange);
            }

            log.debug("Request to secured endpoint: {}", request.getURI().getPath());

            // Resolve client IP safely via the configured RemoteAddressResolver.
            // getHostAddress() returns the numeric IP — getHostString() can return a hostname.
            InetSocketAddress resolved = remoteAddressResolver.resolve(sanitisedExchange);
            String clientIp = (resolved != null) ? resolved.getAddress().getHostAddress() : "unknown";
            log.debug("Incoming request from IP: {}", clientIp);

            // ── IP-level rate limit ──────────────────────────────────────────────────────
            // Runs before JWT validation so unauthenticated floods are stopped here.
            return checkRateLimit("rl:ip:" + clientIp, appConfiguration.getRateLimit().getMaxIpRequest())
                    .flatMap(ipLimit -> {
                        if (ipLimit == Limit.LIMITED) {
                            return rateLimitedResponse(sanitisedExchange,
                                    "Too many requests from your IP address");
                        }
                        if (ipLimit == Limit.UNAVAILABLE) {
                            return unavailableResponse(sanitisedExchange,
                                    "Rate limiter temporarily unavailable");
                        }

                        // ── Authorization header ─────────────────────────────────────────
                        if (!request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
                            return unauthorisedResponse(sanitisedExchange,
                                    "Missing Authorization header");
                        }

                        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
                        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                            return unauthorisedResponse(sanitisedExchange,
                                    "Invalid Authorization header format");
                        }

                        String token = authHeader.substring(7);

                        // ── JWT validation ───────────────────────────────────────────────
                        // Malformed / invalid tokens produce a JwtException; catch it here
                        // so it becomes a 401, not an unhandled 500.
                        boolean valid;
                        String username;
                        String role;
                        try {
                            valid = jwtService.validateToken(token);
                            username = jwtService.extractSubject(token);
                            role = jwtService.extractRole(token);
                        } catch (JwtException | IllegalArgumentException e) {
                            log.debug("Token validation error: {}", e.getMessage());
                            return unauthorisedResponse(sanitisedExchange, "Invalid or malformed token");
                        }

                        if (!valid) {
                            return unauthorisedResponse(sanitisedExchange, "Invalid or expired token");
                        }

                        // ── Per-user rate limit ──────────────────────────────────────────
                        return checkRateLimit("rl:user:" + username, appConfiguration.getRateLimit().getMaxUserRequest())
                                .flatMap(userLimit -> {
                                    if (userLimit == Limit.LIMITED) {
                                        return rateLimitedResponse(sanitisedExchange,
                                                "Rate limit exceeded");
                                    }
                                    if (userLimit == Limit.UNAVAILABLE) {
                                        return unavailableResponse(sanitisedExchange,
                                                "Rate limiter temporarily unavailable");
                                    }

                                    // ── Forward with identity headers ────────────────────
                                    final String finalUsername = username;
                                    final String finalRole = role;
                                    ServerWebExchange modifiedExchange = sanitisedExchange.mutate()
                                            .request(b -> {
                                                b.header("X-User-Name", finalUsername);
                                                if (finalRole != null) {
                                                    b.header("X-User-Role", finalRole);
                                                }
                                            })
                                            .build();

                                    return chain.filter(modifiedExchange);
                                });
                    });
        };
    }

    // ── Rate-limit helper ────────────────────────────────────────────────────────────────

    /**
     * Atomically increments the counter for {@code key} and sets the TTL on the first
     * increment (count == 1). If the TTL is somehow missing on subsequent increments
     * (crash between INCR and EXPIRE on a previous request) it is repaired immediately.
     */
    private Mono<Limit> checkRateLimit(String key, long max) {
        long windowSeconds = appConfiguration.getRateLimit().getMaxTime();

        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(count -> {
                    if (count > max) {
                        return Mono.just(Limit.LIMITED);
                    }
                    if (count == 1) {
                        // First request in the window — set the expiry.
                        return redisTemplate.expire(key, Duration.ofSeconds(windowSeconds))
                                .thenReturn(Limit.ALLOWED);
                    }
                    // For subsequent requests, check that the TTL is still set.
                    // If it is -1 (no expiry) the EXPIRE call was lost previously; repair it now.
                    return redisTemplate.getExpire(key)
                            .flatMap(ttl -> {
                                if (ttl.isNegative()) {
                                    return redisTemplate.expire(key, Duration.ofSeconds(windowSeconds))
                                            .thenReturn(Limit.ALLOWED);
                                }
                                return Mono.just(Limit.ALLOWED);
                            });
                })
                .onErrorResume(e -> {
                    log.error("Rate limiter error for key {}: {}", key, e.toString());
                    return Mono.just(Limit.UNAVAILABLE);
                });
    }

    // ── Response helpers ─────────────────────────────────────────────────────────────────

    /**
     * 401 with {@code WWW-Authenticate: Bearer} header.
     */
    private Mono<Void> unauthorisedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
        response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return writeJson(response, message);
    }

    /**
     * 429 with {@code Retry-After} header set to the configured rate-limit window.
     */
    private Mono<Void> rateLimitedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
        response.getHeaders().set(HttpHeaders.RETRY_AFTER,
                String.valueOf(appConfiguration.getRateLimit().getMaxTime()));
        return writeJson(response, message);
    }

    /**
     * 503 with {@code Retry-After} to signal a temporary outage rather than a rate limit.
     */
    private Mono<Void> unavailableResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, "10");
        return writeJson(response, message);
    }

    private Mono<Void> writeJson(ServerHttpResponse response, String message) {
        // Escape any quotes in the message to avoid breaking the JSON string.
        String safe = message.replace("\\", "\\\\").replace("\"", "\\\"");
        String body = "{\"error\":\"" + safe + "\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
