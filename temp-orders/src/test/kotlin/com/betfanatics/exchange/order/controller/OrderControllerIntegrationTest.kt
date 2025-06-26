package com.betfanatics.exchange.order.controller

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.*
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import com.typesafe.config.ConfigFactory
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding
import org.apache.pekko.cluster.typed.Cluster
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Primary
import org.springframework.boot.test.mock.mockito.MockBean
import com.betfanatics.exchange.order.actor.FixGatewayActor
import com.betfanatics.exchange.order.actor.FBGWalletActor
import com.betfanatics.exchange.order.actor.KalshiWalletActor
import java.math.BigDecimal
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.mockito.Mockito
import org.junit.jupiter.api.Disabled

@TestConfiguration
open class MockBeansConfig {
    @Bean
    open fun fixGatewayActor(): ActorRef<FixGatewayActor.Command> = Mockito.mock(ActorRef::class.java) as ActorRef<FixGatewayActor.Command>

    @Bean
    open fun fbgWalletActor(): ActorRef<FBGWalletActor.Command> = Mockito.mock(ActorRef::class.java) as ActorRef<FBGWalletActor.Command>

    @Bean
    open fun kalshiWalletActor(): ActorRef<KalshiWalletActor.Command> = Mockito.mock(ActorRef::class.java) as ActorRef<KalshiWalletActor.Command>

    @Bean
    open fun oAuth2AuthorizedClientManager(): OAuth2AuthorizedClientManager = Mockito.mock(OAuth2AuthorizedClientManager::class.java)
}

@Disabled
@ActiveProfiles("test") // disables projections
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = [MockBeansConfig::class])
class OrderControllerIntegrationTest {
    companion object {
        // Pekko in-memory config as a string
        private val pekkoConfig = """
            pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
            pekko.persistence.journal.inmem.class = "org.apache.pekko.persistence.journal.inmem.InmemJournal"
            pekko.persistence.snapshot-store.plugin = "pekko.persistence.snapshot-store.local"
            pekko.persistence.snapshot-store.local.dir = "target/snapshots-test"
            pekko.actor.provider = "cluster"
            pekko.remote.artery.canonical.port = 0
            pekko.remote.artery.canonical.hostname = "127.0.0.1"
            pekko.cluster.jmx.multi-mbeans-in-same-jvm = on
        """.trimIndent()
        
        @JvmStatic
        @DynamicPropertySource
        fun registerPekkoProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.pekko.config") { pekkoConfig }
            registry.add("grpc.server.port") { "0" }  // TODO we aren't actually using gRPC in this app, so should remove it properly
        }
    }

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @LocalServerPort
    var port: Int = 0

    @Test
    @Disabled("Disabling stub test while implementing real actor integration")
    fun `should accept v1 order and return stubbed response`() {
        val url = "http://localhost:$port/v1/order"
        val request = mapOf(
            "symbol" to "BTC-USD",
            "side" to "BUY",
            "quantity" to BigDecimal.ONE,
            "price" to BigDecimal(10000),
            "orderType" to "LIMIT"
        )
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val entity = HttpEntity(request, headers)
        val response = restTemplate.postForEntity(url, entity, String::class.java)
        assertTrue(response.statusCode == HttpStatus.OK)
        assertTrue(response.body?.contains("Received v1 order") == true)
    }
} 