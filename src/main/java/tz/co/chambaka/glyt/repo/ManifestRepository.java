package tz.co.chambaka.glyt.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.chambaka.glyt.domain.Manifest;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ManifestStatus;

import java.util.List;

public interface ManifestRepository extends JpaRepository<Manifest, Long> {
    List<Manifest> findAllByOrderByCreatedAtDesc();

    List<Manifest> findByStatusOrderByCreatedAtDesc(ManifestStatus status);

    long countByPreparedById(Long userId);

    long countByAirlineIgnoreCase(String airline);
}
