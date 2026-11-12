package tz.co.chambaka.glyt.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.chambaka.glyt.domain.TrackingToken;

import java.util.Optional;

public interface TrackingTokenRepository extends JpaRepository<TrackingToken, Long> {
    Optional<TrackingToken> findByTokenAndRevokedAtIsNull(String token);
}
