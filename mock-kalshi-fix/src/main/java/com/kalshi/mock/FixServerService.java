package com.kalshi.mock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import quickfix.*;
import quickfix.MemoryStoreFactory;
import quickfix.field.*;
import quickfix.fix50sp2.ExecutionReport;
import quickfix.fix50sp2.NewOrderSingle;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@ConditionalOnProperty(name = "fix.enabled", havingValue = "true", matchIfMissing = true)
public class FixServerService implements Application {

    private Acceptor acceptor;
    private final AtomicBoolean serverRunning = new AtomicBoolean(false);
    private SessionSettings settings;

    public void startServer() throws Exception {
        if (serverRunning.get()) {
            System.out.println("FIX Server is already running");
            return;
        }

        try {
            // Create hard-coded configuration
            settings = createHardCodedSessionSettings();

            // Create message store factory
            MessageStoreFactory storeFactory = new MemoryStoreFactory();

            // Create log factory
            LogFactory logFactory = new ScreenLogFactory(true, true, true);

            // Create message factory
            MessageFactory messageFactory = new DefaultMessageFactory();

            // Create acceptor
            acceptor = new SocketAcceptor(this, storeFactory, settings, logFactory, messageFactory);

            // Start acceptor
            acceptor.start();
            serverRunning.set(true);

            System.out.println("FIX Server started at: " + LocalDateTime.now());
        } catch (Exception e) {
            System.err.println("Failed to start FIX server: " + e.getMessage());
            throw e;
        }
    }



    private SessionSettings createHardCodedSessionSettings() throws ConfigError {
        SessionSettings settings = new SessionSettings();
        
        // Default section settings

        // settings.setString("FileLogPath", "logs");

        // setString sets the "default" session settings
        settings.setString("ConnectionType", "acceptor");
        // settings.setString("StartTime", "07:10:00 UTC");
        // settings.setString("EndTime", "07:00:00 UTC");
        settings.setString("StartTime", "00:00:00");
        settings.setString("EndTime", "23:59:59");
        settings.setString("ReconnectInterval", "5");
        settings.setString("HeartBtInt", "30");
        settings.setString("ValidOrderTypes", "1,2,8,D,F,G");

        settings.setString("DefaultMarketPrice", "77");
        settings.setString("ScreenLogLevels", "DEBUG");
        
        // Use your custom Kalshi dictionary instead of standard FIX dictionaries
        // settings.setString("UseDataDictionary", "Y");
        settings.setString("ValidateUserDefinedFields", "N");
        settings.setString("AllowUnknownMsgFields", "Y");

        // settings.setString("DataDictionary", "kalshi-fix.xml");
        // settings.setString("AppDataDictionary", "kalshi-fix.xml");
        // settings.setString("TransportDataDictionary", "kalshi-FIXT11.xml");

        settings.setString("DefaultApplVerID", "FIX.5.0SP2");

        // Enhanced screen logging
        settings.setString("ScreenLogLevels", "ALL");  // Changed from DEBUG to ALL
        settings.setString("ScreenLogShowIncoming", "Y");
        settings.setString("ScreenLogShowOutgoing", "Y");
        settings.setString("ScreenLogShowEvents", "Y");


        // Session-specific settings
        // FIXT.1.1:FBG-MOCK-KALSHI-RT->SimulatorRT-MOCK
        SessionID sessionID =
                new SessionID("FIXT.1.1",
                        "SimulatorRT-MOCK",
                        "FBG-MOCK-KALSHI-RT");   // Changed: now expecting client as this

        settings.setString(sessionID, "BeginString", "FIXT.1.1");
        // settings.setString(sessionID, "DefaultApplVerID", "FIX.5.0SP2");

        settings.setString(sessionID, "SocketAcceptPort", "9878");
        settings.setString(sessionID, "SocketAcceptAddress", "0.0.0.0");
        settings.setString(sessionID, "ValidateDefinedFields", "N");
        settings.setString(sessionID, "AllowUnknownMsgFields", "Y");
        
        return settings;
    }

    public void stopServer() throws Exception {
        if (!serverRunning.get()) {
            System.out.println("FIX Server is not running");
            return;
        }

        try {
            if (acceptor != null) {
                acceptor.stop();
                serverRunning.set(false);
                System.out.println("FIX Server stopped at: " + LocalDateTime.now());
            }
        } catch (Exception e) {
            System.err.println("Failed to stop FIX server: " + e.getMessage());
            throw e;
        }
    }

    // Stop server at 2:00 AM ET
    @Scheduled(cron = "0 0 2 * * ?", zone = "America/New_York")
    public void scheduledStop() {
        try {
            System.out.println("Scheduled stop initiated at 2:00 AM ET");
            stopServer();
        } catch (Exception e) {
            System.err.println("Error during scheduled stop: " + e.getMessage());
        }
    }

    // Start server at 2:10 AM ET
    @Scheduled(cron = "0 10 2 * * ?", zone = "America/New_York")
    public void scheduledStart() {
        try {
            System.out.println("Scheduled start initiated at 2:10 AM ET");
            // Reset session sequence numbers
            resetSessionSequenceNumbers();
            startServer();
        } catch (Exception e) {
            System.err.println("Error during scheduled start: " + e.getMessage());
        }
    }

    private void resetSessionSequenceNumbers() {
        try {
            if (settings != null) {
                // only one session
                SessionID sessionID = settings.sectionIterator().next();
                Session session = Session.lookupSession(sessionID);
                if (session != null) {
                    session.reset();
                    System.out.println("Reset session sequence numbers for: " + sessionID);
                }
            }
        } catch (Exception e) {
            System.err.println("Error resetting session sequence numbers: " + e.getMessage());
        }
    }

    Map<String, SessionID> sessionIDMap = new ConcurrentHashMap<>();

    // Add this method to your class
    @Override
    public void fromAdmin(Message message, SessionID sessionID)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        
        System.out.println("=== ADMIN MESSAGE RECEIVED ===");
        System.out.println("SessionID: " + sessionID);
        System.out.println("Raw Message: " + message.toString());
        try {
            System.out.println("Message Type: " + message.getHeader().getString(MsgType.FIELD));
            System.out.println("Sender: " + message.getHeader().getString(SenderCompID.FIELD));
            System.out.println("Target: " + message.getHeader().getString(TargetCompID.FIELD));
        } catch (Exception e) {
            System.out.println("Error parsing message fields: " + e.getMessage());
        }
        System.out.println("===============================");
    }

    // Also add exception handling to onCreate
    @Override
    public void onCreate(SessionID sessionID) {
        System.out.println("=== SESSION CREATED ===");
        System.out.println("Expecting --> SessionID: " + sessionID);
        System.out.println("Server as: " + sessionID.getSenderCompID());
        System.out.println("Expecting client: " + sessionID.getTargetCompID());
        System.out.println("========================");
        sessionIDMap.put(sessionID.toString(), sessionID);
    }

    @Override
    public void onLogon(SessionID sessionID) {
        System.out.println("*** SUCCESSFUL LOGON ***");
        System.out.println("SessionID: " + sessionID);
        System.out.println("Time: " + LocalDateTime.now());
    }

    @Override
    public void onLogout(SessionID sessionID) {
        System.out.println("*** LOGOUT ***");
        System.out.println("SessionID: " + sessionID);
        System.out.println("Time: " + LocalDateTime.now());
    }

    @Override
    public void toAdmin(Message message, SessionID sessionID) {
        System.out.println("ToAdmin: " + message);
    }

    @Override
    public void toApp(Message message, SessionID sessionID) throws DoNotSend {
        System.out.println("ToApp: " + message);
    }

    @Override
    public void fromApp(Message message, SessionID sessionID)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        System.out.println("FromApp: " + message);

        // Handle different message types
        crack(message, sessionID);
    }



    // Example message handler for New Order Single
    public void onMessage(NewOrderSingle message, SessionID sessionID)
            throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {

        System.out.println("Received New Order Single: " + message);

        // Extract order details
        String symbol = message.getSymbol().getValue();
        Side side = message.getSide();
        double quantity = message.getOrderQty().getValue();
        String clOrdID = message.getClOrdID().getValue();

        // Create ExecutionReport
        ExecutionReport executionReport = new ExecutionReport(
                new OrderID("ORDER_" + System.currentTimeMillis()),
                new ExecID("EXEC_" + System.currentTimeMillis()),
                new ExecType(ExecType.FILL),
                new OrdStatus(OrdStatus.FILLED),
                side,
                new LeavesQty(0),
                new CumQty(quantity)
        );

        executionReport.set(new ClOrdID(clOrdID));
        executionReport.set(new Symbol(symbol));
        executionReport.set(new OrderQty(quantity));
        executionReport.set(new LastQty(quantity));
        executionReport.set(new LastPx(77));

        try {
            Session.sendToTarget(executionReport, sessionID);
            System.out.println("Sent execution report: " + executionReport);
        } catch (SessionNotFound e) {
            System.err.println("Session not found: " + e.getMessage());
        }
    }

    // Helper method to crack messages
    private void crack(Message message, SessionID sessionID)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {

        String msgType = message.getHeader().getString(MsgType.FIELD);

        switch (msgType) {
            case MsgType.NEW_ORDER_SINGLE:
                onMessage((NewOrderSingle) message, sessionID);
                break;
            default:
                System.out.println("Unhandled message type: " + msgType);
        }
    }
}