package com.chatweb.voice.adapter.out.persistence;

import com.chatweb.voice.domain.model.CallStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "call_history", indexes = {
        @Index(name = "idx_call_history_caller", columnList = "caller_id"),
        @Index(name = "idx_call_history_callee", columnList = "callee_id"),
        @Index(name = "idx_call_history_created", columnList = "created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CallHistoryEntity {

    @Id
    private UUID id;

    @Column(name = "caller_id", nullable = false)
    private UUID callerId;

    @Column(name = "callee_id", nullable = false)
    private UUID calleeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CallStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
