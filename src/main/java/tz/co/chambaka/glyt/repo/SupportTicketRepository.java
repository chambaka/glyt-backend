package tz.co.chambaka.glyt.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.chambaka.glyt.domain.SupportTicket;

import java.util.List;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    List<SupportTicket> findByCustomerAccountIdOrderByCreatedAtDesc(Long customerAccountId);

    List<SupportTicket> findAllByOrderByCreatedAtDesc();

    List<SupportTicket> findByStatusOrderByCreatedAtDesc(String status);

    long countByAssignedToId(Long userId);
}
