package com.betfanatics.exchange.order.service

import com.betfanatics.exchange.order.actor.common.OrderRequestDTO
import com.betfanatics.exchange.order.metrics.FixMessageMetrics
import com.fbg.api.fix.domain.ExecutionReport
import com.fbg.api.fix.domain.IncomingOrder
import com.fbg.api.fix.domain.Instrument
import com.fbg.api.fix.enums.*
import com.fbg.api.common.BetDecimal
import com.fbg.api.common.BetLocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import quickfix.Message
import quickfix.field.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class ExecutionReportEnrichmentService(
    private val clOrdIdMappingService: ClOrdIdMappingService,
    private val fixErrorService: FixErrorService,
    private val fixMessageMetrics: FixMessageMetrics,
    private val fixClOrdIdGenerator: com.betfanatics.exchange.order.util.FixClOrdIdGenerator
) {
    
    private val log = LoggerFactory.getLogger(ExecutionReportEnrichmentService::class.java)
    
    // No longer needed - we'll use the shared ExecutionReport from kalshi-fix-api
    
    /**
     * Enrich an execution report with the original order data and convert to shared ExecutionReport type
     */
    fun enrichExecutionReport(message: Message): ExecutionReport? {
        try {
            // Extract ClOrdID (tag 11)
            val clOrdId = message.getString(ClOrdID.FIELD)
            
            // First try to extract betOrderId from the ClOrdID itself
            // This works for all our ClOrdID formats: new, modify, and cancel
            var betOrderId = fixClOrdIdGenerator.extractOrderId(clOrdId)
            
            // If extraction failed, try Redis lookup as fallback
            if (betOrderId == null) {
                betOrderId = clOrdIdMappingService.getOrderIdByClOrdId(clOrdId)
                if (betOrderId == null) {
                    val error = "No betOrderId mapping found for ClOrdID: $clOrdId"
                    log.error("EXECUTION_REPORT_MAPPING_ERROR: {}", error)
                    fixErrorService.reportGeneralError(null, error, mapOf(
                        "clOrdId" to clOrdId,
                        "errorType" to "MISSING_CLORDID_MAPPING"
                    ))
                    fixMessageMetrics.incrementFixError("missing_clordid_mapping")
                }
            }
            
            // Lookup original order data
            val originalOrder = betOrderId?.let { clOrdIdMappingService.getOrderData(it) }
            if (betOrderId != null && originalOrder == null) {
                log.warn("No order data found in Redis for betOrderId: {}", betOrderId)
            }
            
            // Extract execution report fields
            val execId = message.getString(ExecID.FIELD)
            val execType = message.getChar(ExecType.FIELD).toString()
            val ordStatus = message.getChar(OrdStatus.FIELD).toString()
            val cumQty = message.getDouble(CumQty.FIELD)
            val leavesQty = message.getDouble(LeavesQty.FIELD)
            
            // Optional fields
            val exchangeOrderId = try { message.getString(OrderID.FIELD) } catch (e: Exception) { null }
            val symbol = try { message.getString(Symbol.FIELD) } catch (e: Exception) { null }
            val side = try { message.getChar(Side.FIELD).toString() } catch (e: Exception) { null }
            val orderQty = try { message.getDouble(OrderQty.FIELD) } catch (e: Exception) { null }
            val price = try { message.getDouble(Price.FIELD) } catch (e: Exception) { null }
            val lastPx = try { message.getDouble(LastPx.FIELD) } catch (e: Exception) { null }
            val lastQty = try { message.getDouble(LastQty.FIELD) } catch (e: Exception) { null }
            val avgPx = try { message.getDouble(AvgPx.FIELD) } catch (e: Exception) { null }
            
            // Determine the type of order based on ClOrdID format
            val isNewOrder = fixClOrdIdGenerator.isNewOrderClOrdId(clOrdId)
            val isModifyOrder = fixClOrdIdGenerator.isModifyClOrdId(clOrdId)
            val isCancelOrder = fixClOrdIdGenerator.isCancelClOrdId(clOrdId)
            
            // Convert OrderRequestDTO to appropriate IncomingOrder type
            val newOrder: IncomingOrder.NewOrder? = if (isNewOrder && originalOrder != null) {
                IncomingOrder.NewOrder(
                    fbgOrderId = originalOrder.orderId,
                    createTimestamp = System.currentTimeMillis(),
                    shortUUID = originalOrder.orderId.take(8),
                    side = when (originalOrder.side) {
                        com.betfanatics.exchange.order.actor.common.OrderSide.BUY -> TradingSide.Buy
                        com.betfanatics.exchange.order.actor.common.OrderSide.SELL -> TradingSide.Sell
                    },
                    orderType = when (originalOrder.orderType) {
                        com.betfanatics.exchange.order.actor.common.OrderType.MARKET -> OrderType.Market
                        com.betfanatics.exchange.order.actor.common.OrderType.LIMIT -> OrderType.Limit
                    },
                    timeInForce = when (originalOrder.timeInForce) {
                        com.betfanatics.exchange.order.actor.common.TimeInForce.GTC -> TimeInForce.GoodTillCancel
                        com.betfanatics.exchange.order.actor.common.TimeInForce.IOC -> TimeInForce.ImmediateOrCancel
                        com.betfanatics.exchange.order.actor.common.TimeInForce.FOK -> TimeInForce.FillOrKill
                    },
                    quantity = originalOrder.quantity.toDouble(),
                    price = originalOrder.price?.toDouble(),
                    symbol = originalOrder.symbol,
                    clientOrderId = clOrdId
                )
            } else null
            
            val modifyOrder: IncomingOrder.ModifyOrder? = if (isModifyOrder && originalOrder != null) {
                // Extract OrigClOrdID for modify orders
                val origClOrdId = try { message.getString(OrigClOrdID.FIELD) } catch (e: Exception) { null }
                IncomingOrder.ModifyOrder(
                    fbgOrderId = originalOrder.orderId,
                    createTimestamp = System.currentTimeMillis(),
                    shortUUID = originalOrder.orderId.take(8),
                    originalClientOrderId = origClOrdId ?: "",
                    clientOrderId = clOrdId,
                    side = when (originalOrder.side) {
                        com.betfanatics.exchange.order.actor.common.OrderSide.BUY -> TradingSide.Buy
                        com.betfanatics.exchange.order.actor.common.OrderSide.SELL -> TradingSide.Sell
                    },
                    orderType = when (originalOrder.orderType) {
                        com.betfanatics.exchange.order.actor.common.OrderType.MARKET -> OrderType.Market
                        com.betfanatics.exchange.order.actor.common.OrderType.LIMIT -> OrderType.Limit
                    },
                    timeInForce = when (originalOrder.timeInForce) {
                        com.betfanatics.exchange.order.actor.common.TimeInForce.GTC -> TimeInForce.GoodTillCancel
                        com.betfanatics.exchange.order.actor.common.TimeInForce.IOC -> TimeInForce.ImmediateOrCancel
                        com.betfanatics.exchange.order.actor.common.TimeInForce.FOK -> TimeInForce.FillOrKill
                    },
                    quantity = originalOrder.quantity.toDouble(),
                    price = originalOrder.price?.toDouble(),
                    symbol = originalOrder.symbol
                )
            } else null
            
            val cancelOrder: IncomingOrder.CancelOrder? = if (isCancelOrder && originalOrder != null) {
                // Extract OrigClOrdID for cancel orders
                val origClOrdId = try { message.getString(OrigClOrdID.FIELD) } catch (e: Exception) { null }
                IncomingOrder.CancelOrder(
                    fbgOrderId = originalOrder.orderId,
                    createTimestamp = System.currentTimeMillis(),
                    shortUUID = originalOrder.orderId.take(8),
                    originalClientOrderId = origClOrdId ?: "",
                    clientOrderId = clOrdId,
                    side = when (originalOrder.side) {
                        com.betfanatics.exchange.order.actor.common.OrderSide.BUY -> TradingSide.Buy
                        com.betfanatics.exchange.order.actor.common.OrderSide.SELL -> TradingSide.Sell
                    },
                    symbol = originalOrder.symbol
                )
            } else null
            
            // Create Instrument
            val instrument = Instrument(
                symbol = symbol ?: "UNKNOWN",
                securityID = null,
                securityIDSource = null
            )
            
            // Map execution type
            val executionType = when (execType) {
                "0" -> ExecutionType.New
                "1" -> ExecutionType.PartialFill  
                "2" -> ExecutionType.Fill
                "3" -> ExecutionType.DoneForDay
                "4" -> ExecutionType.Canceled
                "5" -> ExecutionType.Replaced
                "6" -> ExecutionType.PendingCancel
                "7" -> ExecutionType.Stopped
                "8" -> ExecutionType.Rejected
                "9" -> ExecutionType.Suspended
                "A" -> ExecutionType.PendingNew
                "B" -> ExecutionType.Calculated
                "C" -> ExecutionType.Expired
                "D" -> ExecutionType.Restated
                "E" -> ExecutionType.PendingReplace
                "F" -> ExecutionType.Trade
                "G" -> ExecutionType.TradeCorrect
                "H" -> ExecutionType.TradeCancel
                "I" -> ExecutionType.OrderStatus
                else -> ExecutionType.New // Default
            }
            
            // Map order status
            val orderStatus = when (ordStatus) {
                "0" -> OrderStatus.New
                "1" -> OrderStatus.PartiallyFilled
                "2" -> OrderStatus.Filled
                "3" -> OrderStatus.DoneForDay
                "4" -> OrderStatus.Canceled
                "5" -> OrderStatus.Replaced
                "6" -> OrderStatus.PendingCancel
                "7" -> OrderStatus.Stopped
                "8" -> OrderStatus.Rejected
                "9" -> OrderStatus.Suspended
                "A" -> OrderStatus.PendingNew
                "B" -> OrderStatus.Calculated
                "C" -> OrderStatus.Expired
                "D" -> OrderStatus.AcceptedForBidding
                "E" -> OrderStatus.PendingReplace
                else -> OrderStatus.New // Default
            }
            
            // Map side
            val tradingSide = when (side) {
                "1" -> TradingSide.Buy
                "2" -> TradingSide.Sell
                else -> TradingSide.Buy // Default
            }
            
            // Map order type if available
            val ordType = originalOrder?.orderType?.let { type ->
                when (type) {
                    com.betfanatics.exchange.order.actor.common.OrderType.MARKET -> OrderType.Market
                    com.betfanatics.exchange.order.actor.common.OrderType.LIMIT -> OrderType.Limit
                }
            }
            
            // Map time in force if available
            val tif = originalOrder?.timeInForce?.let { tif ->
                when (tif) {
                    com.betfanatics.exchange.order.actor.common.TimeInForce.GTC -> TimeInForce.GoodTillCancel
                    com.betfanatics.exchange.order.actor.common.TimeInForce.IOC -> TimeInForce.ImmediateOrCancel
                    com.betfanatics.exchange.order.actor.common.TimeInForce.FOK -> TimeInForce.FillOrKill
                }
            }
            
            // Extract optional fields
            val origClOrdId = try { message.getString(OrigClOrdID.FIELD) } catch (e: Exception) { null }
            val ordRejReason = try { message.getString(OrdRejReason.FIELD) } catch (e: Exception) { null }
            val text = try { message.getString(Text.FIELD) } catch (e: Exception) { null }
            val transactTime = try {
                val timeStr = message.getString(TransactTime.FIELD)
                // Convert FIX timestamp to LocalDateTime
                BetLocalDateTime(LocalDateTime.now()) // TODO: Parse FIX timestamp properly
            } catch (e: Exception) { null }
            
            val enrichedReport = ExecutionReport(
                orderID = exchangeOrderId ?: "UNKNOWN",
                execID = execId,
                execType = executionType,
                ordStatus = orderStatus,
                side = tradingSide,
                leavesQty = BetDecimal(leavesQty.toString()),
                cumQty = BetDecimal(cumQty.toString()),
                betOrderId = betOrderId ?: "UNKNOWN",
                newOrder = newOrder,
                modifyOrder = modifyOrder,
                cancelOrder = cancelOrder,
                instrument = instrument,
                lastQty = lastQty?.let { BetDecimal(it.toString()) },
                lastPx = lastPx?.let { BetDecimal(it.toString()) },
                clOrdID = clOrdId,
                origClOrdID = origClOrdId,
                orderQty = orderQty?.let { BetDecimal(it.toString()) },
                ordType = ordType,
                price = price?.let { BetDecimal(it.toString()) },
                timeInForce = tif,
                avgPx = avgPx?.let { BetDecimal(it.toString()) },
                ordRejReason = ordRejReason,
                text = text,
                transactTime = transactTime
            )
            
            log.info("EXECUTION_REPORT_ENRICHED: clOrdId={} betOrderId={} exchangeOrderId={} execType={} ordStatus={} hasOriginalOrder={}",
                clOrdId, betOrderId, exchangeOrderId, execType, ordStatus, originalOrder != null)
            
            // Check if this is an acceptance of a modify order
            if (betOrderId != null && fixClOrdIdGenerator.isModifyClOrdId(clOrdId) && 
                (ordStatus == "5" || execType == "5")) { // 5 = REPLACED (order accepted and replaced)
                clOrdIdMappingService.updateLatestAcceptedModifyClOrdId(betOrderId, clOrdId)
                log.info("MODIFY_ORDER_ACCEPTED: Updated latest accepted modify for betOrderId={} to clOrdId={}",
                    betOrderId, clOrdId)
            }
            
            // Check if this is an acceptance of a cancel order
            if (betOrderId != null && fixClOrdIdGenerator.isCancelClOrdId(clOrdId) && 
                (ordStatus == "4" || execType == "4")) { // 4 = CANCELED
                clOrdIdMappingService.updateLatestAcceptedCancelClOrdId(betOrderId, clOrdId)
                log.info("CANCEL_ORDER_ACCEPTED: Updated latest accepted cancel for betOrderId={} to clOrdId={}",
                    betOrderId, clOrdId)
            }
            
            return enrichedReport
            
        } catch (e: Exception) {
            log.error("Failed to enrich execution report: {}", e.message, e)
            fixMessageMetrics.incrementFixError("execution_report_enrichment_failed")
            return null
        }
    }
    
    /**
     * Convert execution type code to human-readable string
     */
    fun getExecutionTypeDescription(execType: String): String {
        return when (execType) {
            "0" -> "NEW"
            "1" -> "PARTIAL_FILL"
            "2" -> "FILL"
            "3" -> "DONE_FOR_DAY"
            "4" -> "CANCELED"
            "5" -> "REPLACED"
            "6" -> "PENDING_CANCEL"
            "7" -> "STOPPED"
            "8" -> "REJECTED"
            "9" -> "SUSPENDED"
            "A" -> "PENDING_NEW"
            "B" -> "CALCULATED"
            "C" -> "EXPIRED"
            "D" -> "RESTATED"
            "E" -> "PENDING_REPLACE"
            "F" -> "TRADE"
            "G" -> "TRADE_CORRECT"
            "H" -> "TRADE_CANCEL"
            "I" -> "ORDER_STATUS"
            else -> "UNKNOWN"
        }
    }
    
    /**
     * Convert order status code to human-readable string
     */
    fun getOrderStatusDescription(ordStatus: String): String {
        return when (ordStatus) {
            "0" -> "NEW"
            "1" -> "PARTIALLY_FILLED"
            "2" -> "FILLED"
            "3" -> "DONE_FOR_DAY"
            "4" -> "CANCELED"
            "5" -> "REPLACED"
            "6" -> "PENDING_CANCEL"
            "7" -> "STOPPED"
            "8" -> "REJECTED"
            "9" -> "SUSPENDED"
            "A" -> "PENDING_NEW"
            "B" -> "CALCULATED"
            "C" -> "EXPIRED"
            "D" -> "ACCEPTED_FOR_BIDDING"
            "E" -> "PENDING_REPLACE"
            else -> "UNKNOWN"
        }
    }
}