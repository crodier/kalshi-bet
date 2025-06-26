package com.betfanatics.exchange.order.actor

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.Behaviors
import com.betfanatics.exchange.model.OrderRequest
import quickfix.*
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.charset.StandardCharsets
import java.security.PrivateKey
import com.betfanatics.exchange.order.util.KalshiSignatureUtil
import quickfix.field.*
import quickfix.fix50sp2.*
import quickfix.Message
import quickfix.SessionNotFound
import java.time.Instant
import org.apache.pekko.actor.typed.ActorRef
import com.betfanatics.exchange.order.actor.common.OrderRequestDTO
import com.betfanatics.exchange.order.actor.common.OrderSide
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey
import java.math.BigDecimal
import com.betfanatics.exchange.order.actor.common.OrderActorResolver
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef
import javax.sql.DataSource
import org.slf4j.Logger
import com.betfanatics.exchange.order.util.FixMessageLogger
import com.betfanatics.exchange.order.util.FixClOrdIdGenerator
import com.betfanatics.exchange.order.health.FixHealthIndicator
import com.betfanatics.exchange.order.service.FixErrorService
import com.betfanatics.exchange.order.service.ClOrdIdMappingService
import com.betfanatics.exchange.order.service.ExecutionReportEnrichmentService

object FixGatewayActor {
    // Define the command protocol
    sealed class Command : SerializationMarker {
        object Connect : Command()
        object Disconnect : Command()
        data class SendOrder(val orderId: String, val order: OrderRequestDTO, val replyTo: ActorRef<Response>) : Command()
        data class CancelOrder(val betOrderId: String, val cancelClOrdId: String, val origClOrdId: String, val userId: String, val replyTo: ActorRef<Response>) : Command()
        object GetStatus : Command()
    }

    // Responses
    sealed interface Response
    data class OrderAccepted(val orderId: String, val timestamp: Instant) : Response
    data class OrderRejected(val orderId: String, val reason: String) : Response
    data class OrderCancelled(val orderId: String, val reason: String) : Response
    
    var mySessionId: SessionID? = null

    fun create(isMockMode: Boolean, settingsPath: String, orderActorResolver: OrderActorResolver, dataSource: DataSource, fixHealthIndicator: FixHealthIndicator, fixErrorService: FixErrorService, fixClOrdIdGenerator: FixClOrdIdGenerator, clOrdIdMappingService: ClOrdIdMappingService, executionReportEnrichmentService: ExecutionReportEnrichmentService): Behavior<Command> = Behaviors.setup { context ->
        var initiator: SocketInitiator? = null
        var connected = false

        val log = context.log
        log.info("FixGatewayActor started with settingsPath: {}", settingsPath)

        // Load private key
        val privateKey: PrivateKey? = try {
            log.info("Loading private key from kalshi-fix.key")
            val privateKeyPem = String(Files.readAllBytes(Paths.get("kalshi-fix.key")), StandardCharsets.UTF_8)
            KalshiSignatureUtil.loadPrivateKey(privateKeyPem)
        } catch (e: Exception) {
            log.error("Could not load private key: {}", e.message)
            null
        }

        log.info("FixGatewayActor created with settingsPath: {}", settingsPath)

        val fixApp = QuickfixJApplication(
            isMockMode,
            privateKey,
            orderActorResolver,
            fixHealthIndicator,
            fixErrorService,
            fixClOrdIdGenerator,
            clOrdIdMappingService,
            executionReportEnrichmentService,
            onConnected = { sessionId ->
                connected = true
                mySessionId = sessionId
            },
            onDisconnected = {
                connected = false
            }
        )

        context.self.tell(Command.Connect) // Connect to FIX

        Behaviors.receiveMessage { msg ->

            when (msg) {
                is Command.Connect -> {
                    if (initiator == null) {
                        log.info("Received Connect command. Starting QuickFIX/J initiator...")

                        var settingsPathResolved = settingsPath;

                        if (settingsPathResolved.startsWith("classpath:")) {
                            settingsPathResolved = settingsPathResolved.substring("classpath:".length)
                            log.info("settingsPathResolved: {}", settingsPathResolved)
                            val resourceStream = this::class.java.classLoader.getResourceAsStream(settingsPathResolved)
                            requireNotNull(resourceStream) { "$settingsPathResolved not found on classpath" }
                        }
                        
                        val resourceStream = this::class.java.classLoader.getResourceAsStream(settingsPathResolved)

                        requireNotNull(resourceStream) { " Terminal config error, VERY BAD: QuickfixJ config, settings file: "+settingsPathResolved +" not found on classpath" }

                        try {
                            val settings = SessionSettings(resourceStream)
                            val storeFactory = JdbcStoreFactory(settings)
                            storeFactory.setDataSource(dataSource);
                            val logFactory = SLF4JLogFactory(settings)
                            val messageFactory = DefaultMessageFactory()
                            initiator = SocketInitiator(fixApp, storeFactory, settings, logFactory, messageFactory)
                            initiator?.start()
                        } catch (e: Exception) {
                            log.error("Failed to start FIX initiator: {}", e.message, e)
                            fixHealthIndicator.setDisconnected("Failed to start FIX initiator: ${e.message}")
                            fixErrorService.reportConnectionError(null, 
                                "Failed to start FIX initiator: ${e.message}",
                                mapOf("exception" to e.javaClass.simpleName, "settingsPath" to settingsPathResolved))
                        }
                    } else {
                        log.info("Connect command received, but initiator is already running.")
                    }
                }
                is Command.Disconnect -> {
                    log.info("Received Disconnect command. Stopping QuickFIX/J initiator...")
                    initiator?.stop()
                    initiator = null
                }
                is Command.SendOrder -> {
                    handleSendOrder(msg, log, connected, mySessionId, clOrdIdMappingService)                
                }
                is Command.CancelOrder -> {
                    handleCancelOrder(msg, log, connected, mySessionId)
                }
                is Command.GetStatus -> {
                    log.info("Received GetStatus command. Connected: {}", connected)
                    // TODO: Optionally reply with status
                }
            }
            Behaviors.same()
        }
    }

    private fun handleSendOrder(
        msg: FixGatewayActor.Command.SendOrder,
        log: Logger,
        connected: Boolean,
        mySessionId: SessionID?,
        clOrdIdMappingService: ClOrdIdMappingService
    ) {
        val side = when (msg.order.side) {
            OrderSide.BUY -> Side.BUY
            OrderSide.SELL -> Side.SELL
        }
        
        // Only LIMIT orders require price
        val price: Double? = if (msg.order.orderType == com.betfanatics.exchange.order.actor.common.OrderType.LIMIT) {
            msg.order.price?.toDouble() ?: run {
                log.error("LIMIT order requires price")
                msg.replyTo.tell(FixGatewayActor.OrderRejected(msg.orderId, "LIMIT order requires price"))
                return
            }
        } else {
            null // MARKET orders don't need price
        }
        if (connected) {
            try {
                val session = Session.lookupSession(mySessionId)
                if (session != null) {
                    // Get the ClOrdID from Redis (should already be generated in OrderController)
                    val clOrderId = clOrdIdMappingService.getClOrdIdByOrderId(msg.orderId) ?: run {
                        log.error("No ClOrdID mapping found for orderId={}", msg.orderId)
                        msg.replyTo.tell(FixGatewayActor.OrderRejected(msg.orderId, "No ClOrdID mapping found"))
                        return
                    }
                    val symbol = msg.order.symbol // This should be the Kalshi market ticker
                    val orderQty = msg.order.quantity.toDouble()
                    
                    // Map order type correctly - using char values to match FIX spec
                    val ordType = when (msg.order.orderType) {
                        com.betfanatics.exchange.order.actor.common.OrderType.MARKET -> OrdType.MARKET  // '1'
                        com.betfanatics.exchange.order.actor.common.OrderType.LIMIT -> OrdType.LIMIT    // '2'
                    }
                    
                    // Map time in force correctly - using char values to match FIX spec
                    val timeInForce = when (msg.order.timeInForce) {
                        com.betfanatics.exchange.order.actor.common.TimeInForce.GTC -> TimeInForce.GOOD_TILL_CANCEL     // '1'
                        com.betfanatics.exchange.order.actor.common.TimeInForce.IOC -> TimeInForce.IMMEDIATE_OR_CANCEL // '3'
                        com.betfanatics.exchange.order.actor.common.TimeInForce.FOK -> TimeInForce.FILL_OR_KILL        // '4'
                    }

                    // Create NewOrderSingle with proper field order
                    val order = NewOrderSingle()
                    
                    // Set fields in the order they appear in the JSON example
                    order.set(ClOrdID(clOrderId))                    // Tag 11
                    order.set(OrderQty(orderQty))                     // Tag 38
                    order.set(OrdType(ordType))                       // Tag 40
                    
                    // Only set price for LIMIT orders
                    if (msg.order.orderType == com.betfanatics.exchange.order.actor.common.OrderType.LIMIT) {
                        price?.let { order.set(Price(it)) }           // Tag 44
                    }
                    
                    order.set(Side(side))                             // Tag 54
                    order.set(Symbol(symbol))                         // Tag 55
                    order.set(TimeInForce(timeInForce))              // Tag 59
                    order.set(TransactTime())                         // Tag 60 - will use current time
                    
                    // Add Parties repeating group
                    order.setInt(NoPartyIDs.FIELD, 1)                // Tag 453
                    val partyGroup = NewOrderSingle.NoPartyIDs()
                    // TODO accountID to config
                    partyGroup.set(PartyID("b0f5a944-8dbd-46ad-a48b-f82e29f57599_${msg.order.userId}")) // Tag 448
                    partyGroup.set(PartyRole(24))                    // Tag 452 - 24 = customer account
                    order.addGroup(partyGroup)

                    log.info("Outgoing NewOrderSingle FIX string: {}", order.toString().replace('\u0001', '|'))

                    val sent = Session.sendToTarget(order, mySessionId)
                    if (sent) {
                        log.info("NewOrderSingle sent: {}", order)
                        msg.replyTo.tell(FixGatewayActor.OrderAccepted(msg.orderId, Instant.now()))
                    } else {
                        log.warn("Failed to send NewOrderSingle")
                        msg.replyTo.tell(FixGatewayActor.OrderRejected(msg.orderId, "Failed to send NewOrderSingle"))
                    }
                } else {
                    log.warn("No active FIX session found to send order.")
                    msg.replyTo.tell(FixGatewayActor.OrderRejected(msg.orderId, "No active FIX session found to send order."))
                }
            } catch (e: Exception) {
                log.error("Error sending NewOrderSingle: {}", e.message)
                msg.replyTo.tell(FixGatewayActor.OrderRejected(msg.orderId, "Exception: "+e.message))
            }
        } else {
            log.warn("Cannot send order, not connected to FIX session.")
            msg.replyTo.tell(FixGatewayActor.OrderRejected(msg.orderId, "Not connected to FIX session."))
        }
    }
    
    private fun handleCancelOrder(
        msg: FixGatewayActor.Command.CancelOrder,
        log: Logger,
        connected: Boolean,
        mySessionId: SessionID?
    ) {
        if (connected) {
            try {
                val session = Session.lookupSession(mySessionId)
                if (session != null) {
                    // Create OrderCancelRequest
                    val cancelRequest = OrderCancelRequest(
                        OrigClOrdID(msg.origClOrdId),
                        ClOrdID(msg.cancelClOrdId),
                        Side(Side.BUY), // Side is required but not used for cancels in most systems
                        TransactTime()
                    )
                    
                    // Add required fields
                    cancelRequest.set(OrderID("UNKNOWN")) // We don't have the exchange OrderID
                    
                    // Add user party information
                    val partyGroup = Group(453, 448)
                    // TODO accountID to config
                    partyGroup.setString(448, "b0f5a944-8dbd-46ad-a48b-f82e29f57599_${msg.userId}")
                    partyGroup.setInt(452, 24) // 24 = customer account
                    cancelRequest.addGroup(partyGroup)
                    
                    log.info("Outgoing OrderCancelRequest FIX string: {}", cancelRequest.toString().replace('\u0001', '|'))
                    
                    val sent = Session.sendToTarget(cancelRequest, mySessionId)
                    if (sent) {
                        log.info("OrderCancelRequest sent: betOrderId={} cancelClOrdId={} origClOrdId={}", 
                            msg.betOrderId, msg.cancelClOrdId, msg.origClOrdId)
                        msg.replyTo.tell(FixGatewayActor.OrderCancelled(msg.betOrderId, "Cancel request sent"))
                    } else {
                        log.warn("Failed to send OrderCancelRequest for betOrderId={}", msg.betOrderId)
                        msg.replyTo.tell(FixGatewayActor.OrderRejected(msg.betOrderId, "Failed to send OrderCancelRequest"))
                    }
                } else {
                    log.warn("No active FIX session found to send cancel request.")
                    msg.replyTo.tell(FixGatewayActor.OrderRejected(msg.betOrderId, "No active FIX session found"))
                }
            } catch (e: Exception) {
                log.error("Error sending OrderCancelRequest: {}", e.message)
                msg.replyTo.tell(FixGatewayActor.OrderRejected(msg.betOrderId, "Exception: " + e.message))
            }
        } else {
            log.warn("Cannot send cancel order, not connected to FIX session.")
            msg.replyTo.tell(FixGatewayActor.OrderRejected(msg.betOrderId, "Not connected to FIX session."))
        }
    }
}