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

object FixGatewayActor {
    // Define the command protocol
    sealed class Command : SerializationMarker {
        object Connect : Command()
        object Disconnect : Command()
        data class SendOrder(val orderId: String, val order: OrderRequestDTO, val replyTo: ActorRef<Response>) : Command()
        object GetStatus : Command()
    }

    // Responses
    sealed interface Response
    data class OrderAccepted(val orderId: String, val timestamp: Instant) : Response
    data class OrderRejected(val orderId: String, val reason: String) : Response
    data class OrderCancelled(val orderId: String, val reason: String) : Response
    
    var mySessionId: SessionID? = null

    fun create(isMockMode: Boolean, settingsPath: String, orderActorResolver: OrderActorResolver, dataSource: DataSource): Behavior<Command> = Behaviors.setup { context ->
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

                        val settings = SessionSettings(resourceStream)
                        val storeFactory = JdbcStoreFactory(settings)
                        storeFactory.setDataSource(dataSource);
                        val logFactory = SLF4JLogFactory(settings)
                        val messageFactory = DefaultMessageFactory()
                        initiator = SocketInitiator(fixApp, storeFactory, settings, logFactory, messageFactory)
                        initiator?.start()
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
                    handleSendOrder(msg, log, connected, mySessionId)                
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
        mySessionId: SessionID?
    ) {
        val side = when (msg.order.side) {
            OrderSide.BUY -> Side.BUY
            OrderSide.SELL -> Side.SELL
        }
        val price: Double = msg.order.price?.toDouble() ?: run {
            log.error("Order price is null")
            msg.replyTo.tell(FixGatewayActor.OrderRejected(msg.orderId, "Order price is null"))
            return
        }
        if (connected) {
            try {
                val session = Session.lookupSession(mySessionId)
                if (session != null) {
                    val clOrderId = msg.orderId
                    val symbol = msg.order.symbol
                    val orderQty = msg.order.quantity.toDouble()
                    val ordType = OrdType.LIMIT // 2 = Limit
                    val timeInForce = TimeInForce.GOOD_TILL_CANCEL // 1 = GTC

                    val order = NewOrderSingle(
                        ClOrdID(clOrderId),
                        Side(side),
                        TransactTime(),
                        OrdType(ordType)
                    )
                    order.set(OrderQty(orderQty))
                    order.set(Symbol(symbol))
                    order.set(Price(price))
                    order.set(TimeInForce(timeInForce))

                    // Properly add the Parties repeating group
                    val partyGroup = Group(453, 448)
                    // TODO accountID to config
                    // before the underscore is our omnibus account ID
                    partyGroup.setString(448, "b0f5a944-8dbd-46ad-a48b-f82e29f57599_${msg.order.userId}") 
                    partyGroup.setInt(452, 24) // 24 = customer account
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
}