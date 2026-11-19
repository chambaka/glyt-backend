package tz.co.chambaka.glyt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.domain.AuditLog;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.BaggageException;
import tz.co.chambaka.glyt.domain.CustomerAccount;
import tz.co.chambaka.glyt.domain.Location;
import tz.co.chambaka.glyt.domain.ServiceCatalogItem;
import tz.co.chambaka.glyt.domain.ServicePrice;
import tz.co.chambaka.glyt.domain.StorageLocation;
import tz.co.chambaka.glyt.domain.SupportTicket;
import tz.co.chambaka.glyt.domain.UserAccount;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionType;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.Priority;
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
import tz.co.chambaka.glyt.support.ServiceTestData;
import tz.co.chambaka.glyt.support.TestFixtures;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.AuditView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ExceptionView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.LocationView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.LocationWrite;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ServiceAdminView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ServiceWrite;
import tz.co.chambaka.glyt.web.dto.GlytDtos.StorageAdminView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.StorageWrite;
import tz.co.chambaka.glyt.web.dto.GlytDtos.SupportAdminView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.SupportAssignRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.SupportResolveRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.WorkloadView;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortalDepthServiceTest {

    @Mock StaffContext staff;
    @Mock ExceptionService exceptions;
    @Mock BaggageExceptionRepository exceptionRows;
    @Mock SupportTicketRepository tickets;
    @Mock PaymentTransactionRepository payments;
    @Mock BagRepository bags;
    @Mock UserAccountRepository users;
    @Mock LocationRepository locations;
    @Mock StorageLocationRepository storage;
    @Mock ServiceCatalogRepository services;
    @Mock ServicePriceRepository prices;
    @Mock QuotationRepository quotations;
    @Mock AuditLogRepository audits;
    @Mock Journal journal;

    private PortalDepthService service;
    private UserAccount admin;

    @BeforeEach
    void setUp() {
        service = new PortalDepthService(staff, exceptions, exceptionRows, tickets, payments, bags, users, locations,
                storage, services, prices, quotations, audits, journal);
        admin = TestFixtures.staff(Role.ADMIN, "KIA");
        when(staff.account()).thenReturn(admin);
        when(locations.save(any(Location.class))).thenAnswer(ServiceTestData.assignId(1L));
        when(storage.save(any(StorageLocation.class))).thenAnswer(ServiceTestData.assignId(1L));
        when(services.save(any(ServiceCatalogItem.class))).thenAnswer(ServiceTestData.assignId(1L));
        when(prices.save(any(ServicePrice.class))).thenAnswer(ServiceTestData.assignId(1L));
    }

    private static void api(Runnable action, HttpStatus status, String message) {
        ApiException ex = ServiceTestData.expectApi(action);
        assertThat(ex.status()).isEqualTo(status);
        assertThat(ex.getMessage()).contains(message);
    }

    // ---- workload ----

    private ExceptionView view(long id, boolean overdue, Instant due) {
        return new ExceptionView(id, 1L, "GL-1", "DAMAGED", "Bag damaged", "d", "HIGH", "OPEN", "KIA", "raiser", null,
                null, null, null, "c", due.toString(), overdue, 1L);
    }

    @Test
    void workloadAggregatesQueuesAndStaffLoads() {
        Bag bag = ServiceTestData.bag(1L, BagStatus.STORED);
        UserAccount assignee = TestFixtures.staff(Role.ZNZ_OFFICER, "ZNZ");
        assignee.setId(20L);
        BaggageException first = ServiceTestData.exception(1, bag, ExceptionType.DAMAGED, Priority.HIGH, ExceptionStatus.OPEN);
        first.setAssignedTo(assignee);
        BaggageException second = ServiceTestData.exception(2, bag, ExceptionType.DELAYED, Priority.NORMAL, ExceptionStatus.OPEN);
        BaggageException third = ServiceTestData.exception(3, bag, ExceptionType.OTHER, Priority.CRITICAL, ExceptionStatus.OPEN);
        third.setAssignedTo(assignee);
        when(exceptionRows.findByStatusOrderByCreatedAtDesc(ExceptionStatus.OPEN)).thenReturn(List.of(first, second, third));
        Instant now = Instant.now();
        when(exceptions.toView(first)).thenReturn(view(1, false, now.plusSeconds(1000)));
        when(exceptions.toView(second)).thenReturn(view(2, true, now.plusSeconds(5000)));
        when(exceptions.toView(third)).thenReturn(view(3, true, now.minusSeconds(100)));

        UserAccount customerUser = TestFixtures.staff(Role.CUSTOMER, "KIA");
        customerUser.setId(30L);
        UserAccount inactive = TestFixtures.staff(Role.READONLY, "KIA");
        inactive.setId(31L);
        inactive.setActive(false);
        UserAccount noLocation = TestFixtures.staff(Role.ADMIN, "KIA");
        noLocation.setId(32L);
        noLocation.setLocation(null);
        when(users.findAll()).thenReturn(List.of(admin, assignee, customerUser, inactive, noLocation));

        when(bags.countByStatus(BagStatus.RECEIVED_KIA)).thenReturn(1L);
        when(bags.countByStatus(BagStatus.SEALED)).thenReturn(2L);
        when(bags.countByStatus(BagStatus.BOOKED)).thenReturn(3L);
        when(bags.countByStatus(BagStatus.RECEIVED_ZNZ)).thenReturn(4L);
        when(bags.countByStatus(BagStatus.STORED)).thenReturn(5L);
        when(bags.countByStatus(BagStatus.READY)).thenReturn(6L);
        when(tickets.findByStatusOrderByCreatedAtDesc("OPEN")).thenReturn(List.of(new SupportTicket(), new SupportTicket()));
        when(payments.findByStatusOrderByCreatedAtDesc("PENDING")).thenReturn(List.of(new tz.co.chambaka.glyt.domain.PaymentTransaction()));

        WorkloadView result = service.workload();

        verify(staff).requireStaff();
        assertThat(result.openExceptions()).isEqualTo(3);
        assertThat(result.overdueExceptions()).isEqualTo(2);
        assertThat(result.openSupportTickets()).isEqualTo(2);
        assertThat(result.pendingPayments()).isEqualTo(1);
        assertThat(result.bagsAtKia()).isEqualTo(6);
        assertThat(result.bagsAtZnz()).isEqualTo(15);
        assertThat(result.readyForCollection()).isEqualTo(6);
        assertThat(result.slaQueue()).extracting(ExceptionView::id).containsExactly(3L, 2L, 1L);
        assertThat(result.staffLoads()).hasSize(3);
        assertThat(result.staffLoads().get(0).userId()).isEqualTo(20L);
        assertThat(result.staffLoads().get(0).openExceptions()).isEqualTo(2);
        assertThat(result.staffLoads()).extracting(r -> r.location()).contains("ALL");
    }

    @Test
    void workloadLimitsSlaQueueToFifty() {
        Bag bag = ServiceTestData.bag(1L, BagStatus.STORED);
        List<BaggageException> rows = new ArrayList<>();
        IntStream.range(0, 55).forEach(i -> {
            BaggageException row = ServiceTestData.exception(i, bag, ExceptionType.OTHER, Priority.NORMAL, ExceptionStatus.OPEN);
            rows.add(row);
            when(exceptions.toView(row)).thenReturn(view(i, false, Instant.now().plusSeconds(i)));
        });
        when(exceptionRows.findByStatusOrderByCreatedAtDesc(ExceptionStatus.OPEN)).thenReturn(rows);
        when(users.findAll()).thenReturn(List.of());

        WorkloadView result = service.workload();

        assertThat(result.slaQueue()).hasSize(50);
        assertThat(result.openExceptions()).isEqualTo(55);
        assertThat(result.staffLoads()).isEmpty();
    }

    // ---- locations ----

    @Test
    void listLocationsMapsRows() {
        when(locations.findAllByOrderByCodeAsc()).thenReturn(List.of(TestFixtures.location("KIA")));
        List<LocationView> views = service.listLocations();
        assertThat(views).hasSize(1);
        assertThat(views.get(0).code()).isEqualTo("KIA");
        verify(staff).requireStaff();
    }

    @Test
    void createLocationNormalisesAndAudits() {
        when(locations.findByCodeIgnoreCase("DAR")).thenReturn(Optional.empty());
        LocationView view = service.createLocation(new LocationWrite(" dar ", " Dar Airport ", " airport ", false));
        ArgumentCaptor<Location> saved = ArgumentCaptor.forClass(Location.class);
        verify(locations).save(saved.capture());
        assertThat(saved.getValue().getCode()).isEqualTo("DAR");
        assertThat(saved.getValue().getLocationType()).isEqualTo("AIRPORT");
        assertThat(saved.getValue().isActive()).isFalse();
        assertThat(view.active()).isFalse();
        verify(journal).audit(isNull(), eq(admin), eq("OPS"), eq("location created DAR"), isNull(), eq("Dar Airport"));
    }

    @Test
    void createLocationDefaultsToActive() {
        when(locations.findByCodeIgnoreCase("DAR")).thenReturn(Optional.empty());
        assertThat(service.createLocation(new LocationWrite("dar", "n", "t", null)).active()).isTrue();
    }

    @Test
    void createLocationRejectsDuplicatesAndMissingFields() {
        when(locations.findByCodeIgnoreCase("KIA")).thenReturn(Optional.of(TestFixtures.location("KIA")));
        api(() -> service.createLocation(new LocationWrite("kia", "n", "t", null)), HttpStatus.CONFLICT, "already exists");
        api(() -> service.createLocation(new LocationWrite(null, "n", "t", null)), HttpStatus.BAD_REQUEST, "Code is required");
        api(() -> service.createLocation(new LocationWrite("X1", " ", "t", null)), HttpStatus.BAD_REQUEST, "Name is required");
        api(() -> service.createLocation(new LocationWrite("X1", "n", null, null)), HttpStatus.BAD_REQUEST, "Location type");
    }

    @Test
    void updateLocationAppliesProvidedFields() {
        Location location = TestFixtures.location("KIA");
        when(locations.findById(1L)).thenReturn(Optional.of(location));
        LocationView view = service.updateLocation(1L, new LocationWrite(null, " New name ", " hub ", false));
        assertThat(view.name()).isEqualTo("New name");
        assertThat(view.locationType()).isEqualTo("HUB");
        assertThat(view.active()).isFalse();
    }

    @Test
    void updateLocationIgnoresBlankFields() {
        Location location = TestFixtures.location("KIA");
        when(locations.findById(1L)).thenReturn(Optional.of(location));
        LocationView view = service.updateLocation(1L, new LocationWrite(null, " ", "", null));
        assertThat(view.name()).isEqualTo("KIA Airport");
        assertThat(view.locationType()).isEqualTo("AIRPORT");
        assertThat(view.active()).isTrue();
        LocationView untouched = service.updateLocation(1L, new LocationWrite(null, null, null, null));
        assertThat(untouched.name()).isEqualTo("KIA Airport");
    }

    @Test
    void updateLocationNotFound() {
        when(locations.findById(9L)).thenReturn(Optional.empty());
        api(() -> service.updateLocation(9L, new LocationWrite(null, null, null, null)), HttpStatus.NOT_FOUND, "Location was not found");
    }

    @Test
    void deleteLocationChecksEveryDependency() {
        Location location = TestFixtures.location("KIA");
        when(locations.findById(9L)).thenReturn(Optional.empty());
        api(() -> service.deleteLocation(9L), HttpStatus.NOT_FOUND, "Facility was not found");

        when(locations.findById(1L)).thenReturn(Optional.of(location));
        when(users.countByLocationId(1L)).thenReturn(2L);
        api(() -> service.deleteLocation(1L), HttpStatus.CONFLICT, "2 staff");

        when(users.countByLocationId(1L)).thenReturn(0L);
        when(storage.countByFacilityCodeIgnoreCase("KIA")).thenReturn(3L);
        api(() -> service.deleteLocation(1L), HttpStatus.CONFLICT, "3 storage slot");

        when(storage.countByFacilityCodeIgnoreCase("KIA")).thenReturn(0L);
        when(bags.countByCurrentLocationIgnoreCase("KIA")).thenReturn(4L);
        api(() -> service.deleteLocation(1L), HttpStatus.CONFLICT, "4 bag");
        verify(locations, never()).delete(any());
    }

    @Test
    void deleteLocationSucceedsWhenUnused() {
        Location location = TestFixtures.location("KIA");
        when(locations.findById(1L)).thenReturn(Optional.of(location));
        service.deleteLocation(1L);
        verify(locations).delete(location);
        verify(journal).audit(isNull(), eq(admin), eq("OPS"), eq("location deleted KIA"), eq("KIA"), isNull());
    }

    // ---- storage ----

    private StorageLocation slot(long id, String code) {
        StorageLocation slot = new StorageLocation();
        slot.setId(id);
        slot.setCode(code);
        return slot;
    }

    @Test
    void listStorageReportsOccupancy() {
        StorageLocation free = slot(1, "A1");
        StorageLocation taken = slot(2, "A2");
        Bag bag = ServiceTestData.bag(4L, BagStatus.STORED);
        when(storage.findAllByOrderByCodeAsc()).thenReturn(List.of(free, taken));
        when(bags.findByStorageLocationId(2L)).thenReturn(Optional.of(bag));
        List<StorageAdminView> views = service.listStorage();
        assertThat(views.get(0).occupied()).isFalse();
        assertThat(views.get(0).bagCode()).isNull();
        assertThat(views.get(1).occupied()).isTrue();
        assertThat(views.get(1).bagCode()).isEqualTo("GL-4");
    }

    @Test
    void createStorageDefaultsFacilityToZnz() {
        when(storage.findByCodeIgnoreCase("B1")).thenReturn(Optional.empty());
        StorageAdminView view = service.createStorage(new StorageWrite(" b1 ", null, null));
        assertThat(view.code()).isEqualTo("B1");
        assertThat(view.facilityCode()).isEqualTo("ZNZ");
        assertThat(view.active()).isTrue();
        assertThat(service.createStorage(new StorageWrite("b2", "  ", true)).facilityCode()).isEqualTo("ZNZ");
    }

    @Test
    void createStorageUsesProvidedFacility() {
        when(storage.findByCodeIgnoreCase("B1")).thenReturn(Optional.empty());
        StorageAdminView view = service.createStorage(new StorageWrite("b1", " kia ", false));
        assertThat(view.facilityCode()).isEqualTo("KIA");
        assertThat(view.active()).isFalse();
    }

    @Test
    void createStorageRejectsDuplicateAndMissingCode() {
        when(storage.findByCodeIgnoreCase("B1")).thenReturn(Optional.of(slot(1, "B1")));
        api(() -> service.createStorage(new StorageWrite("b1", null, null)), HttpStatus.CONFLICT, "already exists");
        api(() -> service.createStorage(new StorageWrite(null, null, null)), HttpStatus.BAD_REQUEST, "Code is required");
    }

    @Test
    void updateStorageHandlesFacilityAndActiveFlags() {
        StorageLocation slot = slot(1, "A1");
        when(storage.findById(1L)).thenReturn(Optional.of(slot));
        assertThat(service.updateStorage(1L, new StorageWrite(null, " kia ", false)).facilityCode()).isEqualTo("KIA");
        assertThat(slot.isActive()).isFalse();
        service.updateStorage(1L, new StorageWrite(null, " ", true));
        assertThat(slot.getFacilityCode()).isEqualTo("KIA");
        assertThat(slot.isActive()).isTrue();
        service.updateStorage(1L, new StorageWrite(null, null, null));
        assertThat(slot.isActive()).isTrue();
    }

    @Test
    void updateStorageRefusesToDeactivateOccupiedSlot() {
        StorageLocation slot = slot(1, "A1");
        when(storage.findById(1L)).thenReturn(Optional.of(slot));
        when(bags.existsByStorageLocationId(1L)).thenReturn(true);
        api(() -> service.updateStorage(1L, new StorageWrite(null, null, false)), HttpStatus.BAD_REQUEST, "Occupied storage");
        assertThat(slot.isActive()).isTrue();
        // Re-activating an occupied slot is fine.
        service.updateStorage(1L, new StorageWrite(null, null, true));
    }

    @Test
    void updateStorageNotFound() {
        when(storage.findById(1L)).thenReturn(Optional.empty());
        api(() -> service.updateStorage(1L, new StorageWrite(null, null, null)), HttpStatus.NOT_FOUND, "Storage slot was not found");
    }

    @Test
    void deleteStorageHandlesAllBranches() {
        when(storage.findById(9L)).thenReturn(Optional.empty());
        api(() -> service.deleteStorage(9L), HttpStatus.NOT_FOUND, "Storage slot was not found");

        StorageLocation slot = slot(1, "A1");
        when(storage.findById(1L)).thenReturn(Optional.of(slot));
        when(bags.existsByStorageLocationId(1L)).thenReturn(true);
        api(() -> service.deleteStorage(1L), HttpStatus.CONFLICT, "occupied");

        when(bags.existsByStorageLocationId(1L)).thenReturn(false);
        service.deleteStorage(1L);
        verify(storage).delete(slot);
        verify(journal).audit(isNull(), eq(admin), eq("OPS"), eq("storage deleted A1"), eq("A1"), isNull());
    }

    // ---- services catalogue ----

    private ServiceCatalogItem item(long id) {
        ServiceCatalogItem item = new ServiceCatalogItem();
        item.setId(id);
        item.setCode("STD");
        item.setName("Standard");
        item.setSortOrder(3);
        return item;
    }

    @Test
    void listServicesAdminIncludesPriceWhenPresent() {
        ServiceCatalogItem priced = item(1);
        ServiceCatalogItem unpriced = item(2);
        ServicePrice price = new ServicePrice();
        price.setId(77L);
        price.setCurrency("USD");
        price.setAmountMinor(1500);
        price.setUnit("PER_KG");
        when(services.findAllByOrderBySortOrderAsc()).thenReturn(List.of(priced, unpriced));
        when(prices.findFirstByServiceIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(Optional.of(price));
        when(prices.findFirstByServiceIdAndActiveTrueOrderByIdAsc(2L)).thenReturn(Optional.empty());

        List<ServiceAdminView> views = service.listServicesAdmin();

        assertThat(views.get(0).amountMinor()).isEqualTo(1500L);
        assertThat(views.get(0).currency()).isEqualTo("USD");
        assertThat(views.get(0).priceId()).isEqualTo(77L);
        assertThat(views.get(1).amountMinor()).isNull();
        assertThat(views.get(1).unit()).isNull();
    }

    @Test
    void createServiceWithPriceDefaults() {
        ServiceAdminView view = service.createService(new ServiceWrite(" std ", " Standard ", " desc ", null, null, null, 5000L, null));
        ArgumentCaptor<ServiceCatalogItem> savedItem = ArgumentCaptor.forClass(ServiceCatalogItem.class);
        verify(services).save(savedItem.capture());
        assertThat(savedItem.getValue().getCode()).isEqualTo("STD");
        assertThat(savedItem.getValue().getSortOrder()).isEqualTo(100);
        assertThat(savedItem.getValue().isActive()).isTrue();
        assertThat(savedItem.getValue().getDescription()).isEqualTo("desc");
        ArgumentCaptor<ServicePrice> savedPrice = ArgumentCaptor.forClass(ServicePrice.class);
        verify(prices).save(savedPrice.capture());
        assertThat(savedPrice.getValue().getCurrency()).isEqualTo("TZS");
        assertThat(savedPrice.getValue().getUnit()).isEqualTo("PER_BAG");
        assertThat(savedPrice.getValue().getAmountMinor()).isEqualTo(5000L);
        assertThat(view.code()).isEqualTo("STD");
    }

    @Test
    void createServiceWithExplicitPriceFields() {
        service.createService(new ServiceWrite("a", "A", null, false, 7, " usd ", 100L, " per_kg "));
        ArgumentCaptor<ServicePrice> savedPrice = ArgumentCaptor.forClass(ServicePrice.class);
        verify(prices).save(savedPrice.capture());
        assertThat(savedPrice.getValue().getCurrency()).isEqualTo("USD");
        assertThat(savedPrice.getValue().getUnit()).isEqualTo("PER_KG");
    }

    @Test
    void createServiceSkipsPriceWhenAmountMissingOrZero() {
        service.createService(new ServiceWrite("a", "A", null, null, null, "", null, ""));
        service.createService(new ServiceWrite("b", "B", null, null, null, "", 0L, ""));
        verify(prices, never()).save(any());
    }

    @Test
    void createServiceRequiresCodeAndName() {
        api(() -> service.createService(new ServiceWrite(null, "n", null, null, null, null, null, null)), HttpStatus.BAD_REQUEST, "Code");
        api(() -> service.createService(new ServiceWrite("c", null, null, null, null, null, null, null)), HttpStatus.BAD_REQUEST, "Name");
    }

    @Test
    void updateServiceAppliesAllFieldsAndReplacesPrice() {
        ServiceCatalogItem item = item(1);
        ServicePrice old = new ServicePrice();
        old.setActive(true);
        when(services.findById(1L)).thenReturn(Optional.of(item));
        when(prices.findByServiceIdAndActiveTrue(1L)).thenReturn(List.of(old));

        service.updateService(1L, new ServiceWrite(null, " Premium ", " ", false, 9, "usd", 900L, "per_kg"));

        assertThat(item.getName()).isEqualTo("Premium");
        assertThat(item.getDescription()).isNull();
        assertThat(item.isActive()).isFalse();
        assertThat(item.getSortOrder()).isEqualTo(9);
        assertThat(old.isActive()).isFalse();
        ArgumentCaptor<ServicePrice> created = ArgumentCaptor.forClass(ServicePrice.class);
        verify(prices).save(created.capture());
        assertThat(created.getValue().getCurrency()).isEqualTo("USD");
        assertThat(created.getValue().getUnit()).isEqualTo("PER_KG");
    }

    @Test
    void updateServiceKeepsFieldsWhenNothingProvided() {
        ServiceCatalogItem item = item(1);
        when(services.findById(1L)).thenReturn(Optional.of(item));
        service.updateService(1L, new ServiceWrite(null, " ", null, null, null, null, 0L, null));
        assertThat(item.getName()).isEqualTo("Standard");
        verify(prices, never()).save(any());
        service.updateService(1L, new ServiceWrite(null, null, null, null, null, null, null, null));
        service.updateService(1L, new ServiceWrite(null, null, "text", null, null, " ", 10L, " "));
        assertThat(item.getDescription()).isEqualTo("text");
        verify(prices).save(any(ServicePrice.class));
    }

    @Test
    void updateServiceNotFound() {
        when(services.findById(1L)).thenReturn(Optional.empty());
        api(() -> service.updateService(1L, new ServiceWrite(null, null, null, null, null, null, null, null)),
                HttpStatus.NOT_FOUND, "Service was not found");
    }

    @Test
    void deleteServiceHandlesAllBranches() {
        when(services.findById(9L)).thenReturn(Optional.empty());
        api(() -> service.deleteService(9L), HttpStatus.NOT_FOUND, "Service was not found");

        ServiceCatalogItem item = item(1);
        when(services.findById(1L)).thenReturn(Optional.of(item));
        when(quotations.countByServiceId(1L)).thenReturn(3L);
        api(() -> service.deleteService(1L), HttpStatus.CONFLICT, "3 quotation");

        when(quotations.countByServiceId(1L)).thenReturn(0L);
        service.deleteService(1L);
        verify(prices).deleteByServiceId(1L);
        verify(services).delete(item);
        verify(journal).audit(isNull(), eq(admin), eq("OPS"), eq("service deleted STD"), eq("STD"), isNull());
    }

    // ---- support ----

    private SupportTicket ticket(long id, String status) {
        SupportTicket ticket = new SupportTicket();
        ticket.setId(id);
        ticket.setSubject("Lost bag");
        ticket.setBody("body");
        ticket.setStatus(status);
        return ticket;
    }

    @Test
    void listSupportFiltersByStatus() {
        SupportTicket full = ticket(1, "OPEN");
        CustomerAccount account = new CustomerAccount();
        account.setFullName("Jane");
        account.setEmail("jane@x.com");
        full.setCustomerAccount(account);
        full.setBag(ServiceTestData.bag(2L, BagStatus.READY));
        full.setAssignedTo(admin);
        full.setResolvedAt(Instant.now());
        when(tickets.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(full, ticket(2, "OPEN")));
        when(tickets.findByStatusOrderByCreatedAtDesc("RESOLVED")).thenReturn(List.of());

        List<SupportAdminView> all = service.listSupport(null);
        assertThat(all).hasSize(2);
        assertThat(all.get(0).bagCode()).isEqualTo("GL-2");
        assertThat(all.get(0).customerEmail()).isEqualTo("jane@x.com");
        assertThat(all.get(0).assignedTo()).isEqualTo("Test User");
        assertThat(all.get(0).resolvedAt()).isNotNull();
        assertThat(all.get(1).bagId()).isNull();
        assertThat(all.get(1).customerName()).isNull();
        assertThat(service.listSupport(" ")).hasSize(2);
        assertThat(service.listSupport(" resolved ")).isEmpty();
    }

    @Test
    void assignSupportSetsAndClearsAssignee() {
        SupportTicket ticket = ticket(1, "OPEN");
        when(tickets.findById(1L)).thenReturn(Optional.of(ticket));
        when(users.findById(10L)).thenReturn(Optional.of(admin));

        assertThat(service.assignSupport(1L, new SupportAssignRequest(10L)).assignedTo()).isEqualTo("Test User");
        verify(journal).audit(isNull(), eq(admin), eq("OPS"), eq("support assigned #1"), isNull(), eq("test@golyt.co.tz"));

        assertThat(service.assignSupport(1L, new SupportAssignRequest(null)).assignedTo()).isNull();
        verify(journal).audit(isNull(), eq(admin), eq("OPS"), eq("support assigned #1"), isNull(), eq("unassigned"));
    }

    @Test
    void assignSupportRejectsMissingTicketOrUser() {
        when(tickets.findById(1L)).thenReturn(Optional.empty());
        api(() -> service.assignSupport(1L, new SupportAssignRequest(1L)), HttpStatus.NOT_FOUND, "Support ticket");

        when(tickets.findById(2L)).thenReturn(Optional.of(ticket(2, "OPEN")));
        when(users.findById(99L)).thenReturn(Optional.empty());
        api(() -> service.assignSupport(2L, new SupportAssignRequest(99L)), HttpStatus.BAD_REQUEST, "Assigned user");
    }

    @Test
    void resolveSupportClosesTicket() {
        SupportTicket ticket = ticket(1, "OPEN");
        when(tickets.findById(1L)).thenReturn(Optional.of(ticket));
        SupportAdminView view = service.resolveSupport(1L, new SupportResolveRequest(" done "));
        assertThat(view.status()).isEqualTo("RESOLVED");
        assertThat(view.resolutionNotes()).isEqualTo("done");
        assertThat(view.resolvedAt()).isNotNull();
        verify(journal).audit(isNull(), eq(admin), eq("OPS"), eq("support resolved #1"), eq("OPEN"), eq("done"));
    }

    @Test
    void resolveSupportRejectsMissingClosedAndBlankNotes() {
        when(tickets.findById(1L)).thenReturn(Optional.empty());
        api(() -> service.resolveSupport(1L, new SupportResolveRequest("x")), HttpStatus.NOT_FOUND, "Support ticket");

        when(tickets.findById(2L)).thenReturn(Optional.of(ticket(2, "RESOLVED")));
        api(() -> service.resolveSupport(2L, new SupportResolveRequest("x")), HttpStatus.BAD_REQUEST, "already closed");
        when(tickets.findById(3L)).thenReturn(Optional.of(ticket(3, "CLOSED")));
        api(() -> service.resolveSupport(3L, new SupportResolveRequest("x")), HttpStatus.BAD_REQUEST, "already closed");

        when(tickets.findById(4L)).thenReturn(Optional.of(ticket(4, "OPEN")));
        api(() -> service.resolveSupport(4L, new SupportResolveRequest(" ")), HttpStatus.BAD_REQUEST, "Resolution notes");
    }

    // ---- audits ----

    private AuditLog audit(long id, boolean withBag, boolean withUser) {
        AuditLog row = new AuditLog();
        row.setId(id);
        row.setBag(withBag ? ServiceTestData.bag(1L, BagStatus.READY) : null);
        row.setUser(withUser ? admin : null);
        row.setLocation("KIA");
        row.setAction("said \"hi\"");
        row.setPreviousValue(null);
        row.setNewValue("n");
        return row;
    }

    @Test
    void listAuditsClampsLimit() {
        when(audits.findTopByOrderByCreatedAtDesc(anyInt())).thenReturn(List.of(audit(1, true, true), audit(2, false, false)));

        List<AuditView> views = service.listAudits(10);
        assertThat(views.get(0).userName()).isEqualTo("Test User");
        assertThat(views.get(1).userName()).isEqualTo("system");

        service.listAudits(0);
        verify(audits).findTopByOrderByCreatedAtDesc(1);
        service.listAudits(10_000);
        verify(audits).findTopByOrderByCreatedAtDesc(500);
        verify(audits).findTopByOrderByCreatedAtDesc(10);
    }

    @Test
    void auditsCsvEscapesQuotesAndHandlesNulls() {
        when(audits.findTopByOrderByCreatedAtDesc(2000)).thenReturn(List.of(audit(1, true, true), audit(2, false, false)));

        String csv = service.auditsCsv();

        assertThat(csv).startsWith("id,bag,user,location,action,previous,next,createdAt\n");
        assertThat(csv).contains("1,\"GL-1\",\"Test User\",\"KIA\",\"said \"\"hi\"\"\",,\"n\",");
        assertThat(csv).contains("2,\"\",\"\",\"KIA\"");
    }

    // ---- dueAt ----

    @Test
    void dueAtDependsOnPriority() {
        Bag bag = ServiceTestData.bag(1L, BagStatus.READY);
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        for (Object[] row : new Object[][]{{Priority.CRITICAL, 2L}, {Priority.HIGH, 8L}, {Priority.NORMAL, 24L}}) {
            BaggageException exception = ServiceTestData.exception(1, bag, ExceptionType.OTHER, (Priority) row[0], ExceptionStatus.OPEN);
            exception.setCreatedAt(created);
            assertThat(PortalDepthService.dueAt(exception)).isEqualTo(created.plus(Duration.ofHours((Long) row[1])));
        }
    }
}
