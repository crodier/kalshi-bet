package com.betfanatics.exchange.order.config

import com.betfanatics.exchange.order.util.FixMessageLogger
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener

@Configuration
class FixMessageLoggerConfig {
    
    @EventListener
    fun onApplicationEvent(event: ContextRefreshedEvent) {
        // Set the static instance for backward compatibility when Spring context is ready
        val fixMessageLogger = event.applicationContext.getBean(FixMessageLogger::class.java)
        FixMessageLogger.setInstance(fixMessageLogger)
    }
}