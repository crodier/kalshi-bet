# Kalshi Binary Options Market Dynamics

## Overview

Kalshi operates a binary options market where traders can take positions on whether specific events will occur. Each market has two complementary contracts: YES and NO, which together represent the complete probability space of an event.

## The Fundamental Relationship

The cornerstone of Kalshi's market structure is:

```
YES Price + NO Price = $1.00 (100¢)
```

This relationship exists because:
- Winners receive exactly $1.00 per contract
- The market represents a complete probability space
- Arbitrage forces maintain this equilibrium

## Understanding Market Positions

### YES Contract Positions

| Action | Price | Event Occurs | Event Does Not Occur |
|--------|-------|--------------|---------------------|
| **Buy YES** | 65¢ | Receive $1.00 (PROFIT: 35¢) | Lose 65¢ (LOSS: 65¢) |
| **Sell YES** | 65¢ | Pay $1.00 (LOSS: 35¢) | Keep 65¢ (PROFIT: 65¢) |

### NO Contract Positions

| Action | Price | Event Occurs | Event Does Not Occur |
|--------|-------|--------------|---------------------|
| **Buy NO** | 35¢ | Lose 35¢ (LOSS: 35¢) | Receive $1.00 (PROFIT: 65¢) |
| **Sell NO** | 35¢ | Keep 35¢ (PROFIT: 35¢) | Pay $1.00 (LOSS: 65¢) |

### Understanding Selling Contracts

When you **sell** a contract (YES or NO), you:
1. Receive the premium immediately (e.g., 65¢ for selling YES)
2. Take on an obligation to pay $1.00 if you're wrong
3. Your maximum profit is the premium received
4. Your maximum loss is ($1.00 - premium received)

## The Key Insight: Order Equivalence

Due to the fundamental relationship, these positions are economically equivalent:

### Equivalence Table

| NO Position | Equivalent YES Position | Economic Outcome |
|-------------|------------------------|------------------|
| Buy NO at 35¢ | Sell YES at 65¢ | Both profit if event doesn't occur |
| Sell NO at 35¢ | Buy YES at 65¢ | Both profit if event occurs |

### Mathematical Proof

For any NO order at price `P_NO`:
- Equivalent YES price: `P_YES = 100¢ - P_NO`
- Side conversion: Buy ↔ Sell

## Order Book Structure

### Traditional View (4 Order Books)

```
YES Market                          NO Market
----------                          ---------
Bids (Buy YES)   Asks (Sell YES)   Bids (Buy NO)   Asks (Sell NO)
64¢ × 100        66¢ × 150         34¢ × 150       36¢ × 100
63¢ × 200        67¢ × 250         33¢ × 250       37¢ × 200
62¢ × 300        68¢ × 350         32¢ × 350       38¢ × 300
```

### Unified View (After Conversion)

Converting all NO orders to YES equivalents:

```
Unified YES Order Book
----------------------
Bids (Buy YES)              Asks (Sell YES)
64¢ × 100 (native YES)      66¢ × 150 (native YES)
64¢ × 100 (from NO sell)    66¢ × 150 (from NO buy)
63¢ × 200 (native YES)      67¢ × 250 (native YES)
63¢ × 200 (from NO sell)    67¢ × 250 (from NO buy)
```

## Cross Market Detection

### 1. External Cross (Arbitrage Opportunity)

Occurs when: `YES_bid + NO_bid > 100¢`

**Example:**
- YES bid: 65¢ (someone wants to buy YES)
- NO bid: 40¢ (someone wants to buy NO)
- Total: 105¢ > 100¢ ✗ CROSSED!

**Why it's an arbitrage:**
- Sell YES to the 65¢ bidder
- Sell NO to the 40¢ bidder
- Collect 105¢ total
- Pay out maximum 100¢ (only one can win)
- Risk-free profit: 5¢

### 2. Self Cross (Within Same Side)

Occurs when: `Bid Price ≥ Ask Price`

**Example:**
- YES bid: 65¢
- YES ask: 64¢
- Bid > Ask ✗ CROSSED!

## Implementation Strategy

### Order Conversion Algorithm

```kotlin
fun convertNOtoYES(noOrder: Order): Order {
    return Order(
        id = noOrder.id,
        side = if (noOrder.side == BUY) SELL else BUY,  // Flip side
        price = 100 - noOrder.price,                     // Convert price
        size = noOrder.size,
        originalSide = "NO",                              // Track origin
        timestamp = noOrder.timestamp
    )
}
```

### Simplified Order Book Design

1. **Single Internal Representation**: Maintain only YES bids and asks
2. **Automatic Conversion**: Convert all NO orders on entry
3. **Unified Matching**: Match orders in the single YES book
4. **Original Tracking**: Maintain original NO/YES designation for reporting

### Benefits of Unified Approach

1. **Simplified Matching Logic**: One matching engine instead of two
2. **Automatic Arbitrage Prevention**: Cross detection becomes trivial
3. **Reduced Complexity**: Fewer data structures to maintain
4. **Better Performance**: Single lock point for order operations

## Order Priority and Time Precedence

### FIFO at Price Levels

Orders at the same price level maintain strict time priority:

```
Price Level 65¢:
1. Order A (10:00:00) - 100 contracts
2. Order B (10:00:05) - 200 contracts  
3. Order C (10:00:10) - 150 contracts

Matching a 250 contract sell:
- Order A filled completely (100)
- Order B partially filled (150 of 200)
- Order C untouched
```

### Modify Order Behavior

- **Size increase/decrease at same price**: Maintains position
- **Price change**: Loses priority (cancel and re-add)

## Example Scenarios

### Scenario 1: Normal Market Making

```
Trader A: Buy YES at 64¢ (believes event likely)
Trader B: Sell YES at 66¢ (believes event unlikely)
Spread: 2¢ (healthy market)
```

### Scenario 2: Arbitrage Opportunity

```
Trader A: Buy YES at 65¢
Trader B: Buy NO at 40¢
Market Maker: Sells YES at 65¢ and NO at 40¢
Profit: 5¢ risk-free
```

### Scenario 3: NO Order Conversion

```
Original: Buy NO at 30¢
Converted: Sell YES at 70¢
Effect: Same economic exposure, simplified book
```

## Conclusion

The Kalshi market's binary structure, where YES + NO = $1.00, allows for elegant simplification of the order book through NO-to-YES conversion. This approach maintains all economic relationships while reducing complexity and improving performance.