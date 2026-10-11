package com.loan.apigateway.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;

/**
 * @author Yusuf Olosan
 * @role software engineer
 * @createdOn 11 Sun Oct, 2026
 */

@Configuration
@ConfigurationProperties(prefix = "app")
@Data
public class AppConfiguration {

    @NestedConfigurationProperty
    private RateLimit rateLimit;
    private String publicPaths;

    /**
     * Number of trusted reverse-proxies sitting in front of this gateway.
     * Used to safely resolve the real client IP from X-Forwarded-For.
     * 0 = ignore XFF entirely and use the raw socket address.
     */
    private int trustedProxies = 0;

    @Data
    public static class RateLimit{
        private Long maxUserRequest;
        private Long maxIpRequest;
        private Long maxTime;
    }



}
