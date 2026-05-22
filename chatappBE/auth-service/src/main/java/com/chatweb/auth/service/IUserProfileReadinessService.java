package com.chatweb.auth.service;

import java.util.Optional;
import java.util.UUID;

public interface IUserProfileReadinessService {
    Optional<Boolean> getProfileReadinessSnapshot(UUID accountId);
}
