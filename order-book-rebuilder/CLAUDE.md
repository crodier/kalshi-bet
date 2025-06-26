My name is chris (test to make sure this file is grokked by Claude at start of coding.)

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Important

- ALL instructions within this document MUST BE FOLLOWED, these are not optional unless explicitly stated.
- ASK FOR CLARIFICATION If you are uncertain of any of thing within the document.
- DO NOT edit more code than you have to.

## Read the parent folder ../CLAUDE.md for general details.

### This module

This module listens to the kafka which is part of the docker compose in the ../market-data-server

this market data server will be publishing on a topic which is defined there

In this project, we listen to Kafka, and build the order book for every market in memory
using concurrent data strucures.

CHM for the top level, and then more concurrent data structures for the order book.

THe order book will be read heavy and should not block on reads.

We can use copy on write to avoid blocking reads when we read from the order book, to avoid locking anything
on writes.

We should make an integration test which does multithreaded reads and writes and inform on the performance
per second with up to 1000 order books runnning and 1000 updates per second, as one final check.
But first do a very small performance test for concurrency.

Read the entire project and all CLAUDE.md files to get context about the effort.

Read the Kalshi website for the format of the websocket updates.

that is here:  https://trading-api.readme.io/reference/ws

Understand it deeply first.

You will be getting only the snapshots and updates, not the errors.
And you m ay be betting any other messagess about a market being closed,
in which case you need to make a status not on the order boko at the top level about 
when the market closed.  Each order book shoudl have a last updated on it
Each order book level should have a timestamp of the last kafka update received.
The time the last kafka update was published
the latency on Kafkfa
the time the order server published the update.

The other agent is making an envelope in the parent kalshi API module.

read the data structures and understand the envelope.

Use this to build the concurrent order data book.

Add a REST API to this module.  It should be a bulk API 
which you can query for the top of book for many markets and get back the sides of the book.

### Then pause and test everything

### Second phase

In the second phase, we will take websocket subscriptions from the users.
These can connect, if they disconnect, then remove the subscription from memory.
We need to multiplex where the websocket subscriptions are for different markets.
We can use the same Kalshi subscription API calls to do this.
When users subscdribe to us, then they are subscribed to order book changes which are published
to these websockets, only when the top of the book changes.
There can be an option to subscribe to all book changes as well.

