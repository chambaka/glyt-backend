package tz.co.chambaka.glyt.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.chambaka.glyt.domain.BaggageException;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionType;

import java.util.List;

public interface BaggageExceptionRepository extends JpaRepository<BaggageException, Long> {
    List<BaggageException> findByBagIdOrderByCreatedAtDesc(Long bagId);

    List<BaggageException> findByStatusOrderByCreatedAtDesc(ExceptionStatus status);

    List<BaggageException> findAllByOrderByCreatedAtDesc();

    long countByStatus(ExceptionStatus status);

    boolean existsByBagIdAndTypeAndStatus(Long bagId, ExceptionType type, ExceptionStatus status);

    long countByBagIdAndStatus(Long bagId, ExceptionStatus status);

    long countByRaisedById(Long userId);

    long countByAssignedToId(Long userId);

    long countByResolvedById(Long userId);
}
