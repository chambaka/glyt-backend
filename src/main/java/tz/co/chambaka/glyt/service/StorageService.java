package tz.co.chambaka.glyt.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.StorageLocation;
import tz.co.chambaka.glyt.repo.BagRepository;
import tz.co.chambaka.glyt.repo.StorageLocationRepository;
import tz.co.chambaka.glyt.web.dto.GlytDtos.StorageSlot;

import java.util.List;

@Service
public class StorageService {

    private final StorageLocationRepository locations;
    private final BagRepository bags;

    public StorageService(StorageLocationRepository locations, BagRepository bags) {
        this.locations = locations;
        this.bags = bags;
    }

    @Transactional(readOnly = true)
    public List<StorageSlot> board() {
        return locations.findAllByOrderByCodeAsc().stream().map(this::slot).toList();
    }

    private StorageSlot slot(StorageLocation location) {
        Bag bag = bags.findByStorageLocationId(location.getId()).orElse(null);
        if (bag == null) {
            return new StorageSlot(location.getId(), location.getCode(), "Available", null, null, null, null, null);
        }
        return new StorageSlot(
                location.getId(),
                location.getCode(),
                "Occupied",
                bag.getId(),
                bag.getCode(),
                bag.getCustomer().getFullName(),
                bag.getUpdatedAt().toString(),
                bag.getBooking().getZanzibarTravelDate()
        );
    }
}
