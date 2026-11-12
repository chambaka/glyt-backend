package tz.co.chambaka.glyt.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.chambaka.glyt.domain.Quotation;

import java.util.List;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {
    List<Quotation> findByCustomerAccountIdOrderByCreatedAtDesc(Long customerAccountId);

    long countByServiceId(Long serviceId);
}
