package tz.co.chambaka.glyt.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.chambaka.glyt.domain.BagSeal;

import java.util.List;

public interface BagSealRepository extends JpaRepository<BagSeal, Long> {
    List<BagSeal> findByBagIdAndCurrentSealTrue(Long bagId);

    long countByAppliedById(Long userId);
}
