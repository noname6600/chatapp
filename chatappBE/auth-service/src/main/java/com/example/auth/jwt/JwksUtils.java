package com.example.auth.jwt;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

public class JwksUtils {

    public static Map<String, Object> toJwk(KeyRecord key) {

        RSAPublicKey rsa = (RSAPublicKey) key.getPublicKey();

        String n = base64Url(rsa.getModulus());
        String e = base64Url(rsa.getPublicExponent());

        return Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "kid", key.getKid(),
                "n", n,
                "e", e
        );
    }

    private static String base64Url(BigInteger value) {

        byte[] bytes = value.toByteArray();

        if (bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
}

