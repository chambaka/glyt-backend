package tz.co.chambaka.glyt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.BaggageException;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.CustodyEventType;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionType;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.Priority;
import tz.co.chambaka.glyt.repo.BagRepository;
import tz.co.chambaka.glyt.repo.BaggageExceptionRepository;
import tz.co.chambaka.glyt.repo.CustodyEventRepository;
import tz.co.chambaka.glyt.support.ServiceTestData;
import tz.co.chambaka.glyt.support.TestFixtures;
import tz.co.chambaka.glyt.web.dto.GlytDtos.AlertView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BagSummary;
import tz.co.chambaka.glyt.web.dto.GlytDtos.DashboardView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    private static final ZoneId DAR = ZoneId.of("Africa/Dar_es_Salaam");

    @Mock BagRepository bags;
    @Mock CustodyEventRepository custody;
    @Mock BaggageExceptionRepository exceptions;
    @Mock BagQueryService queries;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(bags, custody, exceptions, queries, TestFixtures.appProperties());
        when(exceptions.findByStatusOrderByCreatedAtDesc(ExceptionStatus.OPEN)).thenReturn(List.of());
        when(bags.findByStatusIn(any())).thenReturn(List.of());
        when(bags.findByStatus(BagStatus.READY)).thenReturn(List.of());
        when(bags.findTop12ByOrderByUpdatedAtDesc()).thenReturn(List.of());
    }

    private Bag bag(long id, BagStatus status, String travelDate) {
        Bag bag = ServiceTestData.bag(id, status);
        bag.getBooking().setZanzibarTravelDate(travelDate);
        return bag;
    }

    @Test
    void loadAggregatesCountsAndRecentBags() {
        when(custody.countByEventTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(CustodyEventType.KIA_INTAKE), any(), any())).thenReturn(1L);
        when(custody.countByEventTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(CustodyEventType.AIRLINE_HANDOVER), any(), any())).thenReturn(2L);
        when(custody.countByEventTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(CustodyEventType.ZNZ_RECEIPT), any(), any())).thenReturn(3L);
        when(custody.countByEventTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(CustodyEventType.CUSTOMER_RELEASE), any(), any())).thenReturn(4L);
        when(bags.countByStatus(BagStatus.SEALED)).thenReturn(5L);
        when(bags.countByStatus(BagStatus.HANDED_AIRLINE)).thenReturn(6L);
        when(bags.countByStatus(BagStatus.STORED)).thenReturn(7L);
        when(bags.countByStatus(BagStatus.READY)).thenReturn(8L);
        when(exceptions.countByStatus(ExceptionStatus.OPEN)).thenReturn(9L);
        Bag recent = ServiceTestData.bag(1L, BagStatus.READY);
        BagSummary summary = mock(BagSummary.class);
        when(bags.findTop12ByOrderByUpdatedAtDesc()).thenReturn(List.of(recent));
        when(queries.summary(recent)).thenReturn(summary);

        DashboardView view = service.load();

        assertThat(view.receivedToday()).isEqualTo(1);
        assertThat(view.readyForAirline()).isEqualTo(5);
        assertThat(view.inAirlineCustody()).isEqualTo(6);
        assertThat(view.zanzibarStorage()).isEqualTo(7);
        assertThat(view.readyForCollection()).isEqualTo(8);
        assertThat(view.openExceptions()).isEqualTo(9);
        assertThat(view.handedToday()).isEqualTo(2);
        assertThat(view.receivedZnzToday()).isEqualTo(3);
        assertThat(view.collectedToday()).isEqualTo(4);
        assertThat(view.recent()).containsExactly(summary);
        assertThat(view.alerts()).isEmpty();
    }

    @Test
    void openExceptionsBecomeAlertsWithPriorityBasedLevel() {
        Bag bag = ServiceTestData.bag(1L, BagStatus.STORED);
        BaggageException normal = ServiceTestData.exception(1, bag, ExceptionType.DELAYED, Priority.NORMAL, ExceptionStatus.OPEN);
        BaggageException high = ServiceTestData.exception(2, bag, ExceptionType.DAMAGED, Priority.HIGH, ExceptionStatus.OPEN);
        BaggageException critical = ServiceTestData.exception(3, bag, ExceptionType.SEAL_MISMATCH, Priority.CRITICAL, ExceptionStatus.OPEN);
        when(exceptions.findByStatusOrderByCreatedAtDesc(ExceptionStatus.OPEN)).thenReturn(List.of(normal, high, critical));

        List<AlertView> alerts = service.load().alerts();

        assertThat(alerts).extracting(AlertView::level).containsExactly("AMBER", "RED", "RED");
        assertThat(alerts.get(0).message()).isEqualTo("Bag delayed on GL-1");
        assertThat(alerts.get(1).message()).isEqualTo("Bag damaged on GL-1");
        assertThat(alerts.get(0).bagId()).isEqualTo(1L);
        assertThat(alerts.get(0).bagCode()).isEqualTo("GL-1");
    }

    @Test
    void bagsDueSoonButNotReadyRaiseAlerts() {
        LocalDate today = LocalDate.now(DAR);
        Bag past = bag(1, BagStatus.RECEIVED_KIA, today.minusDays(1).toString());
        Bag dueToday = bag(2, BagStatus.SEALED, today.toString());
        Bag dueTomorrow = bag(3, BagStatus.HANDED_AIRLINE, today.plusDays(1).toString());
        Bag later = bag(4, BagStatus.STORED, today.plusDays(2).toString());
        Bag invalid = bag(5, BagStatus.RECEIVED_ZNZ, "not-a-date");
        when(bags.findByStatusIn(any())).thenReturn(List.of(past, dueToday, dueTomorrow, later, invalid));

        List<AlertView> alerts = service.load().alerts();

        assertThat(alerts).extracting(AlertView::bagCode).containsExactly("GL-1", "GL-2", "GL-3");
        assertThat(alerts).allMatch(a -> a.level().equals("AMBER"));
        assertThat(alerts.get(1).message()).isEqualTo("GL-2 is due in Zanzibar on " + today + " and is not ready");
    }

    @Test
    void staleReadyBagsRaiseUncollectedAlerts() {
        Bag stale = ServiceTestData.bag(1L, BagStatus.READY);
        stale.setUpdatedAt(Instant.now().minusSeconds(3L * 24 * 3600)); // limit is 2 days
        Bag fresh = ServiceTestData.bag(2L, BagStatus.READY);
        fresh.setUpdatedAt(Instant.now().minusSeconds(3600));
        when(bags.findByStatus(BagStatus.READY)).thenReturn(List.of(stale, fresh));

        List<AlertView> alerts = service.load().alerts();

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).message()).isEqualTo("GL-1 is still uncollected");
        assertThat(alerts.get(0).level()).isEqualTo("AMBER");
    }
}
