package tz.co.chambaka.glyt.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.chambaka.glyt.domain.Photo;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.PhotoStage;

import java.util.List;

public interface PhotoRepository extends JpaRepository<Photo, Long> {
    boolean existsByBagIdAndStage(Long bagId, PhotoStage stage);

    List<Photo> findByBagIdOrderByCapturedAtAsc(Long bagId);

    long countByCapturedById(Long userId);
}
