package com.ronrob.brti.crypto;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Base64;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

/**
 * Signs Kalshi WebSocket handshakes. Handles Kalshi's recommended Ed25519 PKCS#8
 * keys and its RSA PKCS#1 / PKCS#8 keys. Never logs or serves secret key material.
 */
public final class KalshiSigner {

    private final PrivateKey privateKey;

    public KalshiSigner(Path pemPath) throws IOException {
        try (
            Reader reader = Files.newBufferedReader(pemPath, StandardCharsets.UTF_8);
            PEMParser parser = new PEMParser(reader)
        ) {
            Object parsed = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            if (parsed instanceof PEMKeyPair pair) {
                privateKey = converter.getKeyPair(pair).getPrivate();
            } else if (parsed instanceof PrivateKeyInfo info) {
                privateKey = converter.getPrivateKey(info);
            } else {
                throw new IllegalArgumentException(
                    "Unsupported or encrypted PEM private key. Use a Kalshi Ed25519 or RSA private key PEM."
                );
            }
        }
    }

    public String sign(String timestampMs, String method, String pathWithoutQuery) {
        String message = timestampMs + method + pathWithoutQuery;
        try {
            String keyType = privateKey.getAlgorithm();
            Signature signature;
            if ("Ed25519".equalsIgnoreCase(keyType) || "EdDSA".equalsIgnoreCase(keyType)) {
                signature = Signature.getInstance("Ed25519");
                signature.initSign(privateKey);
            } else if ("RSA".equalsIgnoreCase(keyType)) {
                signature = Signature.getInstance("RSASSA-PSS");
                signature.initSign(privateKey);
                signature.setParameter(
                    new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1)
                );
            } else {
                throw new IllegalArgumentException("Unsupported private key algorithm: " + keyType);
            }
            signature.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception exception) {
            throw new IllegalStateException(
                "Unable to sign Kalshi request: " + exception.getMessage(),
                exception
            );
        }
    }
}
