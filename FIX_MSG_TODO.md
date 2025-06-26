### FIX message plan

2. Understand the kalshi FIX pdf on FIX
2. Verify our kashi-fix-api objects for NewOrder, CancelOrder and ModifyOrder have all necessary fields to support trading on FIX with Kakshi
3. Propose changes
4. Make changes to send FIX orders to the mock server
5. We want a new market called FIX_TEST in the mock server, created by the mock server.
6. We will send orders to FIX_TEST.  The mock server will automatically fill them under certain conditions
   7. First, if an order is for No at 50 cents or greater, the mock server should always fill it instantly
   8. Second if a YES order is for 99 cents, to buy, the mock server should always fill it instantly
9. The REST API must take all combinations of Yes/No with Buy and Sell.  The orders on FIX are always
   10. Converted to YES, buy and sell, using the YesNoConverter.  
   11. The YesNoConverter should be either duplciated or moved to a common code folder.
   11. Third, if a No is less than 20 cents
       12. The REST API needs to know if the new order is IOC
       13. The initial set of integration tests can hit the REST API, sending IOC
       14. The mock server on seeing IOC and not matching, should reject the order
   15. Fourth, if we see a limit order for FIX_TEST, we need similar cases for crossing the book.
   16. these should be hardcoded in the mock server
   17. If a limit order is for 55 cents on YES it should rest on the order book
   18. Then a No should be sent to take this out; this must not conflit with other test logic
       19. The no would need to be for 45 cents
       20. Then both Execution repors for fills must be sent according to the Kalshi FIX spec.
   21. REview this plan and think about other FIX test cases we should do to get the complete REST and FIX conversion and round trip 
   22. This needs to be fully tested from the Mock server.
   23.  Then lets discuss the plan.
   24. Then you can implement this entire plan.

## Code review and debuggin

1. After the coding, we should review what decisions you made and walk me through the code
2. Then tell me how to run it and review it myself.
3. Then we can move onto settlement logic and drop copy logic.

### Settlement
1. Add another FIX session to the mock to connect for "Market Settlement session"
2. This will mimic the Kalshi market settlement they discuss under MarketSettlement and the session they mention
3. The market settlement should make a specific status FIX report and push to a Kafka topic FIX-SETTLEMENT-<ENV>
   4. Where <ENV> is the FIX_ENV from the configuration
4.  Add REST to our API and use it in the mock server to mock the GetSettlements API on the Kalshi exchange

### Drop Copy Session
1. This only allows a three-hour lookback
2. Add another session for this to the mock server
3. Every hour, the system should request all messages from the past hour
4. The system should then compare this to what has been seen on the trading connection
5. This can be a DropCopyActor in the Pekko
6. This must alarm on any messages > 10 (configurable) seconds which have not been recorded by the primary FIX Trading session
7. These messages must also feed the ExecutionReport; 
   8. Only in the event they have not been seen by the primary FIX Trading session
8. 