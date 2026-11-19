package tz.co.chambaka.glyt.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tz.co.chambaka.glyt.domain.Customer;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByPassportNumberIgnoreCaseAndNationalityIgnoreCase(String passportNumber, String nationality);

    @Query("""
            select c from Customer c
            where lower(c.fullName) like :q
               or lower(c.phone) like :q
               or lower(c.email) like :q
               or lower(c.passportNumber) like :q
               or lower(c.customerNumber) like :q
            order by c.fullName
            """)
    List<Customer> search(@Param("q") String q, Pageable pageable);
}
