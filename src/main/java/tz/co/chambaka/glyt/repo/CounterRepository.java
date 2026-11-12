package tz.co.chambaka.glyt.repo;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tz.co.chambaka.glyt.domain.Counter;

import java.util.Optional;

public interface CounterRepository extends JpaRepository<Counter, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Counter c where c.name = :name")
    Optional<Counter> lockByName(@Param("name") String name);
}
