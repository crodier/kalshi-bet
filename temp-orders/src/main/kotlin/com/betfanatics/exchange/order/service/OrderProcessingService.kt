package com.betfanatics.exchange.order.service

import org.springframework.stereotype.Service
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.AskPattern
import com.betfanatics.exchange.order.actor.OrderManagementActor
import com.betfanatics.exchange.order.actor.EMSActor
import com.betfanatics.exchange.order.actor.common.OrderRequestDTO
import com.betfanatics.exchange.order.config.KafkaTopicsConfig
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.concurrent.CompletableFuture
import org.slf4j.LoggerFactory

@Service
class OrderProcessingService @Autowired constructor(
    private val clusterSharding: ClusterSharding,
    private val actorSystem: ActorSystem<Void>,
    private val kafkaTopicsConfig: KafkaTopicsConfig,
    private val objectMapper: ObjectMapper
) {
    
    private val log = LoggerFactory.getLogger(OrderProcessingService::class.java)
    
    private val orderManagementTypeKey: EntityTypeKey<OrderManagementActor.Command> =
        EntityTypeKey.create(OrderManagementActor.Command::class.java, "OrderManagementActor")
    
    private val emsActorTypeKey: EntityTypeKey<EMSActor.Command> =
        EntityTypeKey.create(EMSActor.Command::class.java, "EMSActor")

    fun processOrder(orderRequest: OrderRequestDTO): CompletableFuture<OrderManagementActor.Response> {
        log.info("Processing order via OrderManagementActor: orderId={}, userId={}", 
            orderRequest.orderId, orderRequest.userId)
        
        val orderManagementRef: EntityRef<OrderManagementActor.Command> =
            clusterSharding.entityRefFor(orderManagementTypeKey, orderRequest.orderId)
        
        val timeout = Duration.ofSeconds(30)
        
        return AskPattern.ask(
            orderManagementRef,
            { replyTo ->
                OrderManagementActor.ProcessOrder(orderRequest, replyTo)
            },
            timeout,
            actorSystem.scheduler()
        ).toCompletableFuture()
    }

    @KafkaListener(topics = ["#{kafkaTopicsConfig.fixOrderTopic.name}"])
    fun handleOrderFromKafka(message: String) {
        log.info("Received order from Kafka topic: {}", kafkaTopicsConfig.fixOrderTopic.name)
        
        try {
            @Suppress("UNCHECKED_CAST")
            val orderMessage = objectMapper.readValue(message, Map::class.java) as Map<String, Any>
            val orderId = orderMessage["orderId"] as String
            
            log.info("Processing order from Kafka: orderId={}", orderId)
            
            val emsActorRef: EntityRef<EMSActor.Command> =
                clusterSharding.entityRefFor(emsActorTypeKey, orderId)
            
            val timeout = Duration.ofSeconds(30)
            
            AskPattern.ask(
                emsActorRef,
                { replyTo ->
                    EMSActor.ProcessOrderFromKafka(orderMessage, replyTo)
                },
                timeout,
                actorSystem.scheduler()
            ).thenAccept { response ->
                when (response) {
                    is EMSActor.OrderProcessingStarted -> {
                        log.info("EMS processing started for orderId: {}", response.orderId)
                    }
                    is EMSActor.ProcessingFailed -> {
                        log.error("EMS processing failed for orderId: {}, reason: {}", 
                            response.orderId, response.reason)
                    }
                    else -> {
                        log.warn("Unexpected response from EMSActor: {}", response)
                    }
                }
            }.exceptionally { throwable ->
                log.error("Error processing order from Kafka: orderId={}, error={}", 
                    orderId, throwable.message, throwable)
                null
            }
            
        } catch (e: Exception) {
            log.error("Error parsing Kafka message: error={}, message={}", e.message, message, e)
        }
    }

    @KafkaListener(topics = ["#{kafkaTopicsConfig.fixExecutionTopic.name}"])
    fun handleExecutionReport(message: String) {
        log.info("Received execution report from Kafka topic: {}", kafkaTopicsConfig.fixExecutionTopic.name)
        
        try {
            @Suppress("UNCHECKED_CAST")
            val executionMessage = objectMapper.readValue(message, Map::class.java) as Map<String, Any>
            val orderId = executionMessage["orderId"] as String
            
            log.info("Processing execution report: orderId={}, status={}", 
                orderId, executionMessage["status"])
            
            // Here we could update order status, notify other systems, etc.
            // For now, just log the execution report
            
        } catch (e: Exception) {
            log.error("Error parsing execution report: error={}, message={}", e.message, message, e)
        }
    }
}