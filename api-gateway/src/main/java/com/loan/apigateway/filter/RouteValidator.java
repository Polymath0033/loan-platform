package com.loan.apigateway.filter;


import com.loan.apigateway.config.AppConfiguration;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author Yusuf Olosan
 * @role software engineer
 * @createdOn 11 Sun Oct, 2026
 */

@Component
@RequiredArgsConstructor
public class RouteValidator {

    private final AppConfiguration appConfiguration;

    private List<String> publicPaths;

    @PostConstruct
    public void init() {
        publicPaths = List.of(Arrays.stream(appConfiguration.getPublicPaths().trim().split(",")).map(PathPatternParser.defaultInstance::parse).toArray(String[]::new));
    }

    public Predicate<ServerHttpRequest> isSecured() {

        return request -> publicPaths.stream()
                .noneMatch(uri -> request.getURI().getPath().contains(uri.trim()));
    }
}
