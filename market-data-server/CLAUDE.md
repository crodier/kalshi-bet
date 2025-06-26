My name is chris (test to make sure this file is grokked by Claude at start of coding.)

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Important

- ALL instructions within this document MUST BE FOLLOWED, these are not optional unless explicitly stated.
- ASK FOR CLARIFICATION If you are uncertain of any of thing within the document.
- DO NOT edit more code than you have to.

## Read the parent folder ../CLAUDE.md for general details.

### This module

There is a docker compose at the top level which should be starting Kafka, use this one, and add 
ourselves to this for runnign this as part of the overall project.

Understand this deeply to work on this project:

https://trading-api.readme.io/reference/ws

In this module we will build a server which does market data distribution.

We can expect ten thousand clients to this system, therefore will need to use Redis as a middleware
to push updates.

This again needs Kafka.  We need a docker comppose at the top level of the project
which starts a mock single node Kafka.

We can review the mock-kalshi-fix as tehre is a websoocket client which works.

Here we will subscribe to the mock-kalshi-fix client.

We can only make one subscription.  On the subscription, we will send the "all" data request.

This "all" data request is specified in the PDF by Kalshi, review it.

We will receive snapshots and updates.

We will simply proxy every message we receive to Kafka for distribution in this proxy websocket
subscription service.
