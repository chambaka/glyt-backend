package tz.co.chambaka.glyt.domain.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class BagStatusTest {

    @ParameterizedTest
    @CsvSource({
            "BOOKED,0",
            "RECEIVED_KIA,1",
            "SEALED,2",
            "HANDED_AIRLINE,3",
            "RECEIVED_ZNZ,4",
            "STORED,5",
            "READY,6",
            "COLLECTED,7",
            "CANCELLED,-1"
    })
    void rankMatchesLifecycleOrder(BagStatus status, int rank) {
        assertThat(status.rank()).isEqualTo(rank);
    }

    @Test
    void ranksStrictlyIncreaseAlongHappyPath() {
        List<BagStatus> path = List.of(
                BagStatus.BOOKED, BagStatus.RECEIVED_KIA, BagStatus.SEALED, BagStatus.HANDED_AIRLINE,
                BagStatus.RECEIVED_ZNZ, BagStatus.STORED, BagStatus.READY, BagStatus.COLLECTED);
        for (int i = 1; i < path.size(); i++) {
            assertThat(path.get(i).rank()).isGreaterThan(path.get(i - 1).rank());
        }
    }

    @Test
    void cancelledRanksBelowEverything() {
        for (BagStatus status : BagStatus.values()) {
            if (status != BagStatus.CANCELLED) {
                assertThat(BagStatus.CANCELLED.rank()).isLessThan(status.rank());
            }
        }
    }

    @Test
    void ranksAreUnique() {
        assertThat(Arrays.stream(BagStatus.values()).map(BagStatus::rank).collect(Collectors.toSet()))
                .hasSize(BagStatus.values().length);
    }

    @ParameterizedTest
    @CsvSource({
            "BOOKED,Booked",
            "RECEIVED_KIA,Received at KIA",
            "SEALED,Sealed / ready for transfer",
            "HANDED_AIRLINE,Handed to airline",
            "RECEIVED_ZNZ,Received in Zanzibar",
            "STORED,Stored in Zanzibar",
            "READY,Ready for collection",
            "COLLECTED,Collected",
            "CANCELLED,Cancelled"
    })
    void staffLabels(BagStatus status, String label) {
        assertThat(status.staffLabel()).isEqualTo(label);
    }

    @ParameterizedTest
    @CsvSource({
            "BOOKED,Booking confirmed",
            "RECEIVED_KIA,Bag received at Kilimanjaro",
            "SEALED,Secured for transfer",
            "HANDED_AIRLINE,On the way to Zanzibar",
            "RECEIVED_ZNZ,Arrived in Zanzibar",
            "STORED,Secured in Zanzibar",
            "READY,Ready for collection",
            "COLLECTED,Collected",
            "CANCELLED,Booking cancelled"
    })
    void customerLabels(BagStatus status, String label) {
        assertThat(status.customerLabel()).isEqualTo(label);
    }

    @ParameterizedTest
    @EnumSource(BagStatus.class)
    void everyStatusHasNonBlankLabels(BagStatus status) {
        assertThat(status.staffLabel()).isNotBlank();
        assertThat(status.customerLabel()).isNotBlank();
        assertThat(BagStatus.valueOf(status.name())).isSameAs(status);
    }

    @Test
    void hasNineStatuses() {
        assertThat(new HashSet<>(Arrays.asList(BagStatus.values()))).hasSize(9);
    }
}
