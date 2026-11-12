package tz.co.chambaka.glyt.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.chambaka.glyt.domain.AuditLog;

import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByBagIdOrderByCreatedAtAsc(Long bagId);

    List<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    default List<AuditLog> findTopByOrderByCreatedAtDesc(int limit) {
        return findAllByOrderByCreatedAtDesc(Pageable.ofSize(limit));
    }

    long countByUserId(Long userId);

    void deleteByUserId(Long userId);
}
