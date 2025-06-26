# FIX Sequence Number Issues

## Problem Description
FIX sequence number mismatches occur when the client and server have different expectations for message sequence numbers. This typically manifests as:

```
Disconnecting: Received logout request: MsgSeqNum too low, expecting 1083 but received 11
```

## Root Cause
This happens when:
1. Client session state is reset (e.g., sessions table cleared) but server maintains persistent sequence state
2. Ungraceful disconnection leaves sequence numbers out of sync
3. Different sequence reset policies between client and server

## Current Detection
The `FixGatewayActor` now logs both incoming and outgoing sequence numbers on logon:
```kotlin
val expectedTargetSeqNum = session.expectedTargetNum  // What we expect to receive next
val nextSenderSeqNum = session.getExpectedSenderNum()  // What we will send next

log.info("Sequence check on logon - Expected to receive: {}, Last received: {}, Next to send: {}", 
        expectedTargetSeqNum, lastReceivedSeqNum, nextSenderSeqNum)
```

And warns about mismatches:
```kotlin
if (nextSenderSeqNum < 100 && expectedTargetSeqNum > 100) {
    log.warn("SEQUENCE MISMATCH DETECTED: Our next sender sequence is {} but counterparty " +
            "expects high sequences (incoming {}). This may cause 'MsgSeqNum too low' rejections.", 
            nextSenderSeqNum, expectedTargetSeqNum)
}
```

## Recovery Options


### Automatic Recovery (must be automated and tested) 
(**but should never happen, is bad.**)
1. **Send replay request from Fix Engine** - QuickfixJ is configured to request all messages missed; sends this on logon.
   2. Next the counterparty (Kalshi) replays any unknown sequence numbers.

#### Other alternatives - not recommended
3. **SequenceReset message** - Send programmatic reset to expected sequence number
2. **Session store reset** - Programmatically clear QuickFIX/J session state
3. **Parse logout and adjust** - Extract expected sequence from logout text and reset

### Manual Recovery (Method of last resort - can lead to message loss)
3. **Coordinate with counterparty** - Contact Kalshi to reset both sides simultaneously
2. **Temporary ResetOnLogon=Y** - Set in `quickfixj-<env>.cfg` then revert after successful connection
3. **Clear session state on both sides** - Ensure clean slate for both parties


## Automatic Recovery Implementation Notes
If implementing automatic recovery:
- Parse logout message: `"expecting (\d+) but received (\d+)"`
- Send SequenceReset message with NewSeqNo field set to expected value
- Ensure proper audit logging
- Consider message gap handling (potential missed messages)
- Test thoroughly in non-production environment

## Configuration
Current QuickFIX/J settings in `quickfixj-<env>.cfg`:
```
ResetOnLogon=N  # Maintains sequence continuity
MessageStoreFactory=quickfix.JdbcStoreFactory  # Persists sequences in database
```

## Prevention
- Implement graceful shutdown procedures
- Monitor sequence gaps proactively
- Coordinate maintenance windows with counterparties
- Consider heartbeat frequency adjustments during high-latency periods