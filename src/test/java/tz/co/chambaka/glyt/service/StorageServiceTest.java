package tz.co.chambaka.glyt.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.StorageLocation;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.repo.BagRepository;
import tz.co.chambaka.glyt.repo.StorageLocationRepository;
import tz.co.chambaka.glyt.support.ServiceTestData;
import tz.co.chambaka.glyt.web.dto.GlytDtos.StorageSlot;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock StorageLocationRepository locations;
    @Mock BagRepository bags;
    @InjectMocks StorageService service;

    private static StorageLocation slot(long id, String code) {
        StorageLocation slot = new StorageLocation();
        slot.setId(id);
        slot.setCode(code);
        return slot;
    }

    @Test
    void boardShowsAvailableAndOccupiedSlots() {
        Bag bag = ServiceTestData.bag(4L, BagStatus.STORED);
        when(locations.findAllByOrderByCodeAsc()).thenReturn(List.of(slot(1, "A1"), slot(2, "A2")));
        when(bags.findByStorageLocationId(1L)).thenReturn(Optional.empty());
        when(bags.findByStorageLocationId(2L)).thenReturn(Optional.of(bag));

        List<StorageSlot> board = service.board();

        assertThat(board).hasSize(2);
        assertThat(board.get(0).status()).isEqualTo("Available");
        assertThat(board.get(0).bagId()).isNull();
        assertThat(board.get(0).customerName()).isNull();
        assertThat(board.get(1).status()).isEqualTo("Occupied");
        assertThat(board.get(1).bagId()).isEqualTo(4L);
        assertThat(board.get(1).bagCode()).isEqualTo("GL-4");
        assertThat(board.get(1).customerName()).isEqualTo("Jane Traveler");
        assertThat(board.get(1).expectedCollection()).isEqualTo("2026-01-11");
        assertThat(board.get(1).arrivalDate()).isEqualTo(bag.getUpdatedAt().toString());
    }

    @Test
    void emptyBoard() {
        when(locations.findAllByOrderByCodeAsc()).thenReturn(List.of());
        assertThat(service.board()).isEmpty();
    }
}
