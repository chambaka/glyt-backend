package tz.co.chambaka.glyt.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.chambaka.glyt.domain.CustomerRefreshToken;

import java.util.Optional;

public interface CustomerRefreshTokenRepository extends JpaRepository<CustomerRefreshToken, Long> {
    Optional<CustomerRefreshToken> findByTokenHash(String tokenHash);

    void deleteByAccountId(Long accountId);
}
