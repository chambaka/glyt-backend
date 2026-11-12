package tz.co.chambaka.glyt.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.chambaka.glyt.domain.Airline;

import java.util.List;
import java.util.Optional;

public interface AirlineRepository extends JpaRepository<Airline, Long> {
    Optional<Airline> findByCodeIgnoreCase(String code);

    Optional<Airline> findByNameIgnoreCase(String name);

    List<Airline> findAllByOrderBySortOrderAscNameAsc();

    List<Airline> findByActiveTrueOrderBySortOrderAscNameAsc();
}
