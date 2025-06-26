package com.betfanatics.exchange.order.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

@Component
class FixMessageMetrics(private val meterRegistry: MeterRegistry) {
    
    // Message counters
    private val fixIncomingCounter = Counter.builder("fix.messages.incoming")
        .description("Count of incoming FIX messages")
        .register(meterRegistry)
    
    private val fixOutgoingCounter = Counter.builder("fix.messages.outgoing")
        .description("Count of outgoing FIX messages")
        .register(meterRegistry)
    
    private val fixHeartbeatIncomingCounter = Counter.builder("fix.heartbeats.incoming")
        .description("Count of incoming FIX heartbeats")
        .register(meterRegistry)
    
    private val fixHeartbeatOutgoingCounter = Counter.builder("fix.heartbeats.outgoing")
        .description("Count of outgoing FIX heartbeats")
        .register(meterRegistry)
    
    private val fixAdminIncomingCounter = Counter.builder("fix.admin.incoming")
        .description("Count of incoming FIX admin messages")
        .register(meterRegistry)
    
    private val fixAdminOutgoingCounter = Counter.builder("fix.admin.outgoing")
        .description("Count of outgoing FIX admin messages")
        .register(meterRegistry)
    
    private val fixUnknownMessageCounter = Counter.builder("fix.messages.unknown")
        .description("Count of unknown/unhandled FIX messages")
        .register(meterRegistry)
    
    private val fixErrorCounter = Counter.builder("fix.errors")
        .description("Count of FIX processing errors")
        .register(meterRegistry)
    
    // Order-specific counters
    private val orderIncomingCounter = Counter.builder("orders.incoming")
        .description("Count of incoming order requests")
        .register(meterRegistry)
    
    private val orderProcessedCounter = Counter.builder("orders.processed")
        .description("Count of successfully processed orders")
        .register(meterRegistry)
    
    private val orderRejectedCounter = Counter.builder("orders.rejected")
        .description("Count of rejected orders")
        .register(meterRegistry)
    
    private val executionReportCounter = Counter.builder("executions.reports")
        .description("Count of execution reports received")
        .register(meterRegistry)
    
    // Kafka message counters
    private val kafkaPublishSuccessCounter = Counter.builder("kafka.publish.success")
        .description("Count of successful Kafka publishes")
        .register(meterRegistry)
    
    private val kafkaPublishFailureCounter = Counter.builder("kafka.publish.failure")
        .description("Count of failed Kafka publishes")
        .register(meterRegistry)
    
    // REST API counters
    private val restRequestCounter = Counter.builder("rest.requests")
        .description("Count of REST API requests")
        .register(meterRegistry)
    
    private val restErrorCounter = Counter.builder("rest.errors")
        .description("Count of REST API errors")
        .register(meterRegistry)
    
    // Timing metrics
    private val orderProcessingTimer = Timer.builder("order.processing.time")
        .description("Time taken to process orders")
        .register(meterRegistry)
    
    private val fixMessageProcessingTimer = Timer.builder("fix.message.processing.time")
        .description("Time taken to process FIX messages")
        .register(meterRegistry)
    
    // Gauges for current state
    private val activeOrdersGauge = AtomicLong(0)
    private val connectedSessionsGauge = AtomicLong(0)
    
    init {
        // Register gauges
        meterRegistry.gauge("orders.active", activeOrdersGauge) { it.get().toDouble() }
        meterRegistry.gauge("fix.sessions.connected", connectedSessionsGauge) { it.get().toDouble() }
    }
    
    // FIX Message Metrics
    fun incrementFixIncoming(msgType: String = "unknown") {
        fixIncomingCounter.increment(
            io.micrometer.core.instrument.Tags.of("msgType", msgType)
        )
    }
    
    fun incrementFixOutgoing(msgType: String = "unknown") {
        fixOutgoingCounter.increment(
            io.micrometer.core.instrument.Tags.of("msgType", msgType)
        )
    }
    
    fun incrementFixHeartbeatIncoming() {
        fixHeartbeatIncomingCounter.increment()
    }
    
    fun incrementFixHeartbeatOutgoing() {
        fixHeartbeatOutgoingCounter.increment()
    }
    
    fun incrementFixAdminIncoming(msgType: String = "unknown") {
        fixAdminIncomingCounter.increment(
            io.micrometer.core.instrument.Tags.of("msgType", msgType)
        )
    }
    
    fun incrementFixAdminOutgoing(msgType: String = "unknown") {
        fixAdminOutgoingCounter.increment(
            io.micrometer.core.instrument.Tags.of("msgType", msgType)
        )
    }
    
    fun incrementFixUnknownMessage(msgType: String = "unknown", reason: String = "unknown") {
        fixUnknownMessageCounter.increment(
            io.micrometer.core.instrument.Tags.of(
                "msgType", msgType,
                "reason", reason
            )
        )
    }
    
    fun incrementFixError(errorType: String = "unknown") {
        fixErrorCounter.increment(
            io.micrometer.core.instrument.Tags.of("errorType", errorType)
        )
    }
    
    // Order Metrics
    fun incrementOrderIncoming(userId: String = "unknown") {
        orderIncomingCounter.increment(
            io.micrometer.core.instrument.Tags.of("userId", userId)
        )
    }
    
    fun incrementOrderProcessed(userId: String = "unknown") {
        orderProcessedCounter.increment(
            io.micrometer.core.instrument.Tags.of("userId", userId)
        )
    }
    
    fun incrementOrderRejected(reason: String = "unknown", userId: String = "unknown") {
        orderRejectedCounter.increment(
            io.micrometer.core.instrument.Tags.of(
                "reason", reason,
                "userId", userId
            )
        )
    }
    
    fun incrementExecutionReport(execType: String = "unknown", ordStatus: String = "unknown") {
        executionReportCounter.increment(
            io.micrometer.core.instrument.Tags.of(
                "execType", execType,
                "ordStatus", ordStatus
            )
        )
    }
    
    // Kafka Metrics
    fun incrementKafkaPublishSuccess(topic: String = "unknown") {
        kafkaPublishSuccessCounter.increment(
            io.micrometer.core.instrument.Tags.of("topic", topic)
        )
    }
    
    fun incrementKafkaPublishFailure(topic: String = "unknown", reason: String = "unknown") {
        kafkaPublishFailureCounter.increment(
            io.micrometer.core.instrument.Tags.of(
                "topic", topic,
                "reason", reason
            )
        )
    }
    
    // REST Metrics
    fun incrementRestRequest(endpoint: String = "unknown", method: String = "unknown") {
        restRequestCounter.increment(
            io.micrometer.core.instrument.Tags.of(
                "endpoint", endpoint,
                "method", method
            )
        )
    }
    
    fun incrementRestError(endpoint: String = "unknown", errorCode: String = "unknown") {
        restErrorCounter.increment(
            io.micrometer.core.instrument.Tags.of(
                "endpoint", endpoint,
                "errorCode", errorCode
            )
        )
    }
    
    // Timing Metrics
    fun recordOrderProcessingTime(timeMs: Long) {
        orderProcessingTimer.record(timeMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }
    
    fun recordFixMessageProcessingTime(timeMs: Long) {
        fixMessageProcessingTimer.record(timeMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }
    
    // Gauge Updates
    fun updateActiveOrders(count: Long) {
        activeOrdersGauge.set(count)
    }
    
    fun incrementActiveOrders() {
        activeOrdersGauge.incrementAndGet()
    }
    
    fun decrementActiveOrders() {
        activeOrdersGauge.decrementAndGet()
    }
    
    fun updateConnectedSessions(count: Long) {
        connectedSessionsGauge.set(count)
    }
    
    fun incrementConnectedSessions() {
        connectedSessionsGauge.incrementAndGet()
    }
    
    fun decrementConnectedSessions() {
        connectedSessionsGauge.decrementAndGet()
    }
}