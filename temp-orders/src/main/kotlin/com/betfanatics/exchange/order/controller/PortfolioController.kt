package com.betfanatics.exchange.order.controller

import com.betfanatics.exchange.order.projection.OrderProjection
import com.betfanatics.exchange.order.projection.OrderProjectionRepository
import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity
import reactor.core.publisher.Mono
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey
import com.betfanatics.exchange.order.actor.PositionActor
import java.time.Duration
import org.apache.pekko.util.Timeout
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef
import com.betfanatics.exchange.order.actor.common.PositionActorResolver

@RestController
@RequestMapping("/v1/portfolio")
class PortfolioController(
    private val orderProjectionRepository: OrderProjectionRepository,
    private val positionActorResolver: PositionActorResolver
) {
    @GetMapping
    fun getPortfolio(
        @RequestParam userId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): Mono<ResponseEntity<Map<String, Any>>> {
        val offset = page * size
        val positionsFlux = orderProjectionRepository.findPortfolioByUserId(userId, size, offset).collectList()
        val countMono = orderProjectionRepository.countPortfolioByUserId(userId)

        return Mono.zip(positionsFlux, countMono).map { tuple ->
            val positions = tuple.t1
            val total = tuple.t2
            val totalPages = if (size == 0) 1 else ((total + size - 1) / size).toInt()
            ResponseEntity.ok(
                mapOf(
                    "userId" to userId,
                    "positions" to positions,
                    "page" to page,
                    "size" to size,
                    "totalPositions" to total,
                    "totalPages" to totalPages
                )
            )
        }
    }

    // This is primarily for testing
    // When we want to expose to customers, we should make a projection
    @GetMapping("/position")
    fun getPosition(
        @RequestParam userId: String,
        @RequestParam symbol: String
    ): Mono<ResponseEntity<Map<String, Any>>> {
        val positionRef = positionActorResolver(userId)
        val timeout = Duration.ofSeconds(3)
        return Mono.fromFuture {
            positionRef.ask(
                { replyTo -> PositionActor.GetPosition(symbol, replyTo) },
                timeout
            ).toCompletableFuture()
            .thenApply { result ->
                when (result) {
                    is PositionActor.PositionResult -> ResponseEntity.ok(
                        mapOf(
                            "userId" to userId,
                            "symbol" to symbol,
                            "netPosition" to result.netPosition
                        )
                    )
                    else -> ResponseEntity.status(500).body(mapOf("error" to "Unexpected response"))
                }
            }
        }
    }
} 