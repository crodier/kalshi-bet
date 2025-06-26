package com.betfanatics.exchange.order.util;

import org.junit.jupiter.api.Test;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

public class KalshiSignatureUtilTest {
    private static final String SENDING_TIME = "20240613-12:34:56.789";
    private static final String MSG_TYPE = "D";
    private static final String MSG_SEQ_NUM = "1";
    private static final String SENDER_COMP_ID = "SENDER";
    private static final String TARGET_COMP_ID = "TARGET";

    private static String getPrivateKeyPem(PrivateKey privateKey) {
        String base64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" +
                base64.replaceAll("(.{64})", "$1\n") +
                "\n-----END PRIVATE KEY-----";
    }

    @Test
    public void testGenerateSignatureAndLoadPrivateKey() throws Exception {
        // Generate a test RSA key pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();

        // Convert private key to PEM format
        String pem = getPrivateKeyPem(privateKey);

        // Load private key from PEM
        PrivateKey loadedKey = KalshiSignatureUtil.loadPrivateKey(pem);
        assertNotNull(loadedKey);
        assertEquals(privateKey, loadedKey);

        // Generate signature
        String signature = KalshiSignatureUtil.generateSignature(
                SENDING_TIME, MSG_TYPE, MSG_SEQ_NUM, SENDER_COMP_ID, TARGET_COMP_ID, loadedKey);
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }
} 