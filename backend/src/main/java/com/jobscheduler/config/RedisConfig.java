package com.jobscheduler.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SslOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.StaticScriptSource;

import java.time.Duration;

/**
 * Manually configures a Lettuce TLS connection to Upstash Redis.
 *
 * Why manual instead of Spring Boot auto-config?
 * Upstash requires SSL (port 6379 with TLS). Spring Boot's auto-config
 * only enables SSL when spring.data.redis.ssl.enabled=true AND the URL
 * scheme is rediss://, but Lettuce's URL parsing for rediss:// can be
 * unreliable across versions. Using a manual LettuceConnectionFactory
 * with .useSsl() is the most reliable approach.
 *
 * Properties used (application.properties):
 *   upstash.redis.host     — e.g. lenient-mammal-128557.upstash.io
 *   upstash.redis.port     — 6379
 *   upstash.redis.password — the Upstash Redis password
 */
@Configuration
public class RedisConfig {

    @Value("${upstash.redis.host}")
    private String host;

    @Value("${upstash.redis.port:6379}")
    private int port;

    @Value("${upstash.redis.password}")
    private String password;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        // Server config — host, port, password, DB 0
        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration();
        serverConfig.setHostName(host);
        serverConfig.setPort(port);
        serverConfig.setPassword(RedisPassword.of(password));
        serverConfig.setDatabase(0);

        // Client config — force TLS, 5 s command timeout
        SslOptions sslOptions = SslOptions.builder()
                .jdkSslProvider()
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .sslOptions(sslOptions)
                .build();

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .useSsl()                               // enforce TLS
                .and()
                .commandTimeout(Duration.ofSeconds(5))
                .clientOptions(clientOptions)
                .build();

        LettuceConnectionFactory factory =
                new LettuceConnectionFactory(serverConfig, clientConfig);
        factory.afterPropertiesSet();   // eagerly initialise so connection errors surface at startup
        return factory;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    /**
     * Sliding-window Lua script — atomically enforces the rate limit.
     *
     * KEYS[1] = Redis key (e.g. "rl:{userId}:enqueue")
     * ARGV[1] = current time in milliseconds
     * ARGV[2] = window size in milliseconds
     * ARGV[3] = max requests allowed in window
     *
     * Returns 1 if allowed, 0 if rate-limited.
     */
    @Bean
    public DefaultRedisScript<Long> slidingWindowScript() {
        String lua = """
                local key         = KEYS[1]
                local now         = tonumber(ARGV[1])
                local windowMs    = tonumber(ARGV[2])
                local limit       = tonumber(ARGV[3])
                local windowStart = now - windowMs

                -- Remove entries outside the sliding window
                redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart)

                -- Count current entries in window
                local count = redis.call('ZCARD', key)

                if count < limit then
                    -- Record this request (unique member = timestamp + random suffix)
                    redis.call('ZADD', key, now, now .. '-' .. math.random(1, 1000000))
                    -- Auto-expire the key after the window
                    redis.call('PEXPIRE', key, windowMs)
                    return 1
                else
                    return 0
                end
                """;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new StaticScriptSource(lua));
        script.setResultType(Long.class);
        return script;
    }
}
