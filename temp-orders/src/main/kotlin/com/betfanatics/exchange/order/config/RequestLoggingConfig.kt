package com.betfanatics.exchange.order.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.HandlerInterceptor
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.*

@Configuration
class RequestLoggingConfig : WebMvcConfigurer {

    @Bean
    fun requestLoggingInterceptor(): RequestLoggingInterceptor {
        return RequestLoggingInterceptor()
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(requestLoggingInterceptor())
    }
}

class RequestLoggingInterceptor : HandlerInterceptor {
    
    private val log = LoggerFactory.getLogger(RequestLoggingInterceptor::class.java)
    
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val requestId = UUID.randomUUID().toString().substring(0, 8)
        val startTime = System.currentTimeMillis()
        
        // Add to MDC for correlation across logs
        MDC.put("requestId", requestId)
        MDC.put("startTime", startTime.toString())
        
        // Log REST API arrival
        log.info("REST_ARRIVAL: method={} uri={} requestId={} remoteAddr={} userAgent={} contentType={}",
            request.method,
            request.requestURI,
            requestId,
            getClientIpAddress(request),
            request.getHeader("User-Agent") ?: "unknown",
            request.contentType ?: "none")
        
        // Log headers for debugging
        if (log.isDebugEnabled) {
            val headers = mutableMapOf<String, String>()
            request.headerNames.asIterator().forEach { headerName ->
                headers[headerName] = request.getHeader(headerName)
            }
            log.debug("REST_HEADERS: requestId={} headers={}", requestId, headers)
        }
        
        // Log query parameters
        if (request.queryString != null) {
            log.info("REST_QUERY_PARAMS: requestId={} queryString={}", requestId, request.queryString)
        }
        
        return true
    }
    
    override fun afterCompletion(
        request: HttpServletRequest, 
        response: HttpServletResponse, 
        handler: Any, 
        ex: Exception?
    ) {
        val requestId = MDC.get("requestId")
        val startTime = MDC.get("startTime")?.toLongOrNull() ?: System.currentTimeMillis()
        val duration = System.currentTimeMillis() - startTime
        
        log.info("REST_COMPLETION: requestId={} status={} duration={}ms exception={}",
            requestId,
            response.status,
            duration,
            ex?.message ?: "none")
        
        // Clean up MDC
        MDC.remove("requestId")
        MDC.remove("startTime")
    }
    
    private fun getClientIpAddress(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        if (!xForwardedFor.isNullOrBlank()) {
            return xForwardedFor.split(",")[0].trim()
        }
        
        val xRealIp = request.getHeader("X-Real-IP")
        if (!xRealIp.isNullOrBlank()) {
            return xRealIp
        }
        
        return request.remoteAddr ?: "unknown"
    }
}