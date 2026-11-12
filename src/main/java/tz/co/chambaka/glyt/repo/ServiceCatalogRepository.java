package tz.co.chambaka.glyt.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.chambaka.glyt.domain.ServiceCatalogItem;

import java.util.List;

public interface ServiceCatalogRepository extends JpaRepository<ServiceCatalogItem, Long> {
    List<ServiceCatalogItem> findByActiveTrueOrderBySortOrderAsc();

    List<ServiceCatalogItem> findAllByOrderBySortOrderAsc();
}
