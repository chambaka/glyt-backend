package tz.co.chambaka.glyt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.domain.AuditLog;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.BaggageException;
import tz.co.chambaka.glyt.domain.CustodyEvent;
import tz.co.chambaka.glyt.domain.Manifest;
import tz.co.chambaka.glyt.domain.ManifestBag;
import tz.co.chambaka.glyt.domain.Photo;
import tz.co.chambaka.glyt.domain.StorageLocation;
import tz.co.chambaka.glyt.domain.TrackingToken;
import tz.co.chambaka.glyt.domain.UserAccount;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.BagCondition;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.CustodyEventType;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionType;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ManifestStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.PhotoStage;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.Priority;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.repo.AuditLogRepository;
import tz.co.chambaka.glyt.repo.BagRepository;
import tz.co.chambaka.glyt.repo.BaggageExceptionRepository;
import tz.co.chambaka.glyt.repo.CustodyEventRepository;
import tz.co.chambaka.glyt.repo.PhotoRepository;
import tz.co.chambaka.glyt.support.ServiceTestData;
import tz.co.chambaka.glyt.support.TestFixtures;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BagDetail;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BagSummary;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ExceptionView;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BagQueryServiceTest {

    @Mock BagRepository bags;
    @Mock PhotoRepository photos;
    @Mock CustodyEventRepository custody;
    @Mock AuditLogRepository audits;
    @Mock BaggageExceptionRepository exceptions;
    @Mock ExceptionService exceptionService;
    @Mock StaffContext staff;

    private BagQueryService service;

    @BeforeEach
    void setUp() {
        service = new BagQueryService(bags, photos, custody, audits, exceptions, exceptionService, staff, TestFixtures.appProperties());
        as(Role.ADMIN);
    }

    private void as(Role role) {
        UserAccount user = TestFixtures.staff(role, "KIA");
        when(staff.principal()).thenReturn(TestFixtures.authUser(user));
        when(staff.kia()).thenReturn(role == Role.ADMIN || role == Role.KIA_OFFICER);
        when(staff.znz()).thenReturn(role == Role.ADMIN || role == Role.ZNZ_OFFICER);
    }

    private static void api(Runnable action, HttpStatus status, String message) {
        ApiException ex = ServiceTestData.expectApi(action);
        assertThat(ex.status()).isEqualTo(status);
        assertThat(ex.getMessage()).contains(message);
    }

    private Bag withToken(Bag bag, String token, Instant revoked) {
        TrackingToken tracking = new TrackingToken();
        tracking.setBag(bag);
        tracking.setToken(token);
        tracking.setRevokedAt(revoked);
        bag.setTracking(tracking);
        return bag;
    }

    // ---- lookups ----

    @Test
    void detailAndByCodeReturnBagDetail() {
        Bag bag = ServiceTestData.bag(1L, BagStatus.BOOKED);
        when(bags.findById(1L)).thenReturn(Optional.of(bag));
        when(bags.findByCodeIgnoreCase("gl-1")).thenReturn(Optional.of(bag));

        assertThat(service.detail(1L).code()).isEqualTo("GL-1");
        assertThat(service.byCode("gl-1").id()).isEqualTo(1L);
        verify(staff, org.mockito.Mockito.times(2)).requireStaff();
    }

    @Test
    void lookupsFailForUnknownBags() {
        when(bags.findById(9L)).thenReturn(Optional.empty());
        when(bags.findByCodeIgnoreCase("nope")).thenReturn(Optional.empty());
        api(() -> service.detail(9L), HttpStatus.NOT_FOUND, "Bag was not found");
        api(() -> service.byCode("nope"), HttpStatus.NOT_FOUND, "Bag was not found");
    }

    @Test
    void lookupsRequireStaff() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "staff only")).when(staff).requireStaff();
        api(() -> service.detail(1L), HttpStatus.FORBIDDEN, "staff only");
        api(() -> service.byCode("x"), HttpStatus.FORBIDDEN, "staff only");
        api(() -> service.search("x"), HttpStatus.FORBIDDEN, "staff only");
        verify(bags, never()).findById(any());
    }

    @Test
    void searchWithoutTermReturnsMostRecentBags() {
        when(bags.findTop12ByOrderByUpdatedAtDesc()).thenReturn(List.of(ServiceTestData.bag(1L, BagStatus.BOOKED)));
        assertThat(service.search(null)).hasSize(1);
        assertThat(service.search("   ")).hasSize(1);
        verify(bags, never()).search(any(), any());
    }

    @Test
    void searchWithTermUsesLowercaseLikePattern() {
        when(bags.search("%jane%", PageRequest.of(0, 40))).thenReturn(List.of(
                ServiceTestData.bag(1L, BagStatus.BOOKED), ServiceTestData.bag(2L, BagStatus.READY)));
        List<BagSummary> result = service.search("  JANE ");
        assertThat(result).extracting(BagSummary::code).containsExactly("GL-1", "GL-2");
    }

    // ---- toDetail ----

    @Test
    void toDetailAssemblesEveryRelatedRecord() {
        Bag bag = withToken(ServiceTestData.bag(1L, BagStatus.STORED), "tok", null);
        bag.setSealNumber("SEAL-1");
        bag.setCondition(BagCondition.MINOR);
        bag.setOriginalAirlineTag("TAG-9");
        bag.setProhibitedConfirmed(true);
        StorageLocation slot = new StorageLocation();
        slot.setCode("A1");
        bag.setStorageLocation(slot);
        Manifest manifest = new Manifest();
        manifest.setId(4L);
        manifest.setNumber("MF-1");
        manifest.setAirline("Precision Air");
        manifest.setFlightNumber("PW 1");
        manifest.setStatus(ManifestStatus.HANDED_OVER);
        ManifestBag link = new ManifestBag();
        link.setManifest(manifest);
        bag.setManifestLink(link);

        UserAccount user = TestFixtures.staff(Role.KIA_OFFICER, "KIA");
        Photo photo = new Photo();
        photo.setId(11L);
        photo.setStage(PhotoStage.KIA_FULL);
        photo.setCapturedBy(user);
        CustodyEvent withStatuses = new CustodyEvent();
        withStatuses.setId(12L);
        withStatuses.setEventType(CustodyEventType.SECURITY_SEAL);
        withStatuses.setUser(user);
        withStatuses.setLocation("KIA");
        withStatuses.setPreviousStatus(BagStatus.RECEIVED_KIA);
        withStatuses.setNewStatus(BagStatus.SEALED);
        CustodyEvent withoutStatuses = new CustodyEvent();
        withoutStatuses.setId(13L);
        withoutStatuses.setEventType(CustodyEventType.STATUS_CORRECTION);
        withoutStatuses.setUser(user);
        AuditLog audit = new AuditLog();
        audit.setId(14L);
        audit.setUser(user);
        audit.setAction("act");
        BaggageException exception = ServiceTestData.exception(15, bag, ExceptionType.DAMAGED, Priority.HIGH, ExceptionStatus.OPEN);
        ExceptionView exceptionView = mock(ExceptionView.class);
        when(photos.findByBagIdOrderByCapturedAtAsc(1L)).thenReturn(List.of(photo));
        when(custody.findByBagIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(withStatuses, withoutStatuses));
        when(audits.findByBagIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(audit));
        when(exceptions.findByBagIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(exception));
        when(exceptionService.toView(exception)).thenReturn(exceptionView);

        BagDetail detail = service.toDetail(bag);

        assertThat(detail.trackingUrl()).isEqualTo("http://localhost:8080/track/GL-1/tok");
        assertThat(detail.shareMessage()).contains("GL-1").endsWith("http://localhost:8080/track/GL-1/tok");
        assertThat(detail.status()).isEqualTo("STORED");
        assertThat(detail.statusLabel()).isEqualTo("Stored in Zanzibar");
        assertThat(detail.customerStatusLabel()).isEqualTo("Secured in Zanzibar");
        assertThat(detail.condition()).isEqualTo("MINOR");
        assertThat(detail.storageCode()).isEqualTo("A1");
        assertThat(detail.sealNumber()).isEqualTo("SEAL-1");
        assertThat(detail.manifest().number()).isEqualTo("MF-1");
        assertThat(detail.manifest().status()).isEqualTo("HANDED_OVER");
        assertThat(detail.photos()).hasSize(1);
        assertThat(detail.photos().get(0).capturedBy()).isEqualTo("Test User");
        assertThat(detail.custody()).hasSize(2);
        assertThat(detail.custody().get(0).previousStatus()).isEqualTo("RECEIVED_KIA");
        assertThat(detail.custody().get(0).newStatus()).isEqualTo("SEALED");
        assertThat(detail.custody().get(1).previousStatus()).isNull();
        assertThat(detail.custody().get(1).newStatus()).isNull();
        assertThat(detail.audits()).hasSize(1);
        assertThat(detail.exceptions()).containsExactly(exceptionView);
        assertThat(detail.customer().bookingNumber()).isEqualTo("BK-1");
        assertThat(detail.customer().bookingId()).isEqualTo(1L);
    }

    @Test
    void toDetailHandlesBagWithoutOptionalRelations() {
        Bag bag = ServiceTestData.bag(1L, BagStatus.BOOKED);
        bag.setBooking(null);

        BagDetail detail = service.toDetail(bag);

        assertThat(detail.trackingUrl()).isEqualTo("http://localhost:8080/track/GL-1/");
        assertThat(detail.condition()).isNull();
        assertThat(detail.storageCode()).isNull();
        assertThat(detail.manifest()).isNull();
        assertThat(detail.customer().bookingId()).isZero();
        assertThat(detail.customer().bookingNumber()).isNull();
        assertThat(detail.customer().internationalArrivalFlight()).isNull();
        assertThat(detail.customer().notes()).isNull();
        assertThat(detail.photos()).isEmpty();
        assertThat(detail.custody()).isEmpty();
        assertThat(detail.audits()).isEmpty();
        assertThat(detail.exceptions()).isEmpty();
    }

    @Test
    void passportIsOnlyVisibleToAdminAndKiaOfficers() {
        Bag bag = ServiceTestData.bag(1L, BagStatus.BOOKED);
        for (Role role : new Role[]{Role.ADMIN, Role.KIA_OFFICER}) {
            as(role);
            assertThat(service.toDetail(bag).customer().passportNumber()).isEqualTo("AB1234567");
        }
        for (Role role : new Role[]{Role.ZNZ_OFFICER, Role.READONLY}) {
            as(role);
            assertThat(service.toDetail(bag).customer().passportNumber()).isEqualTo("•••••4567");
        }
    }

    @Test
    void revokedTrackingTokenIsOmittedFromUrl() {
        Bag bag = withToken(ServiceTestData.bag(1L, BagStatus.BOOKED), "tok", Instant.now());
        assertThat(service.trackingUrl(bag)).isEqualTo("http://localhost:8080/track/GL-1/");
    }

    @Test
    void trackingUrlStripsTrailingSlashFromBaseUrl() {
        AppProperties.Ops base = TestFixtures.appProperties().ops();
        AppProperties props = ServiceTestData.withOps(new AppProperties.Ops(base.overweightKg(), base.uncollectedDays(),
                "https://golyt.co.tz/", base.pickupName(), base.pickupPlace(), base.pickupHours(), base.pickupPhone(), base.pickupWhatsapp()));
        BagQueryService custom = new BagQueryService(bags, photos, custody, audits, exceptions, exceptionService, staff, props);
        Bag bag = withToken(ServiceTestData.bag(1L, BagStatus.BOOKED), "tok", null);
        assertThat(custom.trackingUrl(bag)).isEqualTo("https://golyt.co.tz/track/GL-1/tok");
    }

    // ---- summary ----

    @Test
    void summaryIncludesStorageManifestAndTravelDate() {
        Bag bag = ServiceTestData.bag(1L, BagStatus.READY);
        StorageLocation slot = new StorageLocation();
        slot.setCode("A1");
        bag.setStorageLocation(slot);
        Manifest manifest = new Manifest();
        manifest.setNumber("MF-1");
        ManifestBag link = new ManifestBag();
        link.setManifest(manifest);
        bag.setManifestLink(link);

        BagSummary summary = service.summary(bag);

        assertThat(summary.storageCode()).isEqualTo("A1");
        assertThat(summary.manifestNumber()).isEqualTo("MF-1");
        assertThat(summary.zanzibarTravelDate()).isEqualTo("2026-01-11");
        assertThat(summary.statusLabel()).isEqualTo("Ready for collection");
        assertThat(summary.phone()).isEqualTo("+255712000111");
    }

    @Test
    void summaryHandlesMissingRelations() {
        Bag bag = ServiceTestData.bag(1L, BagStatus.BOOKED);
        bag.setBooking(null);
        BagSummary summary = service.summary(bag);
        assertThat(summary.storageCode()).isNull();
        assertThat(summary.manifestNumber()).isNull();
        assertThat(summary.zanzibarTravelDate()).isNull();
    }

    // ---- allowed actions ----

    private List<String> actions(BagStatus status, Role role) {
        as(role);
        return service.toDetail(ServiceTestData.bag(1L, status)).allowedActions();
    }

    @Test
    void actionsForKiaStagesDependOnRole() {
        assertThat(actions(BagStatus.BOOKED, Role.KIA_OFFICER)).containsExactly("receive", "cancel", "exception", "share");
        assertThat(actions(BagStatus.BOOKED, Role.ZNZ_OFFICER)).containsExactly("exception", "share");
        assertThat(actions(BagStatus.RECEIVED_KIA, Role.KIA_OFFICER)).containsExactly("seal", "exception", "share");
        assertThat(actions(BagStatus.RECEIVED_KIA, Role.ZNZ_OFFICER)).containsExactly("exception", "share");
        assertThat(actions(BagStatus.SEALED, Role.KIA_OFFICER)).containsExactly("addToManifest", "exception", "share");
        assertThat(actions(BagStatus.SEALED, Role.ZNZ_OFFICER)).containsExactly("exception", "share");
    }

    @Test
    void sealedBagAlreadyOnManifestCannotBeAddedAgain() {
        as(Role.KIA_OFFICER);
        Bag bag = ServiceTestData.bag(1L, BagStatus.SEALED);
        Manifest manifest = new Manifest();
        manifest.setId(1L);
        manifest.setStatus(ManifestStatus.DRAFT);
        ManifestBag link = new ManifestBag();
        link.setManifest(manifest);
        bag.setManifestLink(link);
        assertThat(service.toDetail(bag).allowedActions()).containsExactly("exception", "share");
    }

    @Test
    void actionsForZnzStagesDependOnRole() {
        assertThat(actions(BagStatus.HANDED_AIRLINE, Role.ZNZ_OFFICER)).containsExactly("receiveZnz", "exception", "share");
        assertThat(actions(BagStatus.HANDED_AIRLINE, Role.KIA_OFFICER)).containsExactly("exception", "share");
        assertThat(actions(BagStatus.RECEIVED_ZNZ, Role.ZNZ_OFFICER)).containsExactly("store", "exception", "share");
        assertThat(actions(BagStatus.RECEIVED_ZNZ, Role.KIA_OFFICER)).containsExactly("exception", "share");
        assertThat(actions(BagStatus.STORED, Role.ZNZ_OFFICER)).containsExactly("ready", "exception", "share");
        assertThat(actions(BagStatus.STORED, Role.KIA_OFFICER)).containsExactly("exception", "share");
        assertThat(actions(BagStatus.READY, Role.ZNZ_OFFICER)).containsExactly("release", "exception", "share");
        assertThat(actions(BagStatus.READY, Role.KIA_OFFICER)).containsExactly("exception", "share");
    }

    @Test
    void terminalStatusesOnlyOfferShareAndAdminCorrection() {
        assertThat(actions(BagStatus.COLLECTED, Role.ZNZ_OFFICER)).containsExactly("share");
        assertThat(actions(BagStatus.CANCELLED, Role.KIA_OFFICER)).containsExactly("share");
        assertThat(actions(BagStatus.COLLECTED, Role.ADMIN)).containsExactly("correct", "share");
    }

    @Test
    void adminGetsEveryApplicableActionAndReadonlyCannotRaiseExceptions() {
        assertThat(actions(BagStatus.BOOKED, Role.ADMIN)).containsExactly("receive", "cancel", "exception", "correct", "share");
        assertThat(actions(BagStatus.READY, Role.ADMIN)).containsExactly("release", "exception", "correct", "share");
        assertThat(actions(BagStatus.READY, Role.READONLY)).containsExactly("share");
        assertThat(actions(BagStatus.BOOKED, Role.READONLY)).containsExactly("share");
    }
}
