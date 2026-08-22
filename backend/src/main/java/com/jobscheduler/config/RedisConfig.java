package com.jobscheduler.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SslOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.StaticScriptSource;

import java.net.URI;
import java.time.Duration;

/**
 * Configures Lettuce to connect to Upstash Redis over TLS (rediss://).
 *
 * Upstash requires:
 *  - TLS (rediss:// scheme)
 *  - Password auth
 *  - Default DB 0
 *
 * We parse the upstash.redis.url property to extract host/port/password
 * so the single URL is the only thing you need to change.
 */
@Configuration
public class RedisConfig {

    @Value("${upstash.redis.url}")
    private String redisUrl;

    @Value("${upstash.redis.password}")
    private String redisPassword;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        // Java's URI class doesn't know the "rediss" scheme, so normalise to "redis"
        // just for parsing — TLS is enforced by LettuceClientConfiguration.useSsl().
        URI uri = URI.create(redisUrl.replaceFirst("^rediss://", "redis://"));

        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration();
        serverConfig.setHostName(uri.getHost());
        serverConfig.setPort(uri.getPort() > 0 ? uri.getPort() : 6379);
        serverConfig.setPassword(redisPassword);
        serverConfig.setDatabase(0);

        // Upstash mandates TLS — enable SSL + disable hostname verification
        // (Upstash uses wildcard certs; hostname verification is safe to skip here
        //  because the password provides authentication).
        SslOptions sslOptions = SslOptions.builder()
                .jdkSslProvider()
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .sslOptions(sslOptions)
                .build();

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .useSsl()
                .and()
                .commandTimeout(Duration.ofSeconds(5))
                .clientOptions(clientOptions)
                .build();

        return new LettuceConnectionFactory(serverConfig, clientConfig);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    /**
     * Pre-compiled sliding-window Lua script.
     * Stored as a bean so it is SHA-cached by Redis on first EVALSHA call.
     *
     * Args passed at runtime:
     *   KEYS[1] = rate limit key  e.g. "rl:user:<userId>:enqueue"
     *   ARGV[1] = current time in milliseconds (as string)
     *   ARGV[2] = window size in milliseconds  (as string)
     *   ARGV[3] = request limit                (as string)
     *
     * Returns:
     *   1  — request allowed
     *   0  — rate limit exceeded
     */
    @Bean
    public DefaultRedisScript<Long> slidingWindowScript() {
        String lua = """
                local key        = KEYS[1]
                local now        = tonumber(ARGV[1])
                local windowMs   = tonumber(ARGV[2])
                local limit      = tonumber(ARGV[3])
                local windowStart = now - windowMs

                -- Remove timestamps outside the current window
                redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart)

                -- Count remaining requests in window
                local count = redis.call('ZCARD', key)

                if count < limit then
                    -- Add this request with score = timestamp
                    redis.call('ZADD', key, now, now .. '-' .. math.random(1, 1000000))
                    -- Expire the key after the window so it auto-cleans
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
