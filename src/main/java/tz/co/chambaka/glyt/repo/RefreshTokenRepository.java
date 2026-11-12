package tz.co.chambaka.glyt.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.chambaka.glyt.domain.RefreshToken;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUserId(Long userId);
}
