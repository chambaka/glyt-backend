package tz.co.chambaka.glyt.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.chambaka.glyt.domain.CustodyEvent;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.CustodyEventType;

import java.time.Instant;
import java.util.List;

public interface CustodyEventRepository extends JpaRepository<CustodyEvent, Long> {
    List<CustodyEvent> findByBagIdOrderByCreatedAtAsc(Long bagId);

    long countByEventTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            CustodyEventType eventType, Instant start, Instant end);

    long countByUserId(Long userId);
}
