package com.example.auth.service;

import java.util.UUID;

public interface IUserProfileReadinessService {
    boolean waitUntilReady(UUID accountId);
}
