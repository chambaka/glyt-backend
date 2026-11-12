package tz.co.chambaka.glyt.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.chambaka.glyt.domain.PaymentTransaction;

import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentTransaction> findByExternalRef(String externalRef);

    Optional<PaymentTransaction> findByReceiptNumber(String receiptNumber);

    List<PaymentTransaction> findByCustomerAccountIdOrderByCreatedAtDesc(Long customerAccountId);

    List<PaymentTransaction> findByStatusOrderByCreatedAtDesc(String status);

    List<PaymentTransaction> findAllByOrderByCreatedAtDesc();

    boolean existsByBookingIdAndStatus(Long bookingId, String status);
}
