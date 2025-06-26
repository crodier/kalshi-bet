
### The Kalshi UX is only BUY for their order book

Kalshi treat everything as a "Buy"

They show the customer what it costs to "Buy" always, Buy No and Buy Yes.

This can be done, by equivalence, the same as Sell Yes, Buy Yes - they can be flipped.

See @Market_Dynamics.md for the equivalance relationship.

### The UX is all "Buy" No, Buy Yes: (customer perspective only.)
 

### FIX is always Yes.
> To avoid redundancy, the FIX API treats all orders as bids or asks for Yes. With this scheme, traders who
> take on a net No position can equivalently be seen as taking on a "short" position in Yes; but because
> Kalshi is fully cash-collateralized, taking on this "short" position requires collateral.

The mock server has an order book, and the order book will be "All Buys", but preserving internally, what is seen.

### Renaming of things:

Also notable - interesting - Kalshi renamed Side to be No and Yes, and Buy and sell is "action."  Super weird, maybe a mistake, hard to say, binary options events is not totally new to me but I have never seen this rename... of tradiginal "Side = Buy/Sell", not on Wall St.  Big note + gotcha.

Order book notes:
We are "showing' the user, the "Buy" side of the market.  So the "Buy No" and "Buy Yes", ranked.  Maybe internally, we should keep it as this as well, fairly intuitive.

Critical details... pushed to Mock Server, your eyes and brain appreciated, but I think i have binary option market mechanics worked out.
It seems we can keep everytihng as a "Buy" in the order book.
Kalshi reference this approach in FIX here:

### Ah, Fix is also only BUY

Here they are trying to say "Send only Buy orders."  In interesting choice of words..

#### Side<54> tag - conflicts with the other statemetns about Yes, but we will confirm

> Side for the contracts the initiator
> wants to buy. Sells should be
> implemented by purchasing an
> offsetting position on the opposite
> side.

Yes they conflict and say:

To avoid redundancy, the FIX API treats all orders as bids or asks for Yes. With this scheme, traders who
take on a net No position can equivalently be seen as taking on a “short” position in Yes; but because
Kalshi is fully cash-collateralized, taking on this “short” position requires collateral

### Websocket is same as REST order book, all Buy based

#### Websocket Messages

This mimics the “OrderBook” api, and is always BUY
No Buys at levels, and Yes Buys at Levels.
