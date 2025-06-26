# Binary Options Trading Example: NBA Finals Market

## Kalshi Order Book

Kalshi are showing the "Buy" sides for "No" and "Yes".  On their order book.

## Market Overview

**Question:** "Will the Pacers win the NBA Finals?"

### Contract Types

If you own a contract at expiration, you receive $1 based on the outcome.

If you sold a contract, at expiration you pay $1 based on the outcome.

* **YES contract (long 1, owned):** Pays $1 if Pacers WIN
* **NO contract (long 1, owned):** Pays $1 if Pacers LOSE

## Trading Position

**Your Position:** Buy NO at 70¢

> When you Buy NO at 70¢, you're betting the Pacers will LOSE.

You end up long 1 No contract.  You offer to **Buy** action, side= **NO** at $70 cents.

Someone takes the other side, they **Sell** action, side = **NO** at $70 cents.

## Kalshi reversed meanings:

It may be typical binary options; however could be Kalshi - the names are odd.

### Note on renaming of Side:  
1. Kalshi renamed Yes and No as the "side".
1. Kalshi reversed the meaning of Side (buy / sell) from FIX, and called it "action".

## Economic Analysis

### Cost and Profit Breakdown

* **Initial Cost:** 70¢ per No contract
* **Winning Scenario (Pacers LOSE):** Receive $1.00 → Profit = 30¢
* **Losing Scenario (Pacers WIN):** Receive $0 → Loss = 70¢

### Order Conversion Mechanism

**Basic Conversion:** Buy NO → Sell YES

The order book converts your Buy NO at 70¢ to Sell YES at 30¢ (100¢ - 70¢ = 30¢)

### Position Equivalence

#### As Buy NO at 70¢

* Initial payment: 70¢ upfront
* Pacers lose (NO event): Receive $1.00
* Pacers win (YES event): Receive $0

#### As Sell YES at 30¢

* Initial receipt: 30¢ upfront
* Pacers win (YES event): Owe $1.00 → Net loss = 70¢
* Pacers lose (NO event): Owe $0 → Net profit = 30¢

## Mathematical Proof

| Outcome     | Buy NO at 70¢       | Sell YES at 30¢     | Net Position |
|-------------|---------------------|---------------------|--------------|
| Pacers WIN  | -70¢ (lose premium) | +30¢ - $1.00 = -70¢ | -70¢         |
| Pacers LOSE | +$1.00 - 70¢ = +30¢ | +30¢ - $0 = +30¢    | +30¢         |

## Market Maker Perspective

### Order Matching Process

The conversion enables efficient order matching:

1. **Your Position:** Buy NO at 70¢ (betting against Pacers)
2. **Counter Party:** Buy YES at 30¢ (betting for Pacers)

### Matching Process

1. Your Buy NO at 70¢ → Converted to Sell YES at 30¢
2. Other trader's Buy YES at 30¢
3. Match executed at 30¢ YES price

## System Implementation

### Order Creation

Market Maker Perspective

This conversion allows market makers to match orders more efficiently:

- You: Want to Buy NO at 70¢ (betting against Pacers)
- Another trader: Wants to Buy YES at 30¢ (betting for Pacers, but only willing to pay 30¢)

These orders can match! The market maker sees:
- Your Buy NO at 70¢ → Converted to Sell YES at 30¢
- Other trader's Buy YES at 30¢
- Match executed at 30¢ YES price

In Our System

// Your original order
```OrderBookEntry buyNo = new OrderBookEntry("1", "you", Side.no, "buy", 70, 100, timestamp);```

```
// After conversion in our system
// getNormalizedPrice() returns: 30 (100 - 70)
// isNormalizedBuy() returns: false (buy NO → sell YES)
// The order is placed in the asks side at price 30
```

This is why in the order book, your Buy NO order appears as an ask (sell order) at the converted price, making it available to match with someone who
wants to Buy YES at that price.
