package tz.co.chambaka.glyt.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.chambaka.glyt.domain.ManifestBag;

import java.util.List;
import java.util.Optional;

public interface ManifestBagRepository extends JpaRepository<ManifestBag, Long> {
    Optional<ManifestBag> findByBagId(Long bagId);

    List<ManifestBag> findByManifestId(Long manifestId);

    void deleteByBagId(Long bagId);
}
