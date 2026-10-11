package com.loan.apigateway.config;


import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;

/**
 * @author Yusuf Olosan
 * @role software engineer
 * @createdOn 11 Sun Oct, 2026
 */

@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private final ObjectMapper mapper;
    private final AppConfiguration appConfiguration;

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        GenericJacksonJsonRedisSerializer jsonRedisSerializer = new GenericJacksonJsonRedisSerializer(mapper);
        template.setValueSerializer(jsonRedisSerializer);
        template.setHashValueSerializer(jsonRedisSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(redisConnectionFactory);
        return template;
    }

    /**
     * Non-blocking Redis template for use on the Netty event loop (WebFlux / Spring Cloud Gateway).
     * The blocking StringRedisTemplate must NOT be used inside GatewayFilter lambdas.
     */
    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(ReactiveRedisConnectionFactory factory) {
        return new ReactiveStringRedisTemplate(factory);
    }

    /**
     * Resolves the real client IP address in a way that cannot be spoofed by an attacker.
     */
    @Bean
    public RemoteAddressResolver remoteAddressResolver() {
        int trusted = appConfiguration.getTrustedProxies();
        return trusted > 0
                ? XForwardedRemoteAddressResolver.maxTrustedIndex(trusted)
                : new RemoteAddressResolver() {};
    }
}
