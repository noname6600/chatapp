package com.example.notification.repository;

import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = NotificationRepositoryIntegrationTest.TestApplication.class)
class NotificationRepositoryIntegrationTest {

    @Autowired
    private NotificationRepository repository;

    @Test
    void findInboxOrdered_pinsUnreadActionRequiredFirst_thenCreatedAtDesc() {
        UUID userId = UUID.randomUUID();

        Notification pinnedOld = repository.save(Notification.builder()
                .userId(userId)
                .type(NotificationType.FRIEND_REQUEST)
                .referenceId(UUID.randomUUID())
                .roomId(null)
                .preview("Pinned old")
                .isRead(false)
                .actionRequired(true)
                .createdAt(Instant.parse("2026-04-17T00:00:00Z"))
                .build());

        Notification regularNew = repository.save(Notification.builder()
                .userId(userId)
                .type(NotificationType.MESSAGE)
                .referenceId(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .preview("Regular new")
                .isRead(false)
                .actionRequired(false)
                .createdAt(Instant.parse("2026-04-17T01:00:00Z"))
                .build());

        Notification pinnedReadNewest = repository.save(Notification.builder()
                .userId(userId)
                .type(NotificationType.FRIEND_REQUEST)
                .referenceId(UUID.randomUUID())
                .roomId(null)
                .preview("Pinned but read (should not be pinned)")
                .isRead(true)
                .actionRequired(true)
                .createdAt(Instant.parse("2026-04-17T02:00:00Z"))
                .build());

        List<Notification> inbox = repository.findInboxOrdered(userId);

        // Unread action-required comes first regardless of timestamp.
        assertThat(inbox.get(0).getId()).isEqualTo(pinnedOld.getId());

        // The remaining entries are ordered by createdAt desc.
        assertThat(inbox.get(1).getId()).isEqualTo(pinnedReadNewest.getId());
        assertThat(inbox.get(2).getId()).isEqualTo(regularNew.getId());
    }

    @Test
    void markAllReadByUserId_preservesFriendRequestUnreadAndClearsOtherTypes() {
        UUID userId = UUID.randomUUID();

        Notification sticky = repository.save(Notification.builder()
                .userId(userId)
                .type(NotificationType.FRIEND_REQUEST)
                .referenceId(UUID.randomUUID())
                .roomId(null)
                .preview("sticky")
                .isRead(false)
                .actionRequired(true)
                .createdAt(Instant.now())
                .build());

        Notification actionRequiredNonFriend = repository.save(Notification.builder()
                .userId(userId)
                .type(NotificationType.GROUP_INVITE)
                .referenceId(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .preview("invite")
                .isRead(false)
                .actionRequired(true)
                .createdAt(Instant.now())
                .build());

        Notification eligible = repository.save(Notification.builder()
                .userId(userId)
                .type(NotificationType.MESSAGE)
                .referenceId(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .preview("eligible")
                .isRead(false)
                .actionRequired(false)
                .createdAt(Instant.now())
                .build());

        int updated = repository.markAllReadByUserId(userId);

        assertThat(updated).isEqualTo(2);
        assertThat(repository.findById(sticky.getId()).orElseThrow().isRead()).isFalse();
        assertThat(repository.findById(actionRequiredNonFriend.getId()).orElseThrow().isRead()).isTrue();
        assertThat(repository.findById(eligible.getId()).orElseThrow().isRead()).isTrue();
    }

    @Test
    void clearRoomByUserId_marksOnlyEligibleUnreadInRoom() {
        UUID userId = UUID.randomUUID();
        UUID roomA = UUID.randomUUID();
        UUID roomB = UUID.randomUUID();

        Notification roomSticky = repository.save(Notification.builder()
                .userId(userId)
                .type(NotificationType.FRIEND_REQUEST)
                .referenceId(UUID.randomUUID())
                .roomId(roomA)
                .preview("sticky")
                .isRead(false)
                .actionRequired(true)
                .createdAt(Instant.now())
                .build());

        Notification roomEligible = repository.save(Notification.builder()
                .userId(userId)
                .type(NotificationType.MESSAGE)
                .referenceId(UUID.randomUUID())
                .roomId(roomA)
                .preview("eligible")
                .isRead(false)
                .actionRequired(false)
                .createdAt(Instant.now())
                .build());

        Notification otherRoomEligible = repository.save(Notification.builder()
                .userId(userId)
                .type(NotificationType.MESSAGE)
                .referenceId(UUID.randomUUID())
                .roomId(roomB)
                .preview("other room")
                .isRead(false)
                .actionRequired(false)
                .createdAt(Instant.now())
                .build());

        int updated = repository.clearRoomByUserId(userId, roomA);

        assertThat(updated).isEqualTo(1);
        assertThat(repository.findById(roomSticky.getId()).orElseThrow().isRead()).isFalse();
        assertThat(repository.findById(roomEligible.getId()).orElseThrow().isRead()).isTrue();
        assertThat(repository.findById(otherRoomEligible.getId()).orElseThrow().isRead()).isFalse();
    }

    @Test
    void findInboxOrderedBefore_returnsWindowedPagesWithoutOverlap() {
        UUID userId = UUID.randomUUID();
        Instant t3 = Instant.parse("2026-04-20T10:03:00Z");
        Instant t2 = Instant.parse("2026-04-20T10:02:00Z");
        Instant t1 = Instant.parse("2026-04-20T10:01:00Z");

        Notification n3 = repository.save(Notification.builder()
                .userId(userId)
                .type(NotificationType.MESSAGE)
                .referenceId(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .preview("n3")
                .isRead(false)
                .actionRequired(false)
                .createdAt(t3)
                .build());

        Notification n2 = repository.save(Notification.builder()
                .userId(userId)
                .type(NotificationType.MESSAGE)
                .referenceId(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .preview("n2")
                .isRead(false)
                .actionRequired(false)
                .createdAt(t2)
                .build());

        Notification n1 = repository.save(Notification.builder()
                .userId(userId)
                .type(NotificationType.MESSAGE)
                .referenceId(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .preview("n1")
                .isRead(false)
                .actionRequired(false)
                .createdAt(t1)
                .build());

        List<Notification> firstPage = repository.findInboxOrdered(userId, PageRequest.of(0, 2));
        assertThat(firstPage).extracting(Notification::getId).containsExactly(n3.getId(), n2.getId());

        List<Notification> secondPage = repository.findInboxOrderedBefore(userId, t3, PageRequest.of(1, 2));
        assertThat(secondPage).extracting(Notification::getId).containsExactly(n1.getId());
        assertThat(secondPage).extracting(Notification::getId).doesNotContain(n3.getId(), n2.getId());
    }

    @Test
        void findInboxOrdered_whenCreatedAtEqual_returnsDeterministicOrder() {
        UUID userId = UUID.randomUUID();
        Instant ts = Instant.parse("2026-04-20T11:00:00Z");

        Notification a = repository.save(Notification.builder()
                .userId(userId)
                .type(NotificationType.MESSAGE)
                .referenceId(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .preview("a")
                .isRead(false)
                .actionRequired(false)
                .createdAt(ts)
                .build());

        Notification b = repository.save(Notification.builder()
                .userId(userId)
                .type(NotificationType.MESSAGE)
                .referenceId(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .preview("b")
                .isRead(false)
                .actionRequired(false)
                .createdAt(ts)
                .build());

        List<Notification> firstRead = repository.findInboxOrdered(userId);
        List<Notification> secondRead = repository.findInboxOrdered(userId);

        assertThat(firstRead).extracting(Notification::getId).containsExactlyElementsOf(
                secondRead.stream().map(Notification::getId).toList()
        );
        assertThat(firstRead).extracting(Notification::getId).containsExactlyInAnyOrder(a.getId(), b.getId());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Notification.class)
    @EnableJpaRepositories(basePackageClasses = NotificationRepository.class)
    static class TestApplication {
    }
}

