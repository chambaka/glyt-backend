package tz.co.chambaka.glyt.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.chambaka.glyt.domain.AuditLog;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.BaggageException;
import tz.co.chambaka.glyt.domain.Location;
import tz.co.chambaka.glyt.domain.ServiceCatalogItem;
import tz.co.chambaka.glyt.domain.ServicePrice;
import tz.co.chambaka.glyt.domain.StorageLocation;
import tz.co.chambaka.glyt.domain.SupportTicket;
import tz.co.chambaka.glyt.domain.UserAccount;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionStatus;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.repo.AuditLogRepository;
import tz.co.chambaka.glyt.repo.BagRepository;
import tz.co.chambaka.glyt.repo.BaggageExceptionRepository;
import tz.co.chambaka.glyt.repo.LocationRepository;
import tz.co.chambaka.glyt.repo.PaymentTransactionRepository;
import tz.co.chambaka.glyt.repo.QuotationRepository;
import tz.co.chambaka.glyt.repo.ServiceCatalogRepository;
import tz.co.chambaka.glyt.repo.ServicePriceRepository;
import tz.co.chambaka.glyt.repo.StorageLocationRepository;
import tz.co.chambaka.glyt.repo.SupportTicketRepository;
import tz.co.chambaka.glyt.repo.UserAccountRepository;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.AuditView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.LocationView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.LocationWrite;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ServiceAdminView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ServiceWrite;
import tz.co.chambaka.glyt.web.dto.GlytDtos.StaffLoadRow;
import tz.co.chambaka.glyt.web.dto.GlytDtos.StorageAdminView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.StorageWrite;
import tz.co.chambaka.glyt.web.dto.GlytDtos.SupportAdminView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.SupportAssignRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.SupportResolveRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.WorkloadView;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PortalDepthService {

    private final StaffContext staff;
    private final ExceptionService exceptions;
    private final BaggageExceptionRepository exceptionRows;
    private final SupportTicketRepository tickets;
    private final PaymentTransactionRepository payments;
    private final BagRepository bags;
    private final UserAccountRepository users;
    private final LocationRepository locations;
    private final StorageLocationRepository storage;
    private final ServiceCatalogRepository services;
    private final ServicePriceRepository prices;
    private final QuotationRepository quotations;
    private final AuditLogRepository audits;
    private final Journal journal;

    public PortalDepthService(
            StaffContext staff,
            ExceptionService exceptions,
            BaggageExceptionRepository exceptionRows,
            SupportTicketRepository tickets,
            PaymentTransactionRepository payments,
            BagRepository bags,
            UserAccountRepository users,
            LocationRepository locations,
            StorageLocationRepository storage,
            ServiceCatalogRepository services,
            ServicePriceRepository prices,
            QuotationRepository quotations,
            AuditLogRepository audits,
            Journal journal
    ) {
        this.staff = staff;
        this.exceptions = exceptions;
        this.exceptionRows = exceptionRows;
        this.tickets = tickets;
        this.payments = payments;
        this.bags = bags;
        this.users = users;
        this.locations = locations;
        this.storage = storage;
        this.services = services;
        this.prices = prices;
        this.quotations = quotations;
        this.audits = audits;
        this.journal = journal;
    }

    @Transactional(readOnly = true)
    public WorkloadView workload() {
        staff.requireStaff();
        List<BaggageException> open = exceptionRows.findByStatusOrderByCreatedAtDesc(ExceptionStatus.OPEN);
        var slaQueue = open.stream()
                .map(exceptions::toView)
                .sorted(Comparator.comparing((tz.co.chambaka.glyt.web.dto.GlytDtos.ExceptionView row) -> !row.overdue())
                        .thenComparing(tz.co.chambaka.glyt.web.dto.GlytDtos.ExceptionView::dueAt))
                .limit(50)
                .toList();
        long overdue = slaQueue.stream().filter(tz.co.chambaka.glyt.web.dto.GlytDtos.ExceptionView::overdue).count();

        Map<Long, Long> assigned = new HashMap<>();
        for (BaggageException row : open) {
            if (row.getAssignedTo() != null) {
                assigned.merge(row.getAssignedTo().getId(), 1L, Long::sum);
            }
        }
        List<StaffLoadRow> loads = new ArrayList<>();
        for (UserAccount user : users.findAll()) {
            if (user.getRole() == Role.CUSTOMER || !user.isActive()) {
                continue;
            }
            loads.add(new StaffLoadRow(
                    user.getId(),
                    user.getFullName(),
                    user.getRole().name(),
                    user.locationCode(),
                    assigned.getOrDefault(user.getId(), 0L)
            ));
        }
        loads.sort(Comparator.comparing(StaffLoadRow::openExceptions).reversed());

        long kia = bags.countByStatus(BagStatus.RECEIVED_KIA)
                + bags.countByStatus(BagStatus.SEALED)
                + bags.countByStatus(BagStatus.BOOKED);
        long znz = bags.countByStatus(BagStatus.RECEIVED_ZNZ)
                + bags.countByStatus(BagStatus.STORED)
                + bags.countByStatus(BagStatus.READY);

        return new WorkloadView(
                open.size(),
                overdue,
                tickets.findByStatusOrderByCreatedAtDesc("OPEN").size(),
                payments.findByStatusOrderByCreatedAtDesc("PENDING").size(),
                kia,
                znz,
                bags.countByStatus(BagStatus.READY),
                loads,
                slaQueue
        );
    }

    @Transactional(readOnly = true)
    public List<LocationView> listLocations() {
        staff.requireStaff();
        return locations.findAllByOrderByCodeAsc().stream().map(this::toLocation).toList();
    }

    @Transactional
    public LocationView createLocation(LocationWrite request) {
        staff.require(Role.ADMIN);
        String code = Texts.required(request.code(), "Code").toUpperCase(Locale.ROOT);
        if (locations.findByCodeIgnoreCase(code).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "That location code already exists.");
        }
        Location location = new Location();
        location.setCode(code);
        location.setName(Texts.required(request.name(), "Name"));
        location.setLocationType(Texts.required(request.locationType(), "Location type").toUpperCase(Locale.ROOT));
        location.setActive(request.active() == null || request.active());
        locations.save(location);
        journal.audit(null, staff.account(), "OPS", "location created " + code, null, location.getName());
        return toLocation(location);
    }

    @Transactional
    public LocationView updateLocation(Long id, LocationWrite request) {
        staff.require(Role.ADMIN);
        Location location = locations.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Location was not found."));
        if (request.name() != null && !request.name().isBlank()) {
            location.setName(request.name().trim());
        }
        if (request.locationType() != null && !request.locationType().isBlank()) {
            location.setLocationType(request.locationType().trim().toUpperCase(Locale.ROOT));
        }
        if (request.active() != null) {
            location.setActive(request.active());
        }
        journal.audit(null, staff.account(), "OPS", "location updated " + location.getCode(), null, location.getName());
        return toLocation(location);
    }

    @Transactional
    public void deleteLocation(Long id) {
        staff.require(Role.ADMIN);
        Location location = locations.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Facility was not found."));
        long staffCount = users.countByLocationId(location.getId());
        if (staffCount > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Cannot delete facility " + location.getCode() + ": " + staffCount + " staff account(s) are assigned to it."
            );
        }
        long storageCount = storage.countByFacilityCodeIgnoreCase(location.getCode());
        if (storageCount > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Cannot delete facility " + location.getCode() + ": " + storageCount + " storage slot(s) still reference it."
            );
        }
        long bagCount = bags.countByCurrentLocationIgnoreCase(location.getCode());
        if (bagCount > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Cannot delete facility " + location.getCode() + ": " + bagCount + " bag(s) currently reference this location."
            );
        }
        String code = location.getCode();
        locations.delete(location);
        journal.audit(null, staff.account(), "OPS", "location deleted " + code, code, null);
    }

    @Transactional(readOnly = true)
    public List<StorageAdminView> listStorage() {
        staff.requireStaff();
        return storage.findAllByOrderByCodeAsc().stream().map(this::toStorage).toList();
    }

    @Transactional
    public StorageAdminView createStorage(StorageWrite request) {
        staff.require(Role.ADMIN);
        String code = Texts.required(request.code(), "Code").toUpperCase(Locale.ROOT);
        if (storage.findByCodeIgnoreCase(code).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "That storage code already exists.");
        }
        StorageLocation slot = new StorageLocation();
        slot.setCode(code);
        slot.setFacilityCode(request.facilityCode() == null || request.facilityCode().isBlank()
                ? "ZNZ"
                : request.facilityCode().trim().toUpperCase(Locale.ROOT));
        slot.setActive(request.active() == null || request.active());
        storage.save(slot);
        journal.audit(null, staff.account(), "OPS", "storage created " + code, null, slot.getFacilityCode());
        return toStorage(slot);
    }

    @Transactional
    public StorageAdminView updateStorage(Long id, StorageWrite request) {
        staff.require(Role.ADMIN);
        StorageLocation slot = storage.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Storage slot was not found."));
        if (request.facilityCode() != null && !request.facilityCode().isBlank()) {
            slot.setFacilityCode(request.facilityCode().trim().toUpperCase(Locale.ROOT));
        }
        if (request.active() != null) {
            if (!request.active() && bags.existsByStorageLocationId(slot.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Occupied storage cannot be deactivated.");
            }
            slot.setActive(request.active());
        }
        journal.audit(null, staff.account(), "OPS", "storage updated " + slot.getCode(), null, String.valueOf(slot.isActive()));
        return toStorage(slot);
    }

    @Transactional
    public void deleteStorage(Long id) {
        staff.require(Role.ADMIN);
        StorageLocation slot = storage.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Storage slot was not found."));
        if (bags.existsByStorageLocationId(slot.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Cannot delete storage slot " + slot.getCode() + ": it is occupied by a bag.");
        }
        String code = slot.getCode();
        storage.delete(slot);
        journal.audit(null, staff.account(), "OPS", "storage deleted " + code, code, null);
    }

    @Transactional(readOnly = true)
    public List<ServiceAdminView> listServicesAdmin() {
        staff.requireStaff();
        return services.findAllByOrderBySortOrderAsc().stream().map(this::toService).toList();
    }

    @Transactional
    public ServiceAdminView createService(ServiceWrite request) {
        staff.require(Role.ADMIN);
        ServiceCatalogItem item = new ServiceCatalogItem();
        item.setCode(Texts.required(request.code(), "Code").toUpperCase(Locale.ROOT));
        item.setName(Texts.required(request.name(), "Name"));
        item.setDescription(Texts.optional(request.description()));
        item.setActive(request.active() == null || request.active());
        item.setSortOrder(request.sortOrder() == null ? 100 : request.sortOrder());
        services.save(item);
        if (request.amountMinor() != null && request.amountMinor() > 0) {
            ServicePrice price = new ServicePrice();
            price.setService(item);
            price.setCurrency(request.currency() == null || request.currency().isBlank() ? "TZS" : request.currency().trim().toUpperCase(Locale.ROOT));
            price.setAmountMinor(request.amountMinor());
            price.setUnit(request.unit() == null || request.unit().isBlank() ? "PER_BAG" : request.unit().trim().toUpperCase(Locale.ROOT));
            price.setActive(true);
            prices.save(price);
        }
        journal.audit(null, staff.account(), "OPS", "service created " + item.getCode(), null, item.getName());
        return toService(item);
    }

    @Transactional
    public ServiceAdminView updateService(Long id, ServiceWrite request) {
        staff.require(Role.ADMIN);
        ServiceCatalogItem item = services.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service was not found."));
        if (request.name() != null && !request.name().isBlank()) {
            item.setName(request.name().trim());
        }
        if (request.description() != null) {
            item.setDescription(Texts.optional(request.description()));
        }
        if (request.active() != null) {
            item.setActive(request.active());
        }
        if (request.sortOrder() != null) {
            item.setSortOrder(request.sortOrder());
        }
        if (request.amountMinor() != null && request.amountMinor() > 0) {
            prices.findByServiceIdAndActiveTrue(item.getId()).forEach(price -> price.setActive(false));
            ServicePrice price = new ServicePrice();
            price.setService(item);
            price.setCurrency(request.currency() == null || request.currency().isBlank() ? "TZS" : request.currency().trim().toUpperCase(Locale.ROOT));
            price.setAmountMinor(request.amountMinor());
            price.setUnit(request.unit() == null || request.unit().isBlank() ? "PER_BAG" : request.unit().trim().toUpperCase(Locale.ROOT));
            price.setActive(true);
            prices.save(price);
        }
        journal.audit(null, staff.account(), "OPS", "service updated " + item.getCode(), null, item.getName());
        return toService(item);
    }

    @Transactional
    public void deleteService(Long id) {
        staff.require(Role.ADMIN);
        ServiceCatalogItem item = services.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service was not found."));
        long quoteCount = quotations.countByServiceId(item.getId());
        if (quoteCount > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Cannot delete service " + item.getCode() + ": it has been used on " + quoteCount
                            + " quotation(s). Deactivate it instead."
            );
        }
        String code = item.getCode();
        prices.deleteByServiceId(item.getId());
        services.delete(item);
        journal.audit(null, staff.account(), "OPS", "service deleted " + code, code, null);
    }

    @Transactional(readOnly = true)
    public List<SupportAdminView> listSupport(String status) {
        staff.requireStaff();
        List<SupportTicket> rows = status == null || status.isBlank()
                ? tickets.findAllByOrderByCreatedAtDesc()
                : tickets.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase(Locale.ROOT));
        return rows.stream().map(this::toSupport).toList();
    }

    @Transactional
    public SupportAdminView assignSupport(Long id, SupportAssignRequest request) {
        staff.requireWriter();
        SupportTicket ticket = tickets.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Support ticket was not found."));
        if (request.assignedToId() == null) {
            ticket.setAssignedTo(null);
        } else {
            UserAccount assignee = users.findById(request.assignedToId())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Assigned user was not found."));
            ticket.setAssignedTo(assignee);
        }
        journal.audit(ticket.getBag(), staff.account(), "OPS", "support assigned #" + ticket.getId(), null,
                ticket.getAssignedTo() == null ? "unassigned" : ticket.getAssignedTo().getEmail());
        return toSupport(ticket);
    }

    @Transactional
    public SupportAdminView resolveSupport(Long id, SupportResolveRequest request) {
        staff.requireWriter();
        SupportTicket ticket = tickets.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Support ticket was not found."));
        if ("RESOLVED".equals(ticket.getStatus()) || "CLOSED".equals(ticket.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ticket is already closed.");
        }
        ticket.setStatus("RESOLVED");
        ticket.setResolutionNotes(Texts.required(request.notes(), "Resolution notes"));
        ticket.setResolvedAt(Instant.now());
        journal.audit(ticket.getBag(), staff.account(), "OPS", "support resolved #" + ticket.getId(), "OPEN", ticket.getResolutionNotes());
        return toSupport(ticket);
    }

    @Transactional(readOnly = true)
    public List<AuditView> listAudits(int limit) {
        staff.requireStaff();
        int size = Math.min(Math.max(limit, 1), 500);
        return audits.findTopByOrderByCreatedAtDesc(size).stream().map(this::toAudit).toList();
    }

    @Transactional(readOnly = true)
    public String auditsCsv() {
        staff.requireStaff();
        StringBuilder csv = new StringBuilder("id,bag,user,location,action,previous,next,createdAt\n");
        for (AuditLog row : audits.findTopByOrderByCreatedAtDesc(2000)) {
            csv.append(row.getId()).append(',')
                    .append(csv(row.getBag() == null ? "" : row.getBag().getCode())).append(',')
                    .append(csv(row.getUser() == null ? "" : row.getUser().getFullName())).append(',')
                    .append(csv(row.getLocation())).append(',')
                    .append(csv(row.getAction())).append(',')
                    .append(csv(row.getPreviousValue())).append(',')
                    .append(csv(row.getNewValue())).append(',')
                    .append(csv(row.getCreatedAt().toString())).append('\n');
        }
        return csv.toString();
    }

    private LocationView toLocation(Location location) {
        return new LocationView(location.getId(), location.getCode(), location.getName(), location.getLocationType(), location.isActive());
    }

    private StorageAdminView toStorage(StorageLocation slot) {
        Bag bag = bags.findByStorageLocationId(slot.getId()).orElse(null);
        return new StorageAdminView(
                slot.getId(),
                slot.getCode(),
                slot.getFacilityCode(),
                slot.isActive(),
                bag != null,
                bag == null ? null : bag.getCode()
        );
    }

    private ServiceAdminView toService(ServiceCatalogItem item) {
        var price = prices.findFirstByServiceIdAndActiveTrueOrderByIdAsc(item.getId()).orElse(null);
        return new ServiceAdminView(
                item.getId(),
                item.getCode(),
                item.getName(),
                item.getDescription(),
                item.isActive(),
                item.getSortOrder(),
                price == null ? null : price.getCurrency(),
                price == null ? null : price.getAmountMinor(),
                price == null ? null : price.getUnit(),
                price == null ? null : price.getId()
        );
    }

    private SupportAdminView toSupport(SupportTicket ticket) {
        return new SupportAdminView(
                ticket.getId(),
                ticket.getSubject(),
                ticket.getBody(),
                ticket.getStatus(),
                ticket.getBag() == null ? null : ticket.getBag().getId(),
                ticket.getBag() == null ? null : ticket.getBag().getCode(),
                ticket.getCustomerAccount() == null ? null : ticket.getCustomerAccount().getFullName(),
                ticket.getCustomerAccount() == null ? null : ticket.getCustomerAccount().getEmail(),
                ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getFullName(),
                ticket.getResolutionNotes(),
                ticket.getCreatedAt().toString(),
                ticket.getResolvedAt() == null ? null : ticket.getResolvedAt().toString()
        );
    }

    private AuditView toAudit(AuditLog row) {
        return new AuditView(
                row.getId(),
                row.getUser() == null ? "system" : row.getUser().getFullName(),
                row.getLocation(),
                row.getAction(),
                row.getPreviousValue(),
                row.getNewValue(),
                row.getCreatedAt().toString()
        );
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    static Instant dueAt(BaggageException row) {
        Duration sla = switch (row.getPriority()) {
            case CRITICAL -> Duration.ofHours(2);
            case HIGH -> Duration.ofHours(8);
            case NORMAL -> Duration.ofHours(24);
        };
        return row.getCreatedAt().plus(sla);
    }
}
