package com.betfanatics.exchange.order.config

import com.betfanatics.exchange.order.projection.OrderProjectionHandler
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.javadsl.ShardedDaemonProcess
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.ProjectionBehavior
import org.apache.pekko.projection.ProjectionId
import org.apache.pekko.projection.eventsourced.javadsl.EventSourcedProvider
import org.apache.pekko.projection.r2dbc.javadsl.R2dbcProjection
import org.apache.pekko.persistence.r2dbc.query.javadsl.R2dbcReadJournal
import org.springframework.context.annotation.Configuration
import jakarta.annotation.PostConstruct
import java.util.Optional
import org.apache.pekko.persistence.query.Offset
import com.betfanatics.exchange.order.actor.OrderActor
import org.springframework.core.env.Environment

@Configuration
open class ProjectionConfig(
    private val system: ActorSystem<Void>,
    private val orderProjectionHandler: OrderProjectionHandler,
    private val environment: Environment
) {
    companion object {
        private const val NUM_SLICES = 4 // Adjust as needed
        private const val ORDER_ENTITY_TYPE = "OrderActor"
    }

    @PostConstruct
    open fun startOrderProjections() {
        if (environment.activeProfiles.contains("test")) {
            return
        }
        val sliceRanges = EventSourcedProvider.sliceRanges(system, R2dbcReadJournal.Identifier(), NUM_SLICES)
        for (range in sliceRanges) {
            val orderSourceProvider = EventSourcedProvider.eventsBySlices<OrderActor.Event>(
                system,
                R2dbcReadJournal.Identifier(),
                ORDER_ENTITY_TYPE,
                range.first(),
                range.second()
            )
            val projectionId = ProjectionId.of("OrderProjection", "${range.first()}-${range.second()}")
            val projection = R2dbcProjection.atLeastOnce<Offset, EventEnvelope<OrderActor.Event>>(
                projectionId,
                Optional.empty(),
                orderSourceProvider,
                { orderProjectionHandler },
                system
            )
            ShardedDaemonProcess.get(system).init(
                ProjectionBehavior.Command::class.java,
                "order-projection-${range.first()}-${range.second()}",
                1,
                { ProjectionBehavior.create(projection) }
            )
        }
    }
} 