package com.chatweb.voice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CallHistoryRepository extends JpaRepository<CallHistoryEntity, UUID> {
}
