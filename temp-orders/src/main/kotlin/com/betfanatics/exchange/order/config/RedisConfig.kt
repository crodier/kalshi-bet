package com.betfanatics.exchange.order.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer
import javax.annotation.PostConstruct

@Configuration
class RedisConfig(
    @Value("\${spring.data.redis.host:localhost}") private val redisHost: String,
    @Value("\${spring.data.redis.port:6379}") private val redisPort: Int,
    @Value("\${spring.data.redis.password:}") private val redisPassword: String
) {
    
    private val log = LoggerFactory.getLogger(RedisConfig::class.java)
    
    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory {
        val config = RedisStandaloneConfiguration(redisHost, redisPort)
        if (redisPassword.isNotBlank()) {
            config.setPassword(redisPassword)
        }
        
        val factory = LettuceConnectionFactory(config)
        
        // This ensures the connection is validated on startup
        factory.setValidateConnection(true)
        factory.setShareNativeConnection(false)
        
        return factory
    }
    
    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, String> {
        val template = RedisTemplate<String, String>()
        template.connectionFactory = connectionFactory
        template.keySerializer = StringRedisSerializer()
        template.valueSerializer = StringRedisSerializer()
        template.hashKeySerializer = StringRedisSerializer()
        template.hashValueSerializer = StringRedisSerializer()
        
        // Initialize the template
        template.afterPropertiesSet()
        
        return template
    }
    
    @PostConstruct
    fun validateRedisConnection() {
        log.info("Validating Redis connection to {}:{}", redisHost, redisPort)
        
        try {
            val factory = redisConnectionFactory()
            val connection = factory.connection
            
            // Test the connection with a PING command
            val pong = connection.ping()
            if (pong == null) {
                throw IllegalStateException("Redis PING returned null - connection may not be working")
            }
            
            connection.close()
            log.info("Redis connection validated successfully - PING returned: {}", String(pong))
            
        } catch (e: Exception) {
            log.error("FATAL: Cannot connect to Redis at {}:{} - {}", redisHost, redisPort, e.message)
            // This will prevent the application from starting
            throw IllegalStateException("Redis connection is required but not available. " +
                "Please ensure Redis is running at $redisHost:$redisPort", e)
        }
    }
}