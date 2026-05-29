package com.chatweb.auth.jwt.impl;

import com.chatweb.auth.entity.JwtKeyEntity;
import com.chatweb.auth.jwt.IKeyManager;
import com.chatweb.auth.jwt.KeyRecord;
import com.chatweb.auth.repository.JwtKeyRepository;
import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.*;
import java.security.spec.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeyManager implements IKeyManager {

    // Redis key used as a cross-instance mutex for JWT key rotation.
    private static final String ROTATION_LOCK_KEY = "auth:jwt:rotation-lock";
    private static final Duration ROTATION_LOCK_TTL = Duration.ofSeconds(30);

    private final JwtKeyRepository repo;
    private final Clock clock;
    private final StringRedisTemplate redisTemplate;

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
        // Acquire a cross-instance Redis mutex so only one auth-service instance
        // performs rotation at a time. Without this, two instances starting simultaneously
        // could both generate different key pairs and each clear the other's active key.
        String lockValue = UUID.randomUUID().toString();
        boolean acquired = Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(ROTATION_LOCK_KEY, lockValue, ROTATION_LOCK_TTL));

        if (!acquired) {
            // Another instance is rotating. Wait briefly then return whatever they saved.
            try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            return repo.findLatestActive()
                    .map(entity -> {
                        KeyRecord r = toRecord(entity);
                        keyStore.put(r.getKid(), r);
                        currentKid = r.getKid();
                        return r;
                    })
                    .orElseThrow(() -> new BusinessException(
                            CommonErrorCode.INTERNAL_ERROR, "Key rotation in progress but no key found in DB"));
        }

        try {
            // Double-check: another instance may have rotated while we waited for synchronized.
            Optional<JwtKeyEntity> fresh = repo.findLatestActive();
            if (fresh.isPresent() && !isEntityExpired(fresh.get())) {
                KeyRecord r = toRecord(fresh.get());
                keyStore.put(r.getKid(), r);
                currentKid = r.getKid();
                return r;
            }

            repo.clearAllActive();

            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();

            Instant now = Instant.now(clock);
            String kid = UUID.randomUUID().toString();

            JwtKeyEntity entity = JwtKeyEntity.builder()
                    .kid(kid)
                    .publicKey(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()))
                    .privateKey(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()))
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
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "Key rotation failed");
        } finally {
            // Release lock only if we still own it (TTL guards against stale locks on crash).
            redisTemplate.delete(ROTATION_LOCK_KEY);
        }
    }

    @Override
    public KeyRecord getCurrentKey() {
        KeyRecord record = currentKid != null ? keyStore.get(currentKid) : null;

        if (record == null || record.isExpired(Instant.now(clock))) {
            // Before generating a new key, check whether another instance already rotated.
            Optional<JwtKeyEntity> fresh = repo.findLatestActive();
            if (fresh.isPresent() && !isEntityExpired(fresh.get())) {
                KeyRecord r = toRecord(fresh.get());
                keyStore.put(r.getKid(), r);
                currentKid = r.getKid();
                return r;
            }
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
        // Local cache cleanup runs on every instance (cheap). DB deletion uses a
        // skip-lock so only one instance issues the DELETE per cycle.
        Instant now = Instant.now(clock);
        keyStore.entrySet().removeIf(e -> e.getValue().isExpired(now));

        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent("auth:scheduler:key-cleanup-db", "1", Duration.ofMinutes(9));
        if (Boolean.TRUE.equals(acquired)) {
            repo.deleteExpired(now);
        }
    }

    @Override
    public Collection<KeyRecord> getKeysForJwks() {
        // Read from DB so every instance returns ALL valid keys, not just the ones
        // it personally rotated. Without this, instance A's JWKS would not include
        // keys generated by instance B, causing verification failures across instances.
        return repo.findByExpiredAtAfter(Instant.now(clock))
                .stream()
                .map(this::toRecord)
                .toList();
    }

    private boolean isEntityExpired(JwtKeyEntity entity) {
        return entity.getExpiredAt() != null && entity.getExpiredAt().isBefore(Instant.now(clock));
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
                    CommonErrorCode.INTERNAL_ERROR,
                    "Key parsing failed"
            );
        }
    }
}

