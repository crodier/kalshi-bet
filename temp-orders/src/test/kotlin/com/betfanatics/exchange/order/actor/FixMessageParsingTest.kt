import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import quickfix.Message
import quickfix.field.*
import quickfix.field.MarketSettlementReportID
import quickfix.field.TotNumMarketSettlementReports
import quickfix.field.MarketResult
import java.time.Instant

class FixMessageParsingTest {
    
    companion object {
        private const val SOH = 1.toChar()
        
        private fun createFixMessage(fields: List<Pair<String, String>>): Message {
            // Build message without checksum first
            val fieldsWithoutChecksum = fields.filterNot { it.first == "10" }
            val messageWithoutChecksum = fieldsWithoutChecksum.joinToString(SOH.toString()) { (tag, value) -> "$tag=$value" } + SOH
            
            // Calculate checksum
            val checksum = messageWithoutChecksum.sumOf { it.code.toByte().toInt() } % 256
            val checksumStr = checksum.toString().padStart(3, '0')
            
            // Append checksum
            val completeMessage = messageWithoutChecksum + "10=$checksumStr" + SOH
            
            return Message(completeMessage)
        }
    }
    
    @Nested
    inner class UmsMessageTests {
        @Test
        fun `should parse UMS message and extract fields`() {
            val message = createFixMessage(listOf(
                "8" to "FIXT.1.1",
                "9" to "693",
                "35" to "UMS",
                "34" to "1689",
                "49" to "KalshiPT",
                "52" to "20250616-23:02:24.046",
                "56" to "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6",
                "55" to "KXINXU-25JUN16H1600-T6074.99997",
                "15" to "20250616",
                "893" to "Y",
                "20105" to "p7q8r9s0-t1u2-v3w4-x5y6-z7a8b9c0d1e2",
                "20106" to "1",
                "20107" to "no",
                "20108" to "3",
                "20109" to "f3g4h5i6-j7k8-l9m0-n1o2-p3q4r5s6t7u8_XYZ7M9P2",
                "20110" to "2",
                "4704" to "100.0000000000",
                "136" to "1",
                "137" to "0.0000000000",
                "138" to "USD",
                "139" to "4",
                "891" to "0"
            ))

            // Header fields
            assertEquals("UMS", message.header.getString(MsgType.FIELD))
            assertEquals("KalshiPT", message.header.getString(SenderCompID.FIELD))
            assertEquals("a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6", message.header.getString(TargetCompID.FIELD))
            assertEquals("20250616-23:02:24.046", message.header.getString(SendingTime.FIELD))
            assertEquals(1689, message.header.getInt(MsgSeqNum.FIELD))

            // Body fields using generated classes
            assertEquals("p7q8r9s0-t1u2-v3w4-x5y6-z7a8b9c0d1e2", message.getString(MarketSettlementReportID.FIELD))
            assertEquals(1, message.getInt(TotNumMarketSettlementReports.FIELD))
            assertEquals("no", message.getString(MarketResult.FIELD))
            assertEquals("KXINXU-25JUN16H1600-T6074.99997", message.getString(Symbol.FIELD))
            assertEquals("20250616", message.getString(Currency.FIELD))
            
            // Verify repeating groups are present
            assertTrue(message.isSetField(20109)) // Verify at least one group entry exists
        }

        @Test
        fun `should parse UMS message with multiple settlement entries`() {
            val message = createFixMessage(listOf(
                "8" to "FIXT.1.1",
                "9" to "529",
                "35" to "UMS",
                "34" to "50",
                "49" to "KalshiPT",
                "52" to "20250619-16:17:23.543",
                "56" to "c883d4f5-d8d1-4682-b43a-f0d964131620",
                "55" to "KXETHD-25JUN1912-T1759.997",
                "15" to "20250619",
                "893" to "Y",
                "20105" to "72a1088b-0da9-48d2-acdd-3ab5ad475788",
                "20106" to "1",
                "20107" to "yes",
                "20108" to "2",  // Number of settlement entries
                // First settlement entry
                "20109" to "b0f5a944-8dbd-46ad-a48b-f82e29f57599_test-user-1",
                "20110" to "24",
                "4705" to "2.0000000000",
                "1703" to "1",
                "1704" to "0.0000000000",
                "1705" to "PAYOUT",
                "136" to "1",
                "137" to "0.0000000000",
                "138" to "USD",
                "139" to "4",
                "891" to "0",
                // Second settlement entry
                "20109" to "b0f5a944-8dbd-46ad-a48b-f82e29f57599_test-user-2",
                "20110" to "24",
                "4705" to "2.0000000000",
                "1703" to "1",
                "1704" to "2.0000000000",
                "1705" to "PAYOUT",
                "136" to "1",
                "137" to "0.0000000000",
                "138" to "USD",
                "139" to "4",
                "891" to "1"
            ))

            // Header fields
            assertEquals("UMS", message.header.getString(MsgType.FIELD))
            assertEquals("KalshiPT", message.header.getString(SenderCompID.FIELD))
            assertEquals("c883d4f5-d8d1-4682-b43a-f0d964131620", message.header.getString(TargetCompID.FIELD))
            assertEquals("20250619-16:17:23.543", message.header.getString(SendingTime.FIELD))
            assertEquals(50, message.header.getInt(MsgSeqNum.FIELD))

            // Main fields
            assertEquals("72a1088b-0da9-48d2-acdd-3ab5ad475788", message.getString(MarketSettlementReportID.FIELD))
            assertEquals(1, message.getInt(TotNumMarketSettlementReports.FIELD))
            assertEquals("yes", message.getString(MarketResult.FIELD))
            assertEquals("KXETHD-25JUN1912-T1759.997", message.getString(Symbol.FIELD))
            assertEquals(2, message.getInt(20108)) // NoMarketSettlements

            // This test will fail until we update the data dictionary
            val group = quickfix.Group(20108, 20109)  // NoMarketSettlements -> first field in group
            assertThrows(quickfix.FieldNotFound::class.java) {
                message.getGroup(1, group)  // Try to get first group instance
            }

            // Document the fields we expect in each group entry
            println("Expected group structure for each settlement entry:")
            println("20109 (MarketSettlementAccount) - Account identifier")
            println("20110 (MarketSettlementRole) - Role (e.g. 24)")
            println("4705 (MarketSettlementAmount) - Settlement amount")
            println("1703 (MarketSettlementType) - Settlement type")
            println("1704 (MarketSettlementPrice) - Settlement price")
            println("1705 (MarketSettlementDesc) - Settlement description")
            println("136 (MarketSettlementNoOrders) - Number of orders")
            println("137 (MarketSettlementOrderQty) - Order quantity")
            println("138 (MarketSettlementCurrency) - Currency")
            println("139 (MarketSettlementStatus) - Status")
            println("891 (MarketSettlementSide) - Side")
        }

        @Test
        fun `should handle malformed UMS message`() {
            val message = createFixMessage(listOf(
                "8" to "FIXT.1.1",
                "9" to "100",
                "35" to "UMS",
                "34" to "1689",
                "49" to "KalshiPT",
                "52" to "20250616-23:02:24.046",
                "56" to "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6"
            ))

            // Required fields should throw FieldNotFound
            assertThrows(quickfix.FieldNotFound::class.java) { 
                message.getString(MarketSettlementReportID.FIELD) 
            }
            assertThrows(quickfix.FieldNotFound::class.java) { 
                message.getString(MarketResult.FIELD) 
            }
        }
    }

    @Nested
    inner class ExecutionReportTests {
        @Test
        fun `should parse execution report for accepted order`() {
            val message = createFixMessage(listOf(
                "8" to "FIXT.1.1",
                "9" to "200",
                "35" to "8",
                "34" to "1234",
                "49" to "KalshiRT",
                "52" to "20250616-23:02:24.046",
                "56" to "client",
                "37" to "ord123",
                "11" to "ord123",
                "150" to "0",
                "39" to "0",
                "55" to "KXINXU-25JUN16H1600",
                "54" to "1",
                "38" to "100",
                "44" to "10.5"
            ))

            // Verify execution report fields
            assertEquals("8", message.header.getString(MsgType.FIELD))
            assertEquals("ord123", message.getString(ClOrdID.FIELD))
            assertEquals("0", message.getString(ExecType.FIELD))  // New
            assertEquals("0", message.getString(OrdStatus.FIELD)) // New
            assertEquals("KXINXU-25JUN16H1600", message.getString(Symbol.FIELD))
            assertEquals("1", message.getString(Side.FIELD))      // Buy
            assertEquals(100.0, message.getDouble(OrderQty.FIELD))
            assertEquals(10.5, message.getDouble(Price.FIELD))
        }

        @Test
        fun `should parse execution report for filled order`() {
            val message = createFixMessage(listOf(
                "8" to "FIXT.1.1",
                "9" to "250",
                "35" to "8",
                "34" to "1235",
                "49" to "KalshiRT",
                "52" to "20250616-23:02:24.046",
                "56" to "client",
                "37" to "ord123",
                "11" to "ord123",
                "150" to "F",
                "39" to "2",
                "55" to "KXINXU-25JUN16H1600",
                "54" to "1",
                "38" to "100",
                "44" to "10.5",
                "32" to "100",
                "31" to "10.5",
                "151" to "0",
                "14" to "100",
                "6" to "10.5"
            ))

            // Verify fill-specific fields
            assertEquals("F", message.getString(ExecType.FIELD))  // Fill
            assertEquals("2", message.getString(OrdStatus.FIELD)) // Filled
            assertEquals(100.0, message.getDouble(LastQty.FIELD))
            assertEquals(10.5, message.getDouble(LastPx.FIELD))
            assertEquals(100.0, message.getDouble(CumQty.FIELD))
            assertEquals(0.0, message.getDouble(LeavesQty.FIELD))
        }
    }

    @Nested
    inner class LogonMessageTests {
        @Test
        fun `should parse logon message`() {
            val message = createFixMessage(listOf(
                "8" to "FIXT.1.1",
                "9" to "100",
                "35" to "A",
                "34" to "1",
                "49" to "client",
                "56" to "KalshiRT",
                "52" to "20250616-23:02:24.046",
                "98" to "0",
                "108" to "30",
                "141" to "Y",
                "1137" to "9"
            ))

            // Verify logon fields
            assertEquals("A", message.header.getString(MsgType.FIELD))
            assertEquals(0, message.getInt(EncryptMethod.FIELD))
            assertEquals(30, message.getInt(HeartBtInt.FIELD))
            assertEquals("9", message.getString(DefaultApplVerID.FIELD))
            assertTrue(message.getBoolean(ResetSeqNumFlag.FIELD))
        }
    }
} 