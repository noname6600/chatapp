package com.example.auth.jwt;

import java.util.Collection;

public interface IKeyManager {
    KeyRecord rotateOnce();
    KeyRecord getCurrentKey();
    KeyRecord getByKid(String kid);
    Collection<KeyRecord> getKeysForJwks();
    void cleanupExpired();
}
