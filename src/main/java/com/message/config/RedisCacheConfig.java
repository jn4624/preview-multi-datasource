package com.message.config;

import java.time.Duration;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

@Configuration
@EnableCaching
public class RedisCacheConfig {

	@Bean
	public RedisCacheConfiguration redisCacheConfiguration() {
		return RedisCacheConfiguration.defaultCacheConfig()
			.entryTtl(Duration.ofMinutes(10))
			.disableCachingNullValues()
			/*
			  - 해당 설정을 하지 않으면 자바 기본 바이트 직/역직렬화가 사용됨
			  - 캐싱된 내용이 바이트코드라 눈으로 확인이 어려움
			 */
			.serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
	}

	@Bean
	public CacheManager cacheManager(RedisConnectionFactory factory) {
		return RedisCacheManager.builder(factory)
			.cacheDefaults(redisCacheConfiguration())
			.transactionAware()
			.build();
	}
}
