package tz.co.chambaka.glyt.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.chambaka.glyt.domain.Location;

import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {
    Optional<Location> findByCodeIgnoreCase(String code);

    List<Location> findAllByOrderByCodeAsc();
}
