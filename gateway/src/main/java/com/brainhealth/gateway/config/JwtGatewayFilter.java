package com.brainhealth.gateway.config;

import com.brainhealth.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.*;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {
    private static final List<String> PUBLIC_PATHS = List.of(
        "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/forgot-password",
        "/api/v1/auth/reset-password");
    private final ReactiveStringRedisTemplate redis;

    public JwtGatewayFilter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (PUBLIC_PATHS.contains(path) || HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return chain.filter(removeSpoofedHeaders(exchange));
        }
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) return unauthorized(exchange, "请先登录");
        Claims claims = JwtUtil.parseToken(header.substring(7));
        if (claims == null || "refresh".equals(claims.get("type", String.class))) {
            return unauthorized(exchange, "登录状态无效或已过期");
        }
        return redis.hasKey("user:disabled:" + claims.getId()).flatMap(disabled -> {
        if (Boolean.TRUE.equals(disabled)) return unauthorized(exchange, "账号已停用");
        var request = exchange.getRequest().mutate()
            .headers(headers -> {
                headers.remove("X-User-Id");
                headers.remove("X-Username");
                headers.remove("X-Institution-Id");
                headers.remove("X-Project-Ids");
                headers.remove("X-Roles");
                headers.remove("X-Subject-Id");
            })
            .header("X-User-Id", claims.getId())
            .header("X-Username", claims.getSubject());
        Object institutionId = claims.get("institutionId");
        if (institutionId != null) request.header("X-Institution-Id", institutionId.toString());
        Object subjectId = claims.get("subjectId");
        if (subjectId != null) request.header("X-Subject-Id", subjectId.toString());
        Object projectIds = claims.get("projectIds");
        if (projectIds instanceof List<?> ids) {
            request.header("X-Project-Ids", String.join(",", ids.stream().map(Object::toString).toList()));
        }
        Object roles = claims.get("roles");
        if (path.startsWith("/api/v1/admin")
                && (!(roles instanceof List<?> values)
                    || values.stream().map(Object::toString)
                        .map(String::toUpperCase)
                        .noneMatch(role -> "ROLE_ADMIN".equals(role) || "ADMIN".equals(role)))) {
            return forbidden(exchange, "仅系统管理员可执行此操作");
        }
        if (roles instanceof List<?> values) {
            request.header("X-Roles", String.join(",", values.stream().map(Object::toString).toList()));
        }
        return chain.filter(exchange.mutate().request(request.build()).build());
        });
    }

    private ServerWebExchange removeSpoofedHeaders(ServerWebExchange exchange) {
        return exchange.mutate().request(exchange.getRequest().mutate().headers(headers -> {
            headers.remove("X-User-Id");
            headers.remove("X-Username");
            headers.remove("X-Institution-Id");
            headers.remove("X-Project-Ids");
            headers.remove("X-Roles");
            headers.remove("X-Subject-Id");
        }).build()).build();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        byte[] bytes = ("{\"code\":401,\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        byte[] bytes = ("{\"code\":403,\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    @Override public int getOrder() { return -100; }
}
