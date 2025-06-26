### FBG trading API

### Summary

This provides the "domain model" for trading,
as the object API which the system can use to interact
with the OEMS.

The OEMS will use the "Extensions" and other
mapping code to generate the QuickFixJ messages,
which are then given to the FIX Engine and sent 'on the wire'
to Kalshi.

The OEMS will also map the QuickfixJ ExecutionReports,
back to our 'domain' defined here.  

#### Enrichment on incoming from Kalshi

The OEMS will **_enrich_**
the domain with metadata which we know about the order but which is
not present or known on the FIX message itself.

We can "stuff" tag 11 with the critical meta-data, and generally
want to "crack" tag 11 to put it back on the ExecutionReports coming
back from the market.  But the OEMS will almsot certainly have more information
to add on the incoming Executions than we can 'stuff' in tag 11.

### Design Details

#### Goals
1. **Kotlin + Java + JS usable - as Types**
   1. An API which can be used as types in Typescript.
   
1. **Adapt** in two directions on FIX (QuickFixJ)
   1. An API which can be used to convert to and from QuickFixJ and also the FIX spec itself.

#### Implementation notes

1. **"enums"**
   2. These are Kotlin representations of the "FIX Spec"

3. **"domain"**
   3. Also Kotlin representations of the FIX Spec, but for **'messages'**
      4. e.g. **NewOrder**
         5. Sent to OEMS to generate an order into the market.


## Hotly debated topics

#### 1. Exclude or Bubble up QuickfixJ

QuickFixJ does not have real enums, 
and uses code generation.

The practice I have always used
is to generate my own "domain model",
and map the QuickFixJ (or other FIX engine),
model back to this domain model.

This hides the gory implementation details
of the FIX Engine, and allows us flexibility 
of what we name things, how we represent the domain.

The trade-off is that there is then these two copies
of the "enums" and other FIX Related domain model objects.
It is annoying to generate these but the QuickFixJ model
of the trading domain is considered ugly, due to the
code generation, general hygiene issues on QuickFixJ.

On all four trading systems prior, this has been done.

I debated this briefly and then followed suit, 
modeling the FIX objects as tidy Enums and "domain" objects.

#### 2. JS complilation alternatives / approach / structure

I opted to get JS compilation right now.

This will be useful on Admin websites
(such as "liquidity provider website",
which will allow you to add liquidity for a market
on an admin webpage.)

These types may ultimately be used
in other important front ends.

I moved the functional conversions into
"Extensions" classes, under a "jvm"
source folder.

This keeps the "domain model" clean
and easy for Kotlin to compile to JS.

As far as the details, I have not cross compiled and guidance / input
on the idiomatic way to handle this would be helpful, before we move
very much further.  I will also check with AI / resources.