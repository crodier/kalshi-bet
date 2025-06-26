package com.betfanatics.exchange.order.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity
import com.betfanatics.exchange.order.actor.OrderManagementActor
import com.betfanatics.exchange.order.actor.EMSActor
import com.betfanatics.exchange.order.actor.FixGatewayActor
import com.betfanatics.exchange.order.messaging.KafkaSender
import org.apache.pekko.actor.typed.ActorRef
import javax.sql.DataSource

@Configuration
class ActorSystemConfig {

    @Bean
    @DependsOn("flywayMigration")
    fun orderManagementActorTypeKey(
        clusterSharding: ClusterSharding,
        kafkaSender: KafkaSender,
        riskLimitsConfig: RiskLimitsConfig,
        kafkaTopicsConfig: KafkaTopicsConfig,
        dataSource: DataSource
    ): EntityTypeKey<OrderManagementActor.Command> {
        
        val typeKey = EntityTypeKey.create(
            OrderManagementActor.Command::class.java, 
            "OrderManagementActor"
        )
        
        clusterSharding.init(
            Entity.of(typeKey) { entityContext ->
                OrderManagementActor.create(
                    entityContext.entityId,
                    kafkaSender,
                    riskLimitsConfig,
                    kafkaTopicsConfig,
                    dataSource
                )
            }
        )
        
        return typeKey
    }

    @Bean
    fun emsActorTypeKey(
        clusterSharding: ClusterSharding,
        kafkaSender: KafkaSender,
        kafkaTopicsConfig: KafkaTopicsConfig
    ): EntityTypeKey<EMSActor.Command> {
        
        val typeKey = EntityTypeKey.create(
            EMSActor.Command::class.java, 
            "EMSActor"
        )
        
        clusterSharding.init(
            Entity.of(typeKey) { entityContext ->
                EMSActor.create(
                    entityContext.entityId,
                    kafkaSender,
                    kafkaTopicsConfig,
                    null // FIX Gateway not available during testing
                )
            }
        )
        
        return typeKey
    }
}