package com.betfanatics.exchange.order.util;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.PSSParameterSpec;
import java.util.Base64;

public class KalshiSignatureUtil {
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static String generateSignature(
            String sendingTime,
            String msgType,
            String msgSeqNum,
            String senderCompID,
            String targetCompID,
            PrivateKey privateKey
    ) throws Exception {
        String soh = "\u0001";
        String preHashString = String.join(soh,
                sendingTime, msgType, msgSeqNum, senderCompID, targetCompID
        );
        byte[] msgBytes = preHashString.getBytes(StandardCharsets.UTF_8);

        Signature signature = Signature.getInstance("RSASSA-PSS");
        signature.setParameter(new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
        signature.initSign(privateKey);
        signature.update(msgBytes);
        byte[] sigBytes = signature.sign();

        return Base64.getEncoder().encodeToString(sigBytes);
    }

    public static PrivateKey loadPrivateKey(String pem) throws Exception {
        String privateKeyPEM = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(keySpec);
    }
} 