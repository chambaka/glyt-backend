package tz.co.chambaka.glyt.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.chambaka.glyt.domain.CustomerDeviceToken;

import java.util.List;
import java.util.Optional;

public interface CustomerDeviceTokenRepository extends JpaRepository<CustomerDeviceToken, Long> {
    Optional<CustomerDeviceToken> findByDeviceToken(String deviceToken);

    List<CustomerDeviceToken> findByCustomerAccountIdAndActiveTrue(Long customerAccountId);
}
