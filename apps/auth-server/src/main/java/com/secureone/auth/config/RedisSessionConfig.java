package com.secureone.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;

@Configuration
@EnableRedisIndexedHttpSession(redisNamespace = "${spring.session.redis.namespace:secureone:session}")
public class RedisSessionConfig {}
