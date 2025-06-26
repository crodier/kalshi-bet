package com.betfanatics.exchange.order.actor

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.ActorRef
import com.betfanatics.exchange.order.actor.common.OrderRequestDTO
import com.betfanatics.exchange.order.actor.common.OrderActorResolver
import com.betfanatics.exchange.order.util.KalshiSignatureUtil
import quickfix.*
import quickfix.field.*
import quickfix.fix50sp2.*
import quickfix.field.MarketSettlementReportID
import quickfix.field.TotNumMarketSettlementReports
import quickfix.field.MarketResult
import quickfix.Message
import quickfix.SessionNotFound
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.charset.StandardCharsets
import java.security.PrivateKey
import java.time.Instant
import javax.sql.DataSource
import org.slf4j.Logger
import java.math.BigDecimal
import kotlin.collections.asSequence

// Base command protocol - only includes common operations
sealed class BaseFixCommand : SerializationMarker {
    object Connect : BaseFixCommand()
    object Disconnect : BaseFixCommand()
    object GetStatus : BaseFixCommand()
}

// Trading-specific commands extend the base
sealed class TradingFixCommand : BaseFixCommand() {
    data class SendOrder(val orderId: String, val order: OrderRequestDTO, val replyTo: ActorRef<TradingFixResponse>) : TradingFixCommand()
}

// Settlement-specific commands extend the base  
sealed class SettlementFixCommand : BaseFixCommand() {
    // Settlement gateway doesn't send orders, but might have other specific commands
    // data class ProcessSettlement(...) : SettlementFixCommand()
}

// Responses are specific to each gateway type
sealed interface TradingFixResponse
data class OrderAccepted(val orderId: String, val timestamp: Instant) : TradingFixResponse
data class OrderRejected(val orderId: String, val reason: String) : TradingFixResponse
data class OrderCancelled(val orderId: String, val reason: String) : TradingFixResponse

sealed interface SettlementFixResponse
// Settlement-specific responses would go here

// Abstract base class for shared FIX functionality - NO ORDER SENDING
abstract class BaseFixGatewayActor(
    protected val settingsPath: String,
    protected val sessionQualifier: String,
    protected val orderActorResolver: OrderActorResolver,
    protected val dataSource: DataSource,
    protected val privateKeyPath: String
) {
    protected var initiator: SocketInitiator? = null
    protected var connected = false
    protected var mySessionId: SessionID? = null
    protected lateinit var log: Logger
    
    // Shared private key loading
    protected fun loadPrivateKey(): PrivateKey? {
        return try {
            log.info("Loading private key from {}", privateKeyPath)
            val privateKeyPem = String(Files.readAllBytes(Paths.get(privateKeyPath)), StandardCharsets.UTF_8)
            KalshiSignatureUtil.loadPrivateKey(privateKeyPem)
        } catch (e: Exception) {
            log.error("Could not load private key: {}", e.message)
            null
        }
    }
    
    // Shared FIX application creation with customization hooks
    protected fun createFixApplication(privateKey: PrivateKey?): Application {
        return object : Application {
            override fun onCreate(sessionId: SessionID?) {
                log.warn("QuickFIX/J session created: {}", sessionId)
                onSessionCreated(sessionId)
            }
            
            override fun onLogon(sessionId: SessionID?) {
                connected = true
                mySessionId = sessionId
                log.warn("QuickFIX/J session logged on: {}", sessionId)
                onSessionLoggedOn(sessionId)
            }
            
            override fun onLogout(sessionId: SessionID?) {
                connected = false
                log.warn("QuickFIX/J session logged out: {}", sessionId)
                onSessionLoggedOut(sessionId)
            }
            
            override fun toAdmin(message: Message, sessionId: SessionID) {
                handleToAdmin(message, sessionId, privateKey)
            }
            
            override fun toApp(message: Message, sessionId: SessionID) {
                log.info("toApp: [{}] [{}]", message, sessionId)
                handleToApp(message, sessionId)
            }
            
            override fun fromAdmin(message: Message, sessionId: SessionID) {
                handleFromAdmin(message, sessionId)
            }
            
            override fun fromApp(message: Message, sessionId: SessionID) {
                log.info("fromApp: [{}] [{}]", message, sessionId)
                handleFromApp(message, sessionId)
            }
        }
    }
    
    // Shared logon message handling
    private fun handleToAdmin(message: Message, sessionId: SessionID, privateKey: PrivateKey?) {
        try {
            if (MsgType.LOGON == message.header.getString(MsgType.FIELD)) {
                val sendingTime = message.header.getString(SendingTime.FIELD)
                val msgType = message.header.getString(MsgType.FIELD)
                val msgSeqNum = message.header.getString(MsgSeqNum.FIELD)
                val senderCompID = message.header.getString(SenderCompID.FIELD)
                val targetCompID = message.header.getString(TargetCompID.FIELD)
                val soh = "\u0001"
                val preHashString = listOf(sendingTime, msgType, msgSeqNum, senderCompID, targetCompID).joinToString(soh)
                log.info("PreHashString: [{}]", preHashString)
                
                val rawDataValue = privateKey?.let {
                    KalshiSignatureUtil.generateSignature(sendingTime, msgType, msgSeqNum, senderCompID, targetCompID, it)
                } ?: ""
                log.info("Generated RawData signature: {}", rawDataValue)
                
                message.setInt(EncryptMethod.FIELD, 0)
                message.setInt(RawDataLength.FIELD, rawDataValue.length)
                message.setString(RawData.FIELD, rawDataValue)
                message.setString(DefaultApplVerID.FIELD, "9")
                message.setString(8013, "N")
                
                customizeLogonMessage(message, sessionId)
                
                log.info("Logon message after setting fields: {}", message)
                log.info("Outgoing FIX string: {}", message.toString().replace('\u0001', '|'))
            }
        } catch (e: Exception) {
            log.error("Error setting logon fields: {}", e.message)
        }
    }
    
    // Shared admin message handling
    private fun handleFromAdmin(message: Message, sessionId: SessionID) {
        try {
                val msgType = message.header.getString(MsgType.FIELD)
                val seqNum = message.header.getInt(MsgSeqNum.FIELD)
                val possDupFlag = if (message.header.isSetField(PossDupFlag.FIELD)) 
                    message.header.getBoolean(PossDupFlag.FIELD) else false
                
                when (msgType) {
                    MsgType.HEARTBEAT -> {
                        log.info("Heartbeat received - SeqNum: {}, PossDup: {}", seqNum, possDupFlag)
                    }
                    MsgType.RESEND_REQUEST -> {
                        log.error("Received resend request from exchange: {}", message)
                    }
                    MsgType.SEQUENCE_RESET -> {
                        log.error("Received sequence reset: {}", message)
                    }
                    MsgType.TEST_REQUEST -> {
                        // tests are ok, people send them as another kind of heartbeat
                        log.info("Test request received - SeqNum: {}, PossDup: {}", seqNum, possDupFlag)
                    }
                    MsgType.REJECT -> {
                        // rejects are generally very bad sign
                        log.error("Reject message received: {}", message)
                    }
                    MsgType.LOGOUT -> {
                        // logout can only happen one time a day, 2:00 AM ET
                        log.warn("Logout message received: {}", message)
                    }
                    else -> {
                        // these are bad; we should know them all
                        log.error("Unknown Admin message received - Type: {}, SeqNum: {}, PossDup: {}", msgType, seqNum, possDupFlag)
                    }
                }
                
                customHandleFromAdmin(message, sessionId, msgType, seqNum, possDupFlag)

        } catch (e: Exception) {
            log.error("Error processing admin message: {}", e.message)
        }
    }
    
    // Shared connection handling
    protected fun handleConnect(): Boolean {
        if (initiator == null) {
            log.info("Received Connect command for session qualifier: {}", sessionQualifier)

            val resourceStream = this::class.java.classLoader.getResourceAsStream(settingsPath)
            requireNotNull(resourceStream) { "$settingsPath not found on classpath" }

            val allSettings = SessionSettings(resourceStream)

            val storeFactory = JdbcStoreFactory(allSettings)
            storeFactory.setDataSource(dataSource)
            val logFactory = SLF4JLogFactory(allSettings)
            val messageFactory = DefaultMessageFactory()

            val privateKey = loadPrivateKey()
            val fixApp = createFixApplication(privateKey)

            initiator = SocketInitiator(fixApp, storeFactory, allSettings, logFactory, messageFactory)
            initiator?.start()
            return true
        } else {
            log.info("Connect command received, but initiator is already running.")
            return false
        }
    }
    
    // Shared disconnect handling
    protected fun handleDisconnect() {
        log.info("Received Disconnect command. Stopping QuickFIX/J initiator...")
        initiator?.stop()
        initiator = null
    }
    
    // Delegated base command handling
    protected fun handleBaseCommand(cmd: BaseFixCommand): Boolean {
        return when (cmd) {
            is BaseFixCommand.Connect -> {
                handleConnect()
                true
            }
            is BaseFixCommand.Disconnect -> {
                handleDisconnect()
                true
            }
            is BaseFixCommand.GetStatus -> {
                log.info("FixGateway status - Connected: {}", connected)
                true
            }
            else -> false
        }
    }
    
    // Template method for creating behavior with delegation
    protected inline fun <reified T : BaseFixCommand> createDelegatedBehavior(
        crossinline handleSpecificCommand: (T) -> Unit
    ): Behavior<T> = Behaviors.setup { context ->
        log = context.log
        log.info("${this::class.simpleName} started with settingsPath: {}", settingsPath)
        
        context.self.tell(BaseFixCommand.Connect as T)
        
        Behaviors.receiveMessage { msg ->
            if (handleBaseCommand(msg)) {
                Behaviors.same()
            } else {
                handleSpecificCommand(msg)
                Behaviors.same()
            }
        }
    }
    
    // Extension points for subclasses
    protected open fun onSessionCreated(sessionId: SessionID?) {}
    protected open fun onSessionLoggedOn(sessionId: SessionID?) {}
    protected open fun onSessionLoggedOut(sessionId: SessionID?) {}
    protected open fun customizeLogonMessage(message: Message, sessionId: SessionID) {}
    protected open fun handleToApp(message: Message, sessionId: SessionID) {}
    protected open fun handleFromApp(message: Message, sessionId: SessionID) {}
    protected open fun customHandleFromAdmin(message: Message, sessionId: SessionID, msgType: String, seqNum: Int, possDupFlag: Boolean) {}
}

// Trading-specific implementation with order sending capabilities
class TradingFixGatewayActor(
    settingsPath: String,
    orderActorResolver: OrderActorResolver,
    dataSource: DataSource,
    private val accountIdPrefix: String = "b0f5a944-8dbd-46ad-a48b-f82e29f57599"
) : BaseFixGatewayActor(
    settingsPath,
    "KalshiRT", // Trading session qualifier
    orderActorResolver,
    dataSource,
    "kalshi-trading-fix.key"
) {
    
    companion object {
        fun create(
            settingsPath: String,
            orderActorResolver: OrderActorResolver,
            dataSource: DataSource
        ): Behavior<TradingFixCommand> {
            return TradingFixGatewayActor(settingsPath, orderActorResolver, dataSource).createBehavior()
        }
    }
    
    fun createBehavior(): Behavior<TradingFixCommand> = createDelegatedBehavior { msg ->
        when (msg) {
            is TradingFixCommand.SendOrder -> {
                handleSendOrder(msg)
            }
        }
    }
    
    // Order sending logic - only in trading gateway
    private fun handleSendOrder(msg: TradingFixCommand.SendOrder): Boolean {
        if (!connected || mySessionId == null) {
            log.warn("Cannot send order, not connected to FIX session.")
            msg.replyTo.tell(OrderRejected(msg.orderId, "Not connected to FIX session."))
            return false
        }
        
        try {
            val session = Session.lookupSession(mySessionId)
            if (session != null) {
                val order = buildNewOrderSingle(msg)
                
                log.info("Outgoing NewOrderSingle FIX string: {}", order.toString().replace('\u0001', '|'))
                
                val sent = Session.sendToTarget(order, mySessionId)
                if (sent) {
                    log.info("NewOrderSingle sent: {}", order)
                    msg.replyTo.tell(OrderAccepted(msg.orderId, Instant.now()))
                    return true
                } else {
                    log.error("Failed to send NewOrderSingle")
                    msg.replyTo.tell(OrderRejected(msg.orderId, "Failed to send NewOrderSingle"))
                    return false
                }
            } else {
                // TODO:  Error or above should go to a system; where then count > X (probably any), sends pagerDuty.
                log.error("No active FIX session found to send order.")
                msg.replyTo.tell(OrderRejected(msg.orderId, "No active FIX session found to send order."))
                return false
            }
        } catch (e: Exception) {
            log.error("Error sending NewOrderSingle: {}", e.message)
            msg.replyTo.tell(OrderRejected(msg.orderId, "Exception: ${e.message}"))
            return false
        }
    }
    
    // Order building logic - only in trading gateway
    private fun buildNewOrderSingle(msg: TradingFixCommand.SendOrder): NewOrderSingle {
        val side = when (msg.order.side) {
            com.betfanatics.exchange.order.actor.common.OrderSide.BUY -> Side.BUY
            com.betfanatics.exchange.order.actor.common.OrderSide.SELL -> Side.SELL
        }
        val price = msg.order.price?.toDouble() ?: throw IllegalArgumentException("Order price is null")
        
        val order = NewOrderSingle(
            ClOrdID(msg.orderId),
            Side(side),
            TransactTime(),
            OrdType(OrdType.LIMIT)
        )
        order.set(OrderQty(msg.order.quantity.toDouble()))
        order.set(Symbol(msg.order.symbol))
        order.set(Price(price))
        order.set(TimeInForce(TimeInForce.GOOD_TILL_CANCEL))
        
        // Add party group
        val partyGroup = Group(453, 448)
        partyGroup.setString(448, "${accountIdPrefix}_${msg.order.userId}")
        partyGroup.setInt(452, 24)
        order.addGroup(partyGroup)
        
        return order
    }
    
    override fun handleFromApp(message: Message, sessionId: SessionID) {
        if (message.header.getString(MsgType.FIELD) == MsgType.EXECUTION_REPORT) {
            handleExecutionReport(message)
        }
    }
    
    private fun handleExecutionReport(message: Message) {
        try {
            val clOrdId = message.getString(ClOrdID.FIELD)
            val execType = message.getString(ExecType.FIELD)
            val ordStatus = message.getString(OrdStatus.FIELD)
            val cumQty = message.getDouble(CumQty.FIELD)
            val leavesQty = message.getDouble(LeavesQty.FIELD)
            val lastPx = if (message.isSetField(LastPx.FIELD)) message.getDouble(LastPx.FIELD) else null
            val lastQty = if (message.isSetField(LastQty.FIELD)) message.getDouble(LastQty.FIELD) else null
            val symbol = if (message.isSetField(Symbol.FIELD)) message.getString(Symbol.FIELD) else null
            val side = if (message.isSetField(Side.FIELD)) message.getString(Side.FIELD) else null

            log.info("[TRADING ExecutionReport] ClOrdID={}, ExecType={}, OrdStatus={}, Symbol={}", 
                clOrdId, execType, ordStatus, symbol)

            val orderId = clOrdId
            val orderRef = orderActorResolver(orderId)
            
            when (ordStatus) {
                "0" -> {
                    log.info("[TRADING] Order accepted for orderId={}", orderId)
                    orderRef.tell(OrderActor.FixOrderAccepted(Instant.now()))
                }
                "1", "2" -> {
                    if ((execType == ExecType.TRADE.toString() || execType == "F") && lastQty != null && lastQty > 0.0) {
                        log.info("[TRADING] Trade execution for orderId={}, filledQty={}", orderId, lastQty)
                        orderRef.tell(OrderActor.OrderFillUpdate(BigDecimal.valueOf(lastQty), Instant.now()))
                    }
                }
                "8" -> {
                    log.info("[TRADING] Order rejected for orderId={}", orderId)
                    orderRef.tell(OrderActor.FixOrderRejected("Order rejected by exchange"))
                }
                "4" -> {
                    log.info("[TRADING] Order canceled for orderId={}", orderId)
                    orderRef.tell(OrderActor.FixOrderCancelled("Order canceled by exchange"))
                }
            }
        } catch (e: Exception) {
            log.error("Error parsing ExecutionReport: {}", e.message)
        }
    }
}

// Settlement-specific implementation - no order sending
class SettlementFixGatewayActor(
    settingsPath: String,
    orderActorResolver: OrderActorResolver,
    dataSource: DataSource
) : BaseFixGatewayActor(
    settingsPath,
    "KalshiPT", // Settlement session qualifier
    orderActorResolver,
    dataSource,
    "kalshi-settlement-fix.key"
) {
    
    companion object {
        fun create(
            settingsPath: String,
            orderActorResolver: OrderActorResolver,
            dataSource: DataSource
        ): Behavior<SettlementFixCommand> {
            return SettlementFixGatewayActor(settingsPath, orderActorResolver, dataSource).createBehavior()
        }
    }
    
    fun createBehavior(): Behavior<SettlementFixCommand> = createDelegatedBehavior { msg ->
        // Settlement gateway has no specific commands yet
        // when (msg) {
        //     is SettlementFixCommand.ProcessSettlement -> {
        //         handleProcessSettlement(msg)
        //     }
        // }
    }
    
    override fun handleFromApp(message: Message, sessionId: SessionID) {
        if (message.header.getString(MsgType.FIELD) == "UMS") {
            handleSettlementMessage(message)
        }
    }
    
    private fun handleSettlementMessage(message: Message) {
        try {
            log.info("[SETTLEMENT UMS] {}", message)
            val id = message.getString(MarketSettlementReportID.FIELD)
            val symbol = message.getString(Symbol.FIELD)
            val totNumReports = message.getInt(TotNumMarketSettlementReports.FIELD)
            val marketResult = message.getString(MarketResult.FIELD)
            
            log.info("[SETTLEMENT] Received settlement report: id={}, symbol={}, totNumReports={}, result={}", 
                    id, symbol, totNumReports, marketResult)
            
            // TODO: Implement settlement logic
            // - get the orderId from the ClOrdID
            // - build a new ClosePositionProcessManager
            // - have it run a workflow to:
            //   - mark the orderactor as settled
            //   - send a message to the kalshi wallet actor to debit the user's account
            //   - send a message to the fbg wallet actor to credit the user's account
            
        } catch (e: Exception) {
            log.error("Error parsing settlement message: {}", e.message)
        }
    }
    
    override fun customizeLogonMessage(message: Message, sessionId: SessionID) {
        // Settlement-specific logon customizations
        message.setString(8014, "Y") // Enable settlement messages
    }
}