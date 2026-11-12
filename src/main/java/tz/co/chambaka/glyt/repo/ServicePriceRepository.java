package tz.co.chambaka.glyt.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.chambaka.glyt.domain.ServicePrice;

import java.util.List;
import java.util.Optional;

public interface ServicePriceRepository extends JpaRepository<ServicePrice, Long> {
    List<ServicePrice> findByServiceIdAndActiveTrue(Long serviceId);

    Optional<ServicePrice> findFirstByServiceIdAndActiveTrueOrderByIdAsc(Long serviceId);

    List<ServicePrice> findByServiceId(Long serviceId);

    void deleteByServiceId(Long serviceId);
}
