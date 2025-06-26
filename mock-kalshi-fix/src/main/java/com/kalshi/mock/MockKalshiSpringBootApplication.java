package com.kalshi.mock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import quickfix.fix50sp2.OrderMassCancelReport;
import quickfix.field.*;

import java.util.UUID;

@SpringBootApplication
@EnableScheduling
public class MockKalshiSpringBootApplication implements CommandLineRunner {

    @Autowired
    private FixServerService fixServerService;
    
    @Value("${quickfix.enabled:true}")
    private boolean quickfixEnabled;

    public static void main(String[] args) {

//        // load the new Kalshi message; test the jar import is working
//        OrderMassCancelReport orderMassCancelReport = new OrderMassCancelReport();
//        orderMassCancelReport.set(new ClOrdID(new UUID(0, 0).toString()));
//        orderMassCancelReport.set(new SecurityGroup("123"));

        SpringApplication.run(MockKalshiSpringBootApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        if (quickfixEnabled) {
            // Start the FIX server when application starts
            fixServerService.startServer();

            // Add shutdown hook to gracefully stop the server
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    fixServerService.stopServer();
                } catch (Exception e) {
                    System.err.println("Error stopping FIX server: " + e.getMessage());
                }
            }));
        } else {
            System.out.println("QuickFIX server is disabled via configuration");
        }
    }
}
