package tz.co.chambaka.glyt.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.chambaka.glyt.domain.StorageLocation;

import java.util.List;
import java.util.Optional;

public interface StorageLocationRepository extends JpaRepository<StorageLocation, Long> {
    Optional<StorageLocation> findByCodeIgnoreCase(String code);

    List<StorageLocation> findAllByOrderByCodeAsc();

    boolean existsByFacilityCodeIgnoreCase(String facilityCode);

    long countByFacilityCodeIgnoreCase(String facilityCode);
}
