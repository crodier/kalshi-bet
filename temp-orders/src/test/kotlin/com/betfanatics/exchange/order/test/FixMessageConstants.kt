package com.betfanatics.exchange.order.test

/**
 * Expected FIX message constants for integration testing.
 * These messages are converted to pipe-delimited format for readability.
 */
object FixMessageConstants {
    
    /**
     * Expected NewOrderSingle message for a BUY LIMIT order.
     * Based on user-provided JSON example converted to FIX format.
     * 
     * Fields included:
     * - Tag 8: BeginString=FIXT.1.1
     * - Tag 9: BodyLength (calculated)
     * - Tag 35: MsgType=D (NewOrderSingle)
     * - Tag 34: MsgSeqNum (variable)
     * - Tag 49: SenderCompID=FBG
     * - Tag 56: TargetCompID=KALSHI
     * - Tag 52: SendingTime (variable)
     * - Tag 11: ClOrdID=FBG_test_order_123
     * - Tag 38: OrderQty=2
     * - Tag 40: OrdType=2 (LIMIT)
     * - Tag 44: Price=49
     * - Tag 54: Side=1 (BUY)
     * - Tag 55: Symbol=KXETHD-25JUN2311-T1509.99
     * - Tag 59: TimeInForce=1 (GTC)
     * - Tag 60: TransactTime (variable)
     * - Tag 453: NoPartyIDs=1
     * - Tag 448: PartyID=b0f5a944-8dbd-46ad-a48b-f82e29f57599_test-user
     * - Tag 452: PartyRole=24
     * - Tag 10: CheckSum (calculated)
     */
    const val EXPECTED_NEW_ORDER_SINGLE_BUY_LIMIT = "8=FIXT.1.1|9=LENGTH|35=D|34=SEQ|49=FBG|56=KALSHI|52=TIME|11=FBG_test_order_123|38=2|40=2|44=49|54=1|55=KXETHD-25JUN2311-T1509.99|59=1|60=TIME|453=1|448=b0f5a944-8dbd-46ad-a48b-f82e29f57599_test-user|452=24|10=CHECKSUM"
    
    /**
     * Expected NewOrderSingle message for a SELL LIMIT order.
     */
    const val EXPECTED_NEW_ORDER_SINGLE_SELL_LIMIT = "8=FIXT.1.1|9=LENGTH|35=D|34=SEQ|49=FBG|56=KALSHI|52=TIME|11=FBG_test_order_456|38=5|40=2|44=52.5|54=2|55=KXETHD-25JUN2311-T1509.99|59=3|60=TIME|453=1|448=b0f5a944-8dbd-46ad-a48b-f82e29f57599_test-user-2|452=24|10=CHECKSUM"
    
    /**
     * Expected NewOrderSingle message for a MARKET order.
     */
    const val EXPECTED_NEW_ORDER_SINGLE_MARKET = "8=FIXT.1.1|9=LENGTH|35=D|34=SEQ|49=FBG|56=KALSHI|52=TIME|11=FBG_market_order_789|38=1|40=1|54=1|55=KXETHD-25JUN2311-T1509.99|59=4|60=TIME|453=1|448=b0f5a944-8dbd-46ad-a48b-f82e29f57599_market-user|452=24|10=CHECKSUM"
    
    /**
     * Expected OrderCancelRequest message.
     * 
     * Fields included:
     * - Tag 35: MsgType=F (OrderCancelRequest)
     * - Tag 11: ClOrdID=FBG_test_order_123_C_1234567890
     * - Tag 41: OrigClOrdID=FBG_test_order_123 (or latest modify)
     * - Tag 37: OrderID=UNKNOWN (we don't have exchange order ID)
     * - Tag 54: Side=1 (required but not used)
     * - Tag 60: TransactTime (variable)
     * - Tag 453: NoPartyIDs=1
     * - Tag 448: PartyID=b0f5a944-8dbd-46ad-a48b-f82e29f57599_test-user
     * - Tag 452: PartyRole=24
     */
    const val EXPECTED_ORDER_CANCEL_REQUEST = "8=FIXT.1.1|9=LENGTH|35=F|34=SEQ|49=FBG|56=KALSHI|52=TIME|11=FBG_test_order_123_C_TIMESTAMP|41=FBG_test_order_123|37=UNKNOWN|54=1|60=TIME|453=1|448=b0f5a944-8dbd-46ad-a48b-f82e29f57599_test-user|452=24|10=CHECKSUM"
    
    /**
     * Expected OrderCancelRequest referencing a modify.
     */
    const val EXPECTED_ORDER_CANCEL_REQUEST_AFTER_MODIFY = "8=FIXT.1.1|9=LENGTH|35=F|34=SEQ|49=FBG|56=KALSHI|52=TIME|11=FBG_test_order_123_C_TIMESTAMP|41=FBG_test_order_123_M_EARLIERTIMESTAMP|37=UNKNOWN|54=1|60=TIME|453=1|448=b0f5a944-8dbd-46ad-a48b-f82e29f57599_test-user|452=24|10=CHECKSUM"
    
    /**
     * Expected Logon message (without signature for testing).
     */
    const val EXPECTED_LOGON_MESSAGE = "8=FIXT.1.1|9=LENGTH|35=A|34=1|49=FBG|56=KALSHI|52=TIME|98=0|108=30|1137=9|8013=N|10=CHECKSUM"
    
    /**
     * Sample ExecutionReport for new order acceptance.
     */
    const val SAMPLE_EXECUTION_REPORT_NEW = "8=FIXT.1.1|9=LENGTH|35=8|34=SEQ|49=KALSHI|56=FBG|52=TIME|11=FBG_test_order_123|37=KALSHI_ORDER_456|17=EXEC_001|150=0|39=0|55=KXETHD-25JUN2311-T1509.99|54=1|38=2|44=49|14=0|151=2|60=TIME|10=CHECKSUM"
    
    /**
     * Sample ExecutionReport for trade execution.
     */
    const val SAMPLE_EXECUTION_REPORT_TRADE = "8=FIXT.1.1|9=LENGTH|35=8|34=SEQ|49=KALSHI|56=FBG|52=TIME|11=FBG_test_order_123|37=KALSHI_ORDER_456|17=EXEC_002|150=F|39=1|55=KXETHD-25JUN2311-T1509.99|54=1|38=2|44=49|14=1|151=1|32=1|31=49|60=TIME|10=CHECKSUM"
    
    /**
     * Sample ExecutionReport for order rejection.
     */
    const val SAMPLE_EXECUTION_REPORT_REJECT = "8=FIXT.1.1|9=LENGTH|35=8|34=SEQ|49=KALSHI|56=FBG|52=TIME|11=FBG_test_order_123|17=EXEC_003|150=8|39=8|55=KXETHD-25JUN2311-T1509.99|54=1|38=2|44=49|14=0|151=0|58=Insufficient balance|60=TIME|10=CHECKSUM"
    
    /**
     * Sample ExecutionReport for order cancellation.
     */
    const val SAMPLE_EXECUTION_REPORT_CANCEL = "8=FIXT.1.1|9=LENGTH|35=8|34=SEQ|49=KALSHI|56=FBG|52=TIME|11=FBG_test_order_123_C_TIMESTAMP|41=FBG_test_order_123|37=KALSHI_ORDER_456|17=EXEC_004|150=4|39=4|55=KXETHD-25JUN2311-T1509.99|54=1|14=0|151=0|60=TIME|10=CHECKSUM"
    
    /**
     * Variable field patterns for matching dynamic content.
     */
    object Patterns {
        const val SEQUENCE_NUMBER = "\\d+"
        const val TIMESTAMP = "\\d{8}-\\d{2}:\\d{2}:\\d{2}\\.\\d{3}"
        const val CHECKSUM = "\\d{3}"
        const val BODY_LENGTH = "\\d+"
        const val CLORDI_WITH_TIMESTAMP = "FBG_[a-zA-Z0-9_]+_[CM]_\\d+"
        const val EXEC_ID = "[A-Z0-9_]+"
        const val ORDER_ID = "[A-Z0-9_]+"
        const val UUID_PATTERN = "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"
    }
    
    /**
     * Helper function to create a regex pattern from a FIX message template.
     * Replaces variable placeholders with regex patterns.
     */
    fun createRegexPattern(template: String): Regex {
        return template
            .replace("LENGTH", Patterns.BODY_LENGTH)
            .replace("SEQ", Patterns.SEQUENCE_NUMBER) 
            .replace("TIME", Patterns.TIMESTAMP)
            .replace("CHECKSUM", Patterns.CHECKSUM)
            .replace("TIMESTAMP", "\\d+")
            .replace("EARLIERTIMESTAMP", "\\d+")
            .replace("EXEC_\\d+", Patterns.EXEC_ID)
            .replace("KALSHI_ORDER_\\d+", Patterns.ORDER_ID)
            .replace("b0f5a944-8dbd-46ad-a48b-f82e29f57599", Patterns.UUID_PATTERN)
            .toRegex()
    }
    
    /**
     * Extract key fields from a FIX message string for verification.
     */
    fun extractFields(fixMessage: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        val parts = fixMessage.split("|")
        
        for (part in parts) {
            val (tag, value) = part.split("=", limit = 2)
            fields[tag] = value
        }
        
        return fields
    }
}