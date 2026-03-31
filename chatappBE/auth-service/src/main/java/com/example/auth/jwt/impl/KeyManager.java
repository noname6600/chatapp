package com.example.auth.jwt.impl;

import com.example.auth.entity.JwtKeyEntity;
import com.example.auth.jwt.IKeyManager;
import com.example.auth.jwt.KeyRecord;
import com.example.auth.repository.JwtKeyRepository;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.*;
import java.security.spec.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeyManager implements IKeyManager {

    private final JwtKeyRepository repo;
    private final Clock clock;

    @Value("${auth.jwt.access-token-expiration-ms}")
    private long jwtExpirationMs;

    @Value("${auth.signing-key.rotation-ms}")
    private long rotationMs;

    private final Map<String, KeyRecord> keyStore = new ConcurrentHashMap<>();
    private volatile String currentKid;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void init() {
        Instant now = Instant.now(clock);

        repo.findByExpiredAtAfter(now)
                .forEach(entity -> {
                    KeyRecord record = toRecord(entity);
                    keyStore.put(record.getKid(), record);
                    if (entity.isActive()) {
                        currentKid = entity.getKid();
                    }
                });

        if (currentKid == null) {
            rotateOnce();
        }
    }

    private long gracePeriodMs() {
        return jwtExpirationMs + rotationMs;
    }

    @Override
    @Transactional
    public synchronized KeyRecord rotateOnce() {

        try {

            repo.clearAllActive();

            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();

            Instant now = Instant.now(clock);
            String kid = UUID.randomUUID().toString();

            JwtKeyEntity entity = JwtKeyEntity.builder()
                    .kid(kid)
                    .publicKey(Base64.getEncoder()
                            .encodeToString(pair.getPublic().getEncoded()))
                    .privateKey(Base64.getEncoder()
                            .encodeToString(pair.getPrivate().getEncoded()))
                    .active(true)
                    .createdAt(now)
                    .expiredAt(now.plusMillis(gracePeriodMs()))
                    .build();

            repo.saveAndFlush(entity);

            KeyRecord record = toRecord(entity);
            keyStore.put(kid, record);

            currentKid = kid;

            log.info("JWT key rotated successfully. new kid={}", kid);

            return record;

        } catch (Exception e) {

            log.error("JWT key rotation failed", e);

            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Key rotation failed"
            );
        }
    }

    @Override
    public KeyRecord getCurrentKey() {
        KeyRecord record = keyStore.get(currentKid);

        if (record == null || record.isExpired(Instant.now(clock))) {
            return rotateOnce();
        }

        return record;
    }

    @Override
    public KeyRecord getByKid(String kid) {
        KeyRecord record = keyStore.get(kid);

        if (record != null) {
            return record;
        }

        return repo.findByKid(kid)
                .map(entity -> {
                    KeyRecord r = toRecord(entity);
                    keyStore.put(kid, r);
                    return r;
                })
                .orElse(null);
    }

    @Override
    @Scheduled(fixedDelay = 600000)
    @Transactional
    public void cleanupExpired() {
        Instant now = Instant.now(clock);
        repo.deleteExpired(now);
        keyStore.entrySet()
                .removeIf(e -> e.getValue().isExpired(now));
    }

    @Override
    public Collection<KeyRecord> getKeysForJwks() {
        return keyStore.values()
                .stream()
                .filter(k -> !k.isExpired(Instant.now(clock)))
                .toList();
    }

    private KeyRecord toRecord(JwtKeyEntity e) {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");

            PrivateKey priv = kf.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(e.getPrivateKey())));

            PublicKey pub = kf.generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(e.getPublicKey())));

            return new KeyRecord(
                    e.getKid(),
                    priv,
                    pub,
                    e.getCreatedAt(),
                    e.getExpiredAt(),
                    e.isActive()
            );

        } catch (Exception ex) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Key parsing failed"
            );
        }
    }
}