package com.betfanatics.exchange.order.integration

import com.betfanatics.exchange.order.actor.FixGatewayActor
import com.betfanatics.exchange.order.actor.FBGWalletActor
import com.betfanatics.exchange.order.actor.KalshiWalletActor
import org.apache.pekko.actor.typed.ActorRef
import org.mockito.Mockito
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager

@TestConfiguration
class FixIntegrationTestConfig {

    @Bean
    @Primary
    fun mockFixGatewayActor(): ActorRef<FixGatewayActor.Command> = 
        Mockito.mock(ActorRef::class.java) as ActorRef<FixGatewayActor.Command>

    @Bean
    @Primary
    fun mockFbgWalletActor(): ActorRef<FBGWalletActor.Command> = 
        Mockito.mock(ActorRef::class.java) as ActorRef<FBGWalletActor.Command>

    @Bean
    @Primary  
    fun mockKalshiWalletActor(): ActorRef<KalshiWalletActor.Command> = 
        Mockito.mock(ActorRef::class.java) as ActorRef<KalshiWalletActor.Command>

    @Bean
    @Primary
    fun mockOAuth2AuthorizedClientManager(): OAuth2AuthorizedClientManager = 
        Mockito.mock(OAuth2AuthorizedClientManager::class.java)
}