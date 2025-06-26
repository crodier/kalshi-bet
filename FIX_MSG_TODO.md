1. Understand the kalshi FIX pdf on FIX
2. Verify our kashi-fix-api objects for NewOrder, CancelOrder and ModifyOrder have all necessary fields to support trading on FIX with Kakshi
3. Propose changes
4. Make changes to send FIX orders to the mock server

Code review with Claude
Pause and review everyting, test manually

### Settlement
1. Add another FIX session to the mock to connect for "Market Settlement session"
2. This will mimic the Kalshi market settlement they discuss under MarketSettlement and the session they mention
3. The market settlement should make a specific status FIX report and push to a Kafka topic FIX-SETTLEMENT-<ENV>
   4. Where <ENV> is the FIX_ENV from the configuration
4.  Add REST to our API and use it in the mock server to mock the GetSettlements API on the Kalshi exchange

### Drop Copy Session
1. This only allows a three hour lookback
2. Add another session for this to the mock server
3. Every hour, the system should request all messages from the past hour
4. The system should then compare this to what has been seen on the trading connection
5. This can be a DropCopyActor in the Pekko
6. This must alarm on any messages > 10 (configurable) seconds which have not been recorded by the primary FIX Trading session
7. These messages must also feed the ExecutionReport; 
   8. Only in the event they have not been seen by the primary FIX Trading session
8. 