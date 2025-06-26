package com.betfanatics.exchange.order.config

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.ActorRef
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import com.betfanatics.exchange.order.actor.FixGatewayActor
import org.apache.pekko.actor.typed.Props
import org.apache.pekko.cluster.typed.ClusterSingleton
import org.apache.pekko.cluster.typed.SingletonActor
import com.typesafe.config.ConfigFactory
import org.springframework.core.env.Environment
import org.springframework.beans.factory.annotation.Autowired
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey
import com.betfanatics.exchange.order.actor.OrderProcessManager
import com.betfanatics.exchange.order.actor.OrderActor
import com.betfanatics.exchange.order.actor.FBGWalletActor
import com.betfanatics.exchange.order.actor.KalshiWalletActor
import com.betfanatics.exchange.order.actor.common.OrderActorResolver
import com.betfanatics.exchange.order.actor.common.OrderProcessManagerResolver
import javax.sql.DataSource
import org.apache.pekko.actor.typed.SupervisorStrategy
import java.time.Duration
import com.betfanatics.exchange.order.actor.PositionActor
import com.betfanatics.exchange.order.actor.common.PositionActorResolver
import com.betfanatics.exchange.order.health.FixHealthIndicator
import com.betfanatics.exchange.order.service.FixErrorService
import lombok.extern.slf4j.Slf4j

@Slf4j
@Configuration
open class PekkoConfig {
    @Autowired
    lateinit var environment: Environment

    @Bean(destroyMethod = "terminate")
    open fun actorSystem(): ActorSystem<Void> {
        // Get the config file name from Spring config, default to application.conf
        val configFile = environment.getProperty("pekko.config", "classpath:application.conf")
        val config = if (configFile.startsWith("classpath:")) {
            ConfigFactory.parseResources(configFile.removePrefix("classpath:")).withFallback(ConfigFactory.load())
        } else {
            ConfigFactory.parseFile(java.io.File(configFile)).withFallback(ConfigFactory.load())
        }
        return ActorSystem.create(Behaviors.empty<Void>(), "PekkoSystem", config.resolve())
    }

    @Bean
    open fun orderProcessManagerTypeKey(): EntityTypeKey<OrderProcessManager.Command> =
        EntityTypeKey.create(OrderProcessManager.Command::class.java, "OrderProcessManager")

    @Bean
    open fun positionActorTypeKey(): EntityTypeKey<PositionActor.Command> =
        EntityTypeKey.create(PositionActor.Command::class.java, "PositionActor")

    @Bean
    open fun orderActorTypeKey(): EntityTypeKey<OrderActor.Command> =
        EntityTypeKey.create(OrderActor.Command::class.java, "OrderActor")

    // to be injected into actors to find other actors
    @Bean
    open fun orderActorResolver(clusterSharding: ClusterSharding, orderActorTypeKey: EntityTypeKey<OrderActor.Command>): OrderActorResolver =
        { entityId ->
            clusterSharding.entityRefFor(orderActorTypeKey, entityId)
        }

    @Bean
    open fun orderProcessManagerResolver(clusterSharding: ClusterSharding, orderProcessManagerTypeKey: EntityTypeKey<OrderProcessManager.Command>): OrderProcessManagerResolver =
        { entityId ->
            clusterSharding.entityRefFor(orderProcessManagerTypeKey, entityId)
        }

    @Bean
    open fun positionActorResolver(clusterSharding: ClusterSharding, positionActorTypeKey: EntityTypeKey<PositionActor.Command>): PositionActorResolver =
        { entityId ->
            clusterSharding.entityRefFor(positionActorTypeKey, entityId)
        }

    // Cluster singleton actor to interface with Kalshi's FIX API
    @Bean
    open fun fixGatewayActor(
        actorSystem: ActorSystem<Void>,
        orderActorResolver: OrderActorResolver,
        dataSource: DataSource,
        fixHealthIndicator: FixHealthIndicator,
        fixErrorService: FixErrorService,
        fixClOrdIdGenerator: com.betfanatics.exchange.order.util.FixClOrdIdGenerator,
        clOrdIdMappingService: com.betfanatics.exchange.order.service.ClOrdIdMappingService,
        executionReportEnrichmentService: com.betfanatics.exchange.order.service.ExecutionReportEnrichmentService
    ): ActorRef<FixGatewayActor.Command> {

        System.setProperty("quickfixj.session.debug", "true");

        var settingsPath = environment.getProperty("quickfixj.config.file")

        // local will be a mock server.
        // "dev" will be an integration area which uses Kalshi "dev" (not prod) FIX engine
        val isSpringMockEnvActive = environment.activeProfiles.contains("mock")

        if (isSpringMockEnvActive) {
            // in application-local.yml, see:  "classpath:quickfixj-mock.cfg"
            settingsPath = environment.getProperty("quickfixj.mock.config.file")

            println("Using MOCK QuickFIX/J settings: "+settingsPath+", from property: quickfixj.mock.config.file,  " +
                    "(Will connect to a MOCK SERVER running on Localhost)")
        }
        else {
            println("Using standard QuickFIX/J settings: "+settingsPath+", from spring application property: quickfixj.config.file")
        }

        val singletonManager = ClusterSingleton.get(actorSystem)
        val proxy = singletonManager.init(
            SingletonActor.of(
                Behaviors.supervise(
                    FixGatewayActor.create(isSpringMockEnvActive,settingsPath, orderActorResolver, dataSource, fixHealthIndicator, fixErrorService, fixClOrdIdGenerator, clOrdIdMappingService, executionReportEnrichmentService))
                    .onFailure(SupervisorStrategy.restartWithBackoff(Duration.ofSeconds(1), Duration.ofSeconds(30), 0.2)),
                "FixGatewayActor"
            )
        )
        return proxy as ActorRef<FixGatewayActor.Command>
    }

    // Prepare sharding for the actors
    @Bean
    open fun clusterSharding(actorSystem: ActorSystem<Void>): ClusterSharding =
        ClusterSharding.get(actorSystem)

    @Bean
    open fun orderProcessManagerSharding(
        clusterSharding: ClusterSharding,
        fbgWalletActor: org.apache.pekko.actor.typed.ActorRef<FBGWalletActor.Command>,
        kalshiWalletActor: org.apache.pekko.actor.typed.ActorRef<KalshiWalletActor.Command>,
        orderActorResolver: OrderActorResolver,
        orderProcessManagerTypeKey: EntityTypeKey<OrderProcessManager.Command>,
        positionActorResolver: PositionActorResolver
    ) : EntityTypeKey<OrderProcessManager.Command> {
        clusterSharding.init(
            Entity.of(orderProcessManagerTypeKey) { entityContext ->
                OrderProcessManager.create(
                    entityContext.entityId,
                    fbgWalletActor,
                    kalshiWalletActor,
                    orderActorResolver,
                    positionActorResolver
                )
            }
        )
        return orderProcessManagerTypeKey
    }

    @Bean
    open fun positionActorSharding(
        clusterSharding: ClusterSharding,
        positionActorTypeKey: EntityTypeKey<PositionActor.Command>
    ): EntityTypeKey<PositionActor.Command> {
        clusterSharding.init(
            Entity.of(positionActorTypeKey) { entityContext ->
                PositionActor.create(entityContext.entityId)
            }
        )
        return positionActorTypeKey
    }

    @Bean
    open fun orderActorSharding(
        clusterSharding: ClusterSharding,
        fixGatewayActor: ActorRef<FixGatewayActor.Command>,
        orderProcessManagerResolver: OrderProcessManagerResolver,
        orderActorTypeKey: EntityTypeKey<OrderActor.Command>,
        positionActorResolver: PositionActorResolver
    ) : EntityTypeKey<OrderActor.Command> {
        clusterSharding.init(Entity.of(orderActorTypeKey) { entityContext ->
            OrderActor.create(
                entityContext.entityId,
                fixGatewayActor,
                orderProcessManagerResolver,
                positionActorResolver
            )
        })
        return orderActorTypeKey
    }

    // One of each, per node
    // Given non-blocking IO for the outbound requests, this should be adequate
    // Potentially could use a pool of actors here
    // https://pekko.apache.org/docs/pekko/current/typed/routers.html#pool-router
    @Bean
    open fun fbgWalletActor(actorSystem: ActorSystem<Void>): org.apache.pekko.actor.typed.ActorRef<FBGWalletActor.Command> =
        actorSystem.systemActorOf(FBGWalletActor.create(), "FBGWalletActor", Props.empty())

    @Bean
    open fun kalshiWalletActor(actorSystem: ActorSystem<Void>): org.apache.pekko.actor.typed.ActorRef<KalshiWalletActor.Command> =
        actorSystem.systemActorOf(KalshiWalletActor.create(), "KalshiWalletActor", Props.empty())
} 