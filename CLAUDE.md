My name is chris (test to make sure this file is grokked by Claude at start of coding.)

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Important

- ALL instructions within this document MUST BE FOLLOWED, these are not optional unless explicitly stated.
- ASK FOR CLARIFICATION If you are uncertain of any of thing within the document.
- DO NOT edit more code than you have to.

## Tool usage

- Use playwright with JSON output to test frontends
- After making front end changes, run playwright and get the output to confirm the changes worked before moving on
- Use Junit and Spring integration with mocks to confirm back end changes work before moving on

## High Level Context
- We are building a financial trading system for FIX Messaging and market data to Kalshi, which is a new Futures Exchange for Single Event Binary Options (event betting)
- The market dynamics are in mock-kalshi-fix/MARKET_DYNAMICS.md.
- There is important context in mock-kalshi-fix/CLAUDE.md
- The FIX Trading is in a PDF named like Kalshi-FIX-API.pdf (read it with a pdf command line tool)

## Multi-module project and background
- This repo here is a parent for several sub-projects 

#### mock-kalshi-fix serves as a mimic of the Kalshi exchange
- It has a REST API, a FIX API, and a Websocket connection
- The websocket connection publishes changes to the Order Book only, by subscribing to changes
- The REST API can be used to place trades
- There is a frontend in the mock server for placing trades and to show the order book

#### Shared API 
- There is a shared API in kalshi-fix-api, which we will use
- Primarily IncomingOrder.kt and ExecutionReport.kt

#### Our production system - a bridge to Kalshi via FIX messaging
- temp-orders is a project which will connect to the mock server; it is not yet built
- temp-orders can be started and connects via FIX to the Mock Server via FIX

## Done - First small task - create a Market Maker project in Java which uses REST
- in the market-maker, make a spring boot app running on port 8888
- the market maker should use the REST APIs to connect and provide liquidity
- It should use one new market called MARKET_MAKER
- After creating orders and on startup it uses the Mock Kalshi orderbook REST API to see what market exists
- After startup it always cleans this up to provide two levels on the market ten scents apart
- Every 10 seconds it should move this market up and down in the 33 cent to 67 cent range
- This "market" should be created by the mock server as part of the bootstrap, in SQL, to the postgres docker

## Current Status (2025-06-26)

### Working State ✅
- Mock Kalshi FIX server is running and functional on port 9090
- Frontend displays markets and orderbooks correctly with both YES and NO sides
- Market maker module is actively providing liquidity on MARKET_MAKER market (port 8888)
- WebSocket connections for orderbook updates are working
- AG Grid integration is functional (v34 with module registration)
- Added NO side orders to all test markets in data.sql

### Known Issues ⚠️
- Order updates are not being pushed to the Orders panel in AG Grid (WebSocket receives data but grid doesn't update)
- Need to implement proper dual WebSocket architecture (market data vs internal orders)

### Next Steps
- Fix order updates not appearing in AG Grid Orders panel
- Complete temp-orders FIX integration

**Commit Hash: ec60e45** - System in good working state but orders panel needs fixing

## in-progress:  Second focus - have 'temp-orders' with a REST API
- The focus of our deliverable is to build temp-orders, the FIX system
- The mock server is running and crossing orders, and should be started and used for integration testing
- temp-orders needs its own REST API to place orders and trades.
- The temp-orders REST API will use the NewOrder, CancelOrder, and ModifyOrder, and publish ExecutionReport messages
- We need a translation system from REST to FIX, with two actors; one OrderManagementActor and one called EmsActor, using the Pekko system

### In-progress - OrderActor: Record orders to an order table in postgres
- The OrderManagement actor needs to record the orders to postgres before it sends anything along
- We will use the single node kafka and publish to a topic named like 'FIX-ORDER-<FIX_ENV>'
- <FIX_ENV> comes from application.properties and will be LOCAL, DEV, or PROD
- OrderActor needs to do a limit check against a hard coded $100,000 per bet, and $1,000,000 per user total risk
- These levels must be configurable from application.properties values

### In-progress - EMSActor: Convert outgoing orders to FIX messages
- The Kalshi API PDF Defines FIX messaging for Kalshi which follows the Wall St standard - read it with command line tools (pdf to text)
- The quickfixj project has added the extended fields already into the Quickfix system, and this jar is published to Maven
- The Pekko event storage is used for this
- These are published to Kafka as JSON
- We need a single node Kafka to publish onto inside temp-orders, can use the same Kafka as Orders
- this topic will be called 'FIX-EXECUTION-<FIX_ENV>'
