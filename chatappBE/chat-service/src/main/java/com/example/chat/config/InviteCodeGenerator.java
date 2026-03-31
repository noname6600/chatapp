package com.example.chat.config;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;

@Component
public class InviteCodeGenerator {

    private static final String SECRET = "guesswhichroomisit";

    public String encode(UUID roomId) {
        byte[] uuidBytes = toBytes(roomId);
        byte[] salted = xor(uuidBytes, SECRET.getBytes());
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(salted);
    }

    public UUID decode(String code) {
        byte[] salted = Base64.getUrlDecoder().decode(code);
        byte[] uuidBytes = xor(salted, SECRET.getBytes());
        return fromBytes(uuidBytes);
    }


    private byte[] toBytes(UUID uuid) {
        ByteBuffer buf = ByteBuffer.allocate(16);
        buf.putLong(uuid.getMostSignificantBits());
        buf.putLong(uuid.getLeastSignificantBits());
        return buf.array();
    }

    private UUID fromBytes(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        return new UUID(buf.getLong(), buf.getLong());
    }

    private byte[] xor(byte[] data, byte[] key) {
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) (data[i] ^ key[i % key.length]);
        }
        return out;
    }
}

