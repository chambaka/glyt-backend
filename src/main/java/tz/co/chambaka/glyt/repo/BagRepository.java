package tz.co.chambaka.glyt.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.enums.BagStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BagRepository extends JpaRepository<Bag, Long> {
    Optional<Bag> findByCodeIgnoreCase(String code);

    long countByStatus(BagStatus status);

    List<Bag> findByStatus(BagStatus status);

    Optional<Bag> findByStorageLocationId(Long storageLocationId);

    List<Bag> findTop12ByOrderByUpdatedAtDesc();

    boolean existsByStorageLocationId(Long storageLocationId);

    boolean existsByCurrentLocationIgnoreCase(String currentLocation);

    long countByCurrentLocationIgnoreCase(String currentLocation);

    List<Bag> findByStatusIn(Collection<BagStatus> statuses);

    List<Bag> findByBookingId(Long bookingId);

    @Query("""
            select b from Bag b
            where b.status = :status
              and not exists (select 1 from ManifestBag mb where mb.bag = b)
            """)
    List<Bag> findEligibleForManifest(@Param("status") BagStatus status);

    @Query("""
            select b from Bag b
            join b.customer c
            join b.booking bk
            left join b.manifestLink ml
            left join ml.manifest m
            where lower(b.code) like :q
               or lower(c.fullName) like :q
               or lower(c.phone) like :q
               or lower(c.passportNumber) like :q
               or lower(c.email) like :q
               or lower(c.customerNumber) like :q
               or lower(bk.bookingNumber) like :q
               or lower(coalesce(b.sealNumber, '')) like :q
               or lower(coalesce(m.number, '')) like :q
            order by b.updatedAt desc
            """)
    List<Bag> search(@Param("q") String q, Pageable pageable);
}
