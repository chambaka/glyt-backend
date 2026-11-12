package tz.co.chambaka.glyt.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.BaggageException;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.CustodyEventType;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.Priority;
import tz.co.chambaka.glyt.repo.BagRepository;
import tz.co.chambaka.glyt.repo.BaggageExceptionRepository;
import tz.co.chambaka.glyt.repo.CustodyEventRepository;
import tz.co.chambaka.glyt.web.dto.GlytDtos.AlertView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.DashboardView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardService {

    private static final ZoneId DAR = ZoneId.of("Africa/Dar_es_Salaam");

    private final BagRepository bags;
    private final CustodyEventRepository custody;
    private final BaggageExceptionRepository exceptions;
    private final BagQueryService queries;
    private final AppProperties properties;

    public DashboardService(
            BagRepository bags,
            CustodyEventRepository custody,
            BaggageExceptionRepository exceptions,
            BagQueryService queries,
            AppProperties properties
    ) {
        this.bags = bags;
        this.custody = custody;
        this.exceptions = exceptions;
        this.queries = queries;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public DashboardView load() {
        // staff-only; enforced at controller via authenticated staff clients
        Instant start = LocalDate.now(DAR).atStartOfDay(DAR).toInstant();
        Instant end = LocalDate.now(DAR).plusDays(1).atStartOfDay(DAR).toInstant();
        List<AlertView> alerts = new ArrayList<>();
        for (BaggageException row : exceptions.findByStatusOrderByCreatedAtDesc(ExceptionStatus.OPEN)) {
            String level = row.getPriority() == Priority.NORMAL ? "AMBER" : "RED";
            alerts.add(new AlertView(
                    level,
                    row.getType().label() + " on " + row.getBag().getCode(),
                    row.getBag().getId(),
                    row.getBag().getCode()
            ));
        }
        LocalDate today = LocalDate.now(DAR);
        LocalDate tomorrow = today.plusDays(1);
        for (Bag bag : bags.findByStatusIn(List.of(BagStatus.RECEIVED_KIA, BagStatus.SEALED, BagStatus.HANDED_AIRLINE, BagStatus.RECEIVED_ZNZ, BagStatus.STORED))) {
            try {
                LocalDate travel = LocalDate.parse(bag.getBooking().getZanzibarTravelDate());
                if (!travel.isAfter(tomorrow)) {
                    alerts.add(new AlertView("AMBER", bag.getCode() + " is due in Zanzibar on " + travel + " and is not ready", bag.getId(), bag.getCode()));
                }
            } catch (DateTimeParseException ignored) {
                // Travel date is validated on intake. Skip a bad historical value.
            }
        }
        Instant cutoff = Instant.now().minusSeconds(properties.ops().uncollectedDays() * 24L * 3600L);
        for (Bag bag : bags.findByStatus(BagStatus.READY)) {
            if (bag.getUpdatedAt().isBefore(cutoff)) {
                alerts.add(new AlertView("AMBER", bag.getCode() + " is still uncollected", bag.getId(), bag.getCode()));
            }
        }
        return new DashboardView(
                custody.countByEventTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(CustodyEventType.KIA_INTAKE, start, end),
                bags.countByStatus(BagStatus.SEALED),
                bags.countByStatus(BagStatus.HANDED_AIRLINE),
                bags.countByStatus(BagStatus.STORED),
                bags.countByStatus(BagStatus.READY),
                exceptions.countByStatus(ExceptionStatus.OPEN),
                custody.countByEventTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(CustodyEventType.AIRLINE_HANDOVER, start, end),
                custody.countByEventTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(CustodyEventType.ZNZ_RECEIPT, start, end),
                custody.countByEventTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(CustodyEventType.CUSTOMER_RELEASE, start, end),
                bags.findTop12ByOrderByUpdatedAtDesc().stream().map(queries::summary).toList(),
                alerts
        );
    }
}
