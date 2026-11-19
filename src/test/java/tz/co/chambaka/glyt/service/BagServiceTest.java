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
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.BagSeal;
import tz.co.chambaka.glyt.domain.Booking;
import tz.co.chambaka.glyt.domain.StorageLocation;
import tz.co.chambaka.glyt.domain.TrackingToken;
import tz.co.chambaka.glyt.domain.UserAccount;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.BagCondition;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.BagType;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.CustodyEventType;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionState;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionType;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.PhotoStage;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.Priority;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.repo.BagRepository;
import tz.co.chambaka.glyt.repo.BagSealRepository;
import tz.co.chambaka.glyt.repo.BookingRepository;
import tz.co.chambaka.glyt.repo.PhotoRepository;
import tz.co.chambaka.glyt.repo.StorageLocationRepository;
import tz.co.chambaka.glyt.repo.TrackingTokenRepository;
import tz.co.chambaka.glyt.support.ServiceTestData;
import tz.co.chambaka.glyt.support.TestFixtures;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BagDetail;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BagInput;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CorrectRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CreateBagRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ReceiveRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ReleaseRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.SealRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ZnzReceiveRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BagServiceTest {

    @Mock BagRepository bags;
    @Mock BookingRepository bookings;
    @Mock BagSealRepository seals;
    @Mock TrackingTokenRepository trackingTokens;
    @Mock PhotoRepository photos;
    @Mock StorageLocationRepository storage;
    @Mock StaffContext staff;
    @Mock Journal journal;
    @Mock CodeGenerator codes;
    @Mock ExceptionService exceptions;
    @Mock BagQueryService queries;
    @Mock NotificationService notifications;

    private BagService service;
    private UserAccount actor;
    private BagDetail detail;

    @BeforeEach
    void setUp() {
        service = new BagService(bags, bookings, seals, trackingTokens, photos, storage, staff, journal, codes,
                exceptions, queries, TestFixtures.appProperties(), notifications);
        actor = TestFixtures.staff(Role.KIA_OFFICER, "KIA");
        detail = mock(BagDetail.class);
        when(staff.account()).thenReturn(actor);
        when(queries.toDetail(any())).thenReturn(detail);
        when(queries.trackingUrl(any())).thenReturn("http://t/url");
        when(codes.next("bag", "BG-", 6)).thenReturn("BG-000001");
    }

    private static void api(Runnable action, HttpStatus status, String message) {
        ApiException ex = ServiceTestData.expectApi(action);
        assertThat(ex.status()).isEqualTo(status);
        assertThat(ex.getMessage()).contains(message);
    }

    private Bag bag(BagStatus status) {
        Bag bag = ServiceTestData.bag(1L, status);
        when(bags.findById(1L)).thenReturn(Optional.of(bag));
        return bag;
    }

    private void photos(PhotoStage... stages) {
        for (PhotoStage stage : stages) {
            when(photos.existsByBagIdAndStage(1L, stage)).thenReturn(true);
        }
    }

    private StorageLocation slot(long id, String code) {
        StorageLocation slot = new StorageLocation();
        slot.setId(id);
        slot.setCode(code);
        when(storage.findByCodeIgnoreCase(code)).thenReturn(Optional.of(slot));
        when(storage.findByCodeIgnoreCase(code.toLowerCase())).thenReturn(Optional.of(slot));
        return slot;
    }

    // ---- create ----

    private static BagInput input() {
        return new BagInput("suitcase", " Black ", 14.5, " Hard shell ", " TAG-1 ");
    }

    private Booking openBooking() {
        Booking booking = ServiceTestData.booking(1L, ServiceTestData.customer(1L));
        when(bookings.findById(1L)).thenReturn(Optional.of(booking));
        return booking;
    }

    @Test
    void createRegistersBagWithTrackingTokenAndAudit() {
        Booking booking = openBooking();
        when(bags.save(any(Bag.class))).thenAnswer(ServiceTestData.assignId(7L));

        BagDetail result = service.create(new CreateBagRequest(1L, input()));

        assertThat(result).isSameAs(detail);
        ArgumentCaptor<Bag> saved = ArgumentCaptor.forClass(Bag.class);
        verify(bags).save(saved.capture());
        Bag bag = saved.getValue();
        assertThat(bag.getCode()).isEqualTo("BG-000001");
        assertThat(bag.getBooking()).isSameAs(booking);
        assertThat(bag.getCustomer()).isSameAs(booking.getCustomer());
        assertThat(bag.getStatus()).isEqualTo(BagStatus.BOOKED);
        assertThat(bag.getExceptionState()).isEqualTo(ExceptionState.NONE);
        assertThat(bag.getCurrentLocation()).isEqualTo("KIA");
        assertThat(bag.getBagType()).isEqualTo(BagType.SUITCASE);
        assertThat(bag.getColor()).isEqualTo("Black");
        assertThat(bag.getWeightKg()).isEqualTo(14.5);
        assertThat(bag.getDescription()).isEqualTo("Hard shell");
        assertThat(bag.getOriginalAirlineTag()).isEqualTo("TAG-1");
        ArgumentCaptor<TrackingToken> token = ArgumentCaptor.forClass(TrackingToken.class);
        verify(trackingTokens).save(token.capture());
        assertThat(token.getValue().getToken()).hasSize(36).matches("[0-9a-f]+");
        assertThat(token.getValue().getBag()).isSameAs(bag);
        assertThat(bag.getTracking()).isSameAs(token.getValue());
        verify(journal).audit(bag, actor, "KIA", "bag registered", null, "BG-000001");
    }

    @Test
    void createRejectsMissingBagOrBookingId() {
        api(() -> service.create(new CreateBagRequest(1L, null)), HttpStatus.BAD_REQUEST, "Bag details are required");
        api(() -> service.create(new CreateBagRequest(null, input())), HttpStatus.BAD_REQUEST, "Booking is required");
    }

    @Test
    void createRejectsUnknownOrClosedBooking() {
        when(bookings.findById(9L)).thenReturn(Optional.empty());
        api(() -> service.create(new CreateBagRequest(9L, input())), HttpStatus.NOT_FOUND, "Booking was not found");
        Booking booking = openBooking();
        booking.setStatus("CLOSED");
        api(() -> service.create(new CreateBagRequest(1L, input())), HttpStatus.BAD_REQUEST, "confirmed (paid) open bookings");
        verify(bags, never()).save(any());
    }

    @Test
    void createValidatesBagFields() {
        openBooking();
        api(() -> service.create(new CreateBagRequest(1L, new BagInput(null, "Black", 10.0, "d", null))),
                HttpStatus.BAD_REQUEST, "Bag type is required");
        api(() -> service.create(new CreateBagRequest(1L, new BagInput("trunk", "Black", 10.0, "d", null))),
                HttpStatus.BAD_REQUEST, "Bag type is not recognised");
        api(() -> service.create(new CreateBagRequest(1L, new BagInput("suitcase", " ", 10.0, "d", null))),
                HttpStatus.BAD_REQUEST, "Bag colour is required");
        api(() -> service.create(new CreateBagRequest(1L, new BagInput("suitcase", "Black", null, "d", null))),
                HttpStatus.BAD_REQUEST, "Weight is required");
        api(() -> service.create(new CreateBagRequest(1L, new BagInput("suitcase", "Black", 0.0, "d", null))),
                HttpStatus.BAD_REQUEST, "Weight is required");
        api(() -> service.create(new CreateBagRequest(1L, new BagInput("suitcase", "Black", 10.0, "", null))),
                HttpStatus.BAD_REQUEST, "Bag description is required");
    }

    @Test
    void createOmitsBlankAirlineTag() {
        openBooking();
        service.create(new CreateBagRequest(1L, new BagInput("suitcase", "Black", 10.0, "d", "  ")));
        ArgumentCaptor<Bag> saved = ArgumentCaptor.forClass(Bag.class);
        verify(bags).save(saved.capture());
        assertThat(saved.getValue().getOriginalAirlineTag()).isNull();
    }

    @Test
    void createRequiresKiaOrAdminRole() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "no")).when(staff).require(Role.ADMIN, Role.KIA_OFFICER);
        api(() -> service.create(new CreateBagRequest(1L, input())), HttpStatus.FORBIDDEN, "no");
        api(() -> service.receive(1L, new ReceiveRequest(10.0, "good", true, null)), HttpStatus.FORBIDDEN, "no");
        api(() -> service.seal(1L, new SealRequest("S", "good", false, null)), HttpStatus.FORBIDDEN, "no");
        api(() -> service.cancel(1L), HttpStatus.FORBIDDEN, "no");
        verify(bags, never()).findById(any());
    }

    @Test
    void znzStepsRequireZnzOrAdminRole() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "no")).when(staff).require(Role.ADMIN, Role.ZNZ_OFFICER);
        api(() -> service.receiveZnz(1L, new ZnzReceiveRequest("S", true, false, "good", null)), HttpStatus.FORBIDDEN, "no");
        api(() -> service.store(1L, "A1"), HttpStatus.FORBIDDEN, "no");
        api(() -> service.ready(1L), HttpStatus.FORBIDDEN, "no");
        api(() -> service.release(1L, new ReleaseRequest("x", true, null, true)), HttpStatus.FORBIDDEN, "no");
        verify(bags, never()).findById(any());
    }

    @Test
    void correctRequiresAdminRole() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "no")).when(staff).require(Role.ADMIN);
        api(() -> service.correct(1L, new CorrectRequest("BOOKED", "why", null)), HttpStatus.FORBIDDEN, "no");
    }

    // ---- receive ----

    @Test
    void receiveMovesBagToKiaWithGoodCondition() {
        Bag bag = bag(BagStatus.BOOKED);
        photos(PhotoStage.KIA_FULL, PhotoStage.KIA_TAG);

        BagDetail result = service.receive(1L, new ReceiveRequest(18.2, " good ", true, null));

        assertThat(result).isSameAs(detail);
        assertThat(bag.getStatus()).isEqualTo(BagStatus.RECEIVED_KIA);
        assertThat(bag.getWeightKg()).isEqualTo(18.2);
        assertThat(bag.getCondition()).isEqualTo(BagCondition.GOOD);
        assertThat(bag.isProhibitedConfirmed()).isTrue();
        assertThat(bag.getCurrentLocation()).isEqualTo("KIA");
        verify(journal).custody(bag, actor, CustodyEventType.KIA_INTAKE, "KIA", null, null, BagStatus.BOOKED, BagStatus.RECEIVED_KIA);
        verify(journal).audit(bag, actor, "KIA", "bag received at KIA", "BOOKED", "RECEIVED_KIA");
        verify(exceptions, never()).open(any(), any(), any(), any(), any());
    }

    @Test
    void receiveDamagedBagOpensDamageException() {
        Bag bag = bag(BagStatus.BOOKED);
        photos(PhotoStage.KIA_FULL, PhotoStage.KIA_TAG);

        service.receive(1L, new ReceiveRequest(12.0, "damaged", true, " Cracked handle "));

        assertThat(bag.getCondition()).isEqualTo(BagCondition.DAMAGED);
        verify(exceptions).open(bag, ExceptionType.DAMAGED, Priority.HIGH, "Cracked handle", actor);
    }

    @Test
    void receiveValidatesStageAndInput() {
        Bag bag = bag(BagStatus.RECEIVED_KIA);
        api(() -> service.receive(1L, new ReceiveRequest(10.0, "good", true, null)),
                HttpStatus.BAD_REQUEST, "This bag is Received at KIA. The next step does not match that stage.");
        bag.setStatus(BagStatus.BOOKED);
        api(() -> service.receive(1L, new ReceiveRequest(10.0, "good", false, null)),
                HttpStatus.BAD_REQUEST, "must confirm the bag contains personal effects");
        api(() -> service.receive(1L, new ReceiveRequest(10.0, "good", true, null)),
                HttpStatus.BAD_REQUEST, "Full baggage photo is required");
        photos(PhotoStage.KIA_FULL);
        api(() -> service.receive(1L, new ReceiveRequest(10.0, "good", true, null)),
                HttpStatus.BAD_REQUEST, "Baggage tag photo is required");
        photos(PhotoStage.KIA_TAG);
        api(() -> service.receive(1L, new ReceiveRequest(null, "good", true, null)), HttpStatus.BAD_REQUEST, "Weight is required");
        api(() -> service.receive(1L, new ReceiveRequest(-1.0, "good", true, null)), HttpStatus.BAD_REQUEST, "Weight is required");
        api(() -> service.receive(1L, new ReceiveRequest(10.0, null, true, null)), HttpStatus.BAD_REQUEST, "Bag condition is required");
        api(() -> service.receive(1L, new ReceiveRequest(10.0, "wrecked", true, null)), HttpStatus.BAD_REQUEST, "not recognised");
        api(() -> service.receive(1L, new ReceiveRequest(10.0, "damaged", true, " ")), HttpStatus.BAD_REQUEST, "Damage note is required");
        assertThat(bag.getStatus()).isEqualTo(BagStatus.BOOKED);
    }

    @Test
    void unknownBagIsNotFound() {
        when(bags.findById(99L)).thenReturn(Optional.empty());
        api(() -> service.receive(99L, new ReceiveRequest(10.0, "good", true, null)), HttpStatus.NOT_FOUND, "Bag was not found");
        api(() -> service.seal(99L, new SealRequest("S", "good", false, null)), HttpStatus.NOT_FOUND, "Bag was not found");
        api(() -> service.receiveZnz(99L, new ZnzReceiveRequest("S", true, false, "good", null)), HttpStatus.NOT_FOUND, "Bag was not found");
        api(() -> service.store(99L, "A1"), HttpStatus.NOT_FOUND, "Bag was not found");
        api(() -> service.ready(99L), HttpStatus.NOT_FOUND, "Bag was not found");
        api(() -> service.release(99L, new ReleaseRequest("x", true, null, true)), HttpStatus.NOT_FOUND, "Bag was not found");
        api(() -> service.cancel(99L), HttpStatus.NOT_FOUND, "Bag was not found");
        api(() -> service.correct(99L, new CorrectRequest("BOOKED", "r", null)), HttpStatus.NOT_FOUND, "Bag was not found");
    }

    // ---- seal ----

    @Test
    void sealAppliesSealAndRetiresPreviousOnes() {
        Bag bag = bag(BagStatus.RECEIVED_KIA);
        photos(PhotoStage.KIA_SEAL);
        BagSeal old = new BagSeal();
        old.setCurrentSeal(true);
        when(seals.findByBagIdAndCurrentSealTrue(1L)).thenReturn(List.of(old));

        service.seal(1L, new SealRequest(" ab-123 ", "minor", false, null));

        assertThat(old.isCurrentSeal()).isFalse();
        ArgumentCaptor<BagSeal> applied = ArgumentCaptor.forClass(BagSeal.class);
        verify(seals).save(applied.capture());
        assertThat(applied.getValue().getSealNumber()).isEqualTo("AB-123");
        assertThat(applied.getValue().isCurrentSeal()).isTrue();
        assertThat(applied.getValue().getAppliedBy()).isSameAs(actor);
        assertThat(applied.getValue().getBag()).isSameAs(bag);
        assertThat(bag.getSealNumber()).isEqualTo("AB-123");
        assertThat(bag.getCondition()).isEqualTo(BagCondition.MINOR);
        assertThat(bag.getStatus()).isEqualTo(BagStatus.SEALED);
        verify(journal).custody(bag, actor, CustodyEventType.SECURITY_SEAL, "KIA", null, "AB-123", BagStatus.RECEIVED_KIA, BagStatus.SEALED);
        verify(journal).audit(bag, actor, "KIA", "seal number changed from blank to AB-123", "blank", "AB-123");
        verify(exceptions, never()).open(any(), any(), any(), any(), any());
    }

    @Test
    void sealRecordsPreviousSealNumberInAudit() {
        Bag bag = bag(BagStatus.RECEIVED_KIA);
        bag.setSealNumber("OLD-1");
        photos(PhotoStage.KIA_SEAL);
        service.seal(1L, new SealRequest("new-2", "good", false, null));
        verify(journal).audit(bag, actor, "KIA", "seal number changed from OLD-1 to NEW-2", "OLD-1", "NEW-2");
    }

    @Test
    void sealOverweightAcceptedOpensException() {
        Bag bag = bag(BagStatus.RECEIVED_KIA);
        bag.setWeightKg(25.0);
        photos(PhotoStage.KIA_SEAL);
        service.seal(1L, new SealRequest("S1", "good", true, null));
        verify(exceptions).open(bag, ExceptionType.OVERWEIGHT, Priority.HIGH, "Bag weight 25.0 kg exceeds 20 kg.", actor);
        assertThat(bag.getStatus()).isEqualTo(BagStatus.SEALED);
    }

    @Test
    void sealOverweightWithoutAcceptanceIsRejected() {
        Bag bag = bag(BagStatus.RECEIVED_KIA);
        bag.setWeightKg(25.0);
        photos(PhotoStage.KIA_SEAL);
        api(() -> service.seal(1L, new SealRequest("S1", "good", false, null)), HttpStatus.BAD_REQUEST, "exceeds the 20 kg limit");
        assertThat(bag.getStatus()).isEqualTo(BagStatus.RECEIVED_KIA);
    }

    @Test
    void sealDamagedBagOpensDamageException() {
        Bag bag = bag(BagStatus.RECEIVED_KIA);
        photos(PhotoStage.KIA_SEAL);
        service.seal(1L, new SealRequest("S1", "damaged", false, " Torn zip "));
        verify(exceptions).open(bag, ExceptionType.DAMAGED, Priority.HIGH, "Torn zip", actor);
    }

    @Test
    void sealValidatesStageAndInput() {
        Bag bag = bag(BagStatus.BOOKED);
        api(() -> service.seal(1L, new SealRequest("S1", "good", false, null)), HttpStatus.BAD_REQUEST, "does not match that stage");
        bag.setStatus(BagStatus.RECEIVED_KIA);
        api(() -> service.seal(1L, new SealRequest("S1", "good", false, null)), HttpStatus.BAD_REQUEST, "Security seal photo is required");
        photos(PhotoStage.KIA_SEAL);
        api(() -> service.seal(1L, new SealRequest(" ", "good", false, null)), HttpStatus.BAD_REQUEST, "Seal number is required");
        api(() -> service.seal(1L, new SealRequest("S1", "bad", false, null)), HttpStatus.BAD_REQUEST, "not recognised");
        api(() -> service.seal(1L, new SealRequest("S1", "damaged", false, null)), HttpStatus.BAD_REQUEST, "Damage note is required");
        verify(seals, never()).save(any());
    }

    // ---- receiveZnz ----

    @Test
    void receiveZnzCleanArrivalNeedsNoException() {
        Bag bag = bag(BagStatus.HANDED_AIRLINE);
        bag.setSealNumber("ab-1");
        photos(PhotoStage.ZNZ_CONDITION, PhotoStage.ZNZ_SEAL);

        service.receiveZnz(1L, new ZnzReceiveRequest(" AB-1 ", true, false, "good", null));

        assertThat(bag.getStatus()).isEqualTo(BagStatus.RECEIVED_ZNZ);
        assertThat(bag.getCurrentLocation()).isEqualTo("ZNZ");
        assertThat(bag.getCondition()).isEqualTo(BagCondition.GOOD);
        verify(journal).custody(bag, actor, CustodyEventType.ZNZ_RECEIPT, "ZNZ", "Seal observed AB-1", "AB-1", BagStatus.HANDED_AIRLINE, BagStatus.RECEIVED_ZNZ);
        verify(journal).audit(bag, actor, "ZNZ", "bag received in Zanzibar", "HANDED_AIRLINE", "RECEIVED_ZNZ");
        verify(exceptions, never()).open(any(), any(), any(), any(), any());
    }

    @Test
    void receiveZnzSealMismatchOpensCriticalException() {
        Bag bag = bag(BagStatus.HANDED_AIRLINE);
        bag.setSealNumber("AB-1");
        photos(PhotoStage.ZNZ_CONDITION, PhotoStage.ZNZ_SEAL);

        service.receiveZnz(1L, new ZnzReceiveRequest("zz-9", true, false, "good", " Different tag "));

        verify(exceptions).open(bag, ExceptionType.SEAL_MISMATCH, Priority.CRITICAL, "Expected AB-1, observed ZZ-9. Different tag", actor);
        verify(exceptions, never()).open(any(), eq(ExceptionType.SEAL_DAMAGED), any(), any(), any());
    }

    @Test
    void receiveZnzBagWithoutRecordedSealCountsAsMismatch() {
        Bag bag = bag(BagStatus.HANDED_AIRLINE);
        photos(PhotoStage.ZNZ_CONDITION, PhotoStage.ZNZ_SEAL);
        service.receiveZnz(1L, new ZnzReceiveRequest("ZZ-9", true, false, "good", "no seal on file"));
        verify(exceptions).open(bag, ExceptionType.SEAL_MISMATCH, Priority.CRITICAL, "Expected , observed ZZ-9. no seal on file", actor);
    }

    @Test
    void receiveZnzDamagedSealAndBagOpenBothExceptions() {
        Bag bag = bag(BagStatus.HANDED_AIRLINE);
        bag.setSealNumber("AB-1");
        photos(PhotoStage.ZNZ_CONDITION, PhotoStage.ZNZ_SEAL);

        service.receiveZnz(1L, new ZnzReceiveRequest("AB-1", false, true, "damaged", "Broken in transit"));

        verify(exceptions).open(bag, ExceptionType.SEAL_DAMAGED, Priority.CRITICAL, "Broken in transit", actor);
        verify(exceptions).open(bag, ExceptionType.DAMAGED, Priority.HIGH, "Broken in transit", actor);
        verify(exceptions, never()).open(any(), eq(ExceptionType.SEAL_MISMATCH), any(), any(), any());
    }

    @Test
    void receiveZnzDamagedBagAloneOpensDamageException() {
        Bag bag = bag(BagStatus.HANDED_AIRLINE);
        bag.setSealNumber("AB-1");
        photos(PhotoStage.ZNZ_CONDITION, PhotoStage.ZNZ_SEAL);
        service.receiveZnz(1L, new ZnzReceiveRequest("AB-1", true, false, "damaged", "Dented"));
        verify(exceptions).open(bag, ExceptionType.DAMAGED, Priority.HIGH, "Dented", actor);
    }

    @Test
    void receiveZnzValidatesStageAndInput() {
        Bag bag = bag(BagStatus.SEALED);
        bag.setSealNumber("AB-1");
        api(() -> service.receiveZnz(1L, new ZnzReceiveRequest("AB-1", true, false, "good", null)), HttpStatus.BAD_REQUEST, "does not match that stage");
        bag.setStatus(BagStatus.HANDED_AIRLINE);
        api(() -> service.receiveZnz(1L, new ZnzReceiveRequest("AB-1", true, false, "good", null)), HttpStatus.BAD_REQUEST, "Arrival condition photo is required");
        photos(PhotoStage.ZNZ_CONDITION);
        api(() -> service.receiveZnz(1L, new ZnzReceiveRequest("AB-1", true, false, "good", null)), HttpStatus.BAD_REQUEST, "Arrival seal photo is required");
        photos(PhotoStage.ZNZ_SEAL);
        api(() -> service.receiveZnz(1L, new ZnzReceiveRequest("AB-1", false, false, "good", null)), HttpStatus.BAD_REQUEST, "intact or damaged");
        api(() -> service.receiveZnz(1L, new ZnzReceiveRequest(" ", true, false, "good", null)), HttpStatus.BAD_REQUEST, "Seal observed is required");
        api(() -> service.receiveZnz(1L, new ZnzReceiveRequest("AB-1", true, false, "huh", null)), HttpStatus.BAD_REQUEST, "not recognised");
        api(() -> service.receiveZnz(1L, new ZnzReceiveRequest("OTHER", true, false, "good", " ")), HttpStatus.BAD_REQUEST, "Exception note is required");
        api(() -> service.receiveZnz(1L, new ZnzReceiveRequest("AB-1", false, true, "good", null)), HttpStatus.BAD_REQUEST, "Exception note is required");
        api(() -> service.receiveZnz(1L, new ZnzReceiveRequest("AB-1", true, false, "damaged", null)), HttpStatus.BAD_REQUEST, "Exception note is required");
        assertThat(bag.getStatus()).isEqualTo(BagStatus.HANDED_AIRLINE);
    }

    // ---- store ----

    @Test
    void storePlacesBagInFreeSlot() {
        Bag bag = bag(BagStatus.RECEIVED_ZNZ);
        bag.setSealNumber("AB-1");
        StorageLocation slot = slot(3L, "A1");

        service.store(1L, "a1");

        assertThat(bag.getStatus()).isEqualTo(BagStatus.STORED);
        assertThat(bag.getStorageLocation()).isSameAs(slot);
        assertThat(bag.getCurrentLocation()).isEqualTo("ZNZ");
        verify(journal).custody(bag, actor, CustodyEventType.STORAGE, "ZNZ", "Stored at A1", "AB-1", BagStatus.RECEIVED_ZNZ, BagStatus.STORED);
        verify(journal).audit(bag, actor, "ZNZ", "storage assigned", null, "A1");
    }

    @Test
    void storeAllowsSlotAlreadyHeldBySameBag() {
        Bag bag = bag(BagStatus.RECEIVED_ZNZ);
        StorageLocation slot = slot(3L, "A1");
        when(bags.findByStorageLocationId(3L)).thenReturn(Optional.of(bag));
        service.store(1L, "A1");
        assertThat(bag.getStorageLocation()).isSameAs(slot);
    }

    @Test
    void storeRejectsOccupiedSlotUnknownSlotAndBlankCode() {
        Bag bag = bag(BagStatus.RECEIVED_ZNZ);
        slot(3L, "A1");
        Bag other = ServiceTestData.bag(2L, BagStatus.STORED);
        when(bags.findByStorageLocationId(3L)).thenReturn(Optional.of(other));
        api(() -> service.store(1L, "A1"), HttpStatus.CONFLICT, "A1 already holds GL-2.");
        when(storage.findByCodeIgnoreCase("Z9")).thenReturn(Optional.empty());
        api(() -> service.store(1L, "Z9"), HttpStatus.BAD_REQUEST, "Storage location was not found");
        api(() -> service.store(1L, " "), HttpStatus.BAD_REQUEST, "Storage location is required");
        bag.setStatus(BagStatus.BOOKED);
        api(() -> service.store(1L, "A1"), HttpStatus.BAD_REQUEST, "does not match that stage");
    }

    // ---- ready ----

    @Test
    void readyMarksBagAndNotifiesCustomer() {
        Bag bag = bag(BagStatus.STORED);
        bag.setSealNumber("AB-1");

        service.ready(1L);

        assertThat(bag.getStatus()).isEqualTo(BagStatus.READY);
        verify(journal).custody(bag, actor, CustodyEventType.READY_FOR_COLLECTION, "ZNZ", null, "AB-1", BagStatus.STORED, BagStatus.READY);
        verify(journal).audit(bag, actor, "ZNZ", "marked ready for collection", "STORED", "READY");
        verify(notifications).enqueue("EMAIL", "jane@example.com", "BAG_READY",
                "{\"bagCode\":\"GL-1\",\"trackUrl\":\"http://t/url\"}");
    }

    @Test
    void readyRequiresStoredBag() {
        bag(BagStatus.RECEIVED_ZNZ);
        api(() -> service.ready(1L), HttpStatus.BAD_REQUEST, "does not match that stage");
        verify(notifications, never()).enqueue(anyString(), anyString(), anyString(), anyString());
    }

    // ---- release ----

    @Test
    void releaseCompletesCollectionAndClosesBooking() {
        Bag bag = bag(BagStatus.READY);
        StorageLocation slot = new StorageLocation();
        slot.setCode("A1");
        bag.setStorageLocation(slot);
        bag.setSealNumber("AB-1");
        Bag sibling = ServiceTestData.bag(2L, BagStatus.COLLECTED);
        when(bags.findByBookingId(1L)).thenReturn(List.of(bag, sibling));

        service.release(1L, new ReleaseRequest(" John ", true, " Showed passport ", true));

        assertThat(bag.getStatus()).isEqualTo(BagStatus.COLLECTED);
        assertThat(bag.getStorageLocation()).isNull();
        assertThat(bag.getCurrentLocation()).isEqualTo("CUSTOMER");
        verify(journal).custody(bag, actor, CustodyEventType.CUSTOMER_RELEASE, "ZNZ", "Collected by John. Showed passport", "AB-1", BagStatus.READY, BagStatus.COLLECTED);
        verify(journal).audit(bag, actor, "ZNZ", "bag released to customer", "A1", "John");
        verify(notifications).enqueue("EMAIL", "jane@example.com", "BAG_COLLECTED", "{\"bagCode\":\"GL-1\"}");
        assertThat(bag.getBooking().getStatus()).isEqualTo("CLOSED");
        verify(bookings).save(bag.getBooking());
    }

    @Test
    void releaseWithoutStorageOrNotesStillWorks() {
        Bag bag = bag(BagStatus.READY);
        service.release(1L, new ReleaseRequest("Jane", true, null, true));
        verify(journal).custody(bag, actor, CustodyEventType.CUSTOMER_RELEASE, "ZNZ", "Collected by Jane", null, BagStatus.READY, BagStatus.COLLECTED);
        verify(journal).audit(bag, actor, "ZNZ", "bag released to customer", null, "Jane");
        bag.setStatus(BagStatus.READY);
        service.release(1L, new ReleaseRequest("Jane", true, "  ", true));
        assertThat(bag.getStatus()).isEqualTo(BagStatus.COLLECTED);
    }

    @Test
    void releaseValidatesStageAndConfirmations() {
        Bag bag = bag(BagStatus.STORED);
        api(() -> service.release(1L, new ReleaseRequest("J", true, null, true)), HttpStatus.BAD_REQUEST, "does not match that stage");
        bag.setStatus(BagStatus.READY);
        api(() -> service.release(1L, new ReleaseRequest("J", true, null, false)), HttpStatus.BAD_REQUEST, "Confirm the release");
        api(() -> service.release(1L, new ReleaseRequest("J", false, null, true)), HttpStatus.BAD_REQUEST, "identification must be verified");
        api(() -> service.release(1L, new ReleaseRequest(" ", true, null, true)), HttpStatus.BAD_REQUEST, "Collected by is required");
        assertThat(bag.getStatus()).isEqualTo(BagStatus.READY);
    }

    // ---- cancel + booking status refresh ----

    @Test
    void cancelMarksBagAndCancelsBookingWhenAllBagsCancelled() {
        Bag bag = bag(BagStatus.BOOKED);
        when(bags.findByBookingId(1L)).thenReturn(List.of(bag));

        service.cancel(1L);

        assertThat(bag.getStatus()).isEqualTo(BagStatus.CANCELLED);
        verify(journal).custody(bag, actor, CustodyEventType.CANCELLED, "KIA", null, null, BagStatus.BOOKED, BagStatus.CANCELLED);
        verify(journal).audit(bag, actor, "KIA", "booking cancelled", "BOOKED", "CANCELLED");
        assertThat(bag.getBooking().getStatus()).isEqualTo("CANCELLED");
        verify(bookings).save(bag.getBooking());
    }

    @Test
    void cancelKeepsBookingOpenWhenOtherBagsRemain() {
        Bag bag = bag(BagStatus.BOOKED);
        Bag other = ServiceTestData.bag(2L, BagStatus.RECEIVED_KIA);
        when(bags.findByBookingId(1L)).thenReturn(List.of(bag, other));
        service.cancel(1L);
        assertThat(bag.getBooking().getStatus()).isEqualTo("OPEN");
        verify(bookings, never()).save(any());
    }

    @Test
    void bookingStatusReopensWhenBagsBecomeActiveAgain() {
        Bag bag = bag(BagStatus.BOOKED);
        bag.getBooking().setStatus("CLOSED");
        Bag other = ServiceTestData.bag(2L, BagStatus.READY);
        when(bags.findByBookingId(1L)).thenReturn(List.of(bag, other));
        service.cancel(1L);
        assertThat(bag.getBooking().getStatus()).isEqualTo("OPEN");
        verify(bookings).save(bag.getBooking());
    }

    @Test
    void bookingMixOfCancelledAndCollectedIsClosed() {
        Bag bag = bag(BagStatus.BOOKED);
        Bag other = ServiceTestData.bag(2L, BagStatus.COLLECTED);
        when(bags.findByBookingId(1L)).thenReturn(List.of(bag, other));
        service.cancel(1L);
        assertThat(bag.getBooking().getStatus()).isEqualTo("CLOSED");
    }

    @Test
    void bookingStatusUntouchedWhenNoRelatedBags() {
        Bag bag = bag(BagStatus.BOOKED);
        when(bags.findByBookingId(1L)).thenReturn(List.of());
        service.cancel(1L);
        assertThat(bag.getBooking().getStatus()).isEqualTo("OPEN");
        verify(bookings, never()).save(any());
    }

    @Test
    void bookingStatusUntouchedWhenBookingMissingOrUnsaved() {
        Bag noBooking = bag(BagStatus.BOOKED);
        noBooking.setBooking(null);
        service.cancel(1L);
        Bag unsaved = bag(BagStatus.BOOKED);
        unsaved.getBooking().setId(null);
        service.cancel(1L);
        verify(bags, never()).findByBookingId(any());
        verify(bookings, never()).save(any());
    }

    @Test
    void cancelOnlyAllowedForBookedBags() {
        bag(BagStatus.RECEIVED_KIA);
        api(() -> service.cancel(1L), HttpStatus.BAD_REQUEST, "does not match that stage");
    }

    // ---- correct ----

    @Test
    void correctToStoredWithStorageCodePlacesBag() {
        Bag bag = bag(BagStatus.COLLECTED);
        bag.setSealNumber("AB-1");
        StorageLocation slot = slot(3L, "B2");

        service.correct(1L, new CorrectRequest("stored", " Wrong click ", "B2"));

        assertThat(bag.getStatus()).isEqualTo(BagStatus.STORED);
        assertThat(bag.getStorageLocation()).isSameAs(slot);
        assertThat(bag.getCurrentLocation()).isEqualTo("ZNZ");
        verify(journal).custody(bag, actor, CustodyEventType.STATUS_CORRECTION, "KIA", "Wrong click", "AB-1", BagStatus.COLLECTED, BagStatus.STORED);
        verify(journal).audit(bag, actor, "KIA", "status corrected: Wrong click", "COLLECTED", "STORED");
    }

    @Test
    void correctToReadyKeepsExistingStorageWhenNoCodeGiven() {
        Bag bag = bag(BagStatus.STORED);
        StorageLocation slot = new StorageLocation();
        slot.setCode("A1");
        bag.setStorageLocation(slot);
        service.correct(1L, new CorrectRequest("READY", "fix", " "));
        assertThat(bag.getStatus()).isEqualTo(BagStatus.READY);
        assertThat(bag.getStorageLocation()).isSameAs(slot);
        service.correct(1L, new CorrectRequest("READY", "fix", null));
        assertThat(bag.getCurrentLocation()).isEqualTo("ZNZ");
    }

    @Test
    void correctToStoredWithoutAnyStorageIsRejected() {
        Bag bag = bag(BagStatus.COLLECTED);
        api(() -> service.correct(1L, new CorrectRequest("STORED", "fix", null)), HttpStatus.BAD_REQUEST, "storage location is required");
        api(() -> service.correct(1L, new CorrectRequest("READY", "fix", "")), HttpStatus.BAD_REQUEST, "storage location is required");
        assertThat(bag.getStatus()).isEqualTo(BagStatus.COLLECTED);
    }

    @Test
    void correctClearsStorageAndSetsLocationPerTargetStatus() {
        Bag bag = bag(BagStatus.STORED);
        StorageLocation slot = new StorageLocation();
        bag.setStorageLocation(slot);

        service.correct(1L, new CorrectRequest("COLLECTED", "r", null));
        assertThat(bag.getStorageLocation()).isNull();
        assertThat(bag.getCurrentLocation()).isEqualTo("CUSTOMER");

        bag.setStorageLocation(slot);
        service.correct(1L, new CorrectRequest("HANDED_AIRLINE", "r", null));
        assertThat(bag.getStorageLocation()).isNull();
        assertThat(bag.getCurrentLocation()).isEqualTo("AIRLINE");

        bag.setStorageLocation(slot);
        service.correct(1L, new CorrectRequest("RECEIVED_ZNZ", "r", null));
        assertThat(bag.getStorageLocation()).isNull();
        assertThat(bag.getCurrentLocation()).isEqualTo("ZNZ");

        bag.setStorageLocation(slot);
        service.correct(1L, new CorrectRequest("BOOKED", "r", null));
        assertThat(bag.getStorageLocation()).isNull();
        assertThat(bag.getCurrentLocation()).isEqualTo("KIA");

        service.correct(1L, new CorrectRequest("CANCELLED", "r", null));
        assertThat(bag.getStatus()).isEqualTo(BagStatus.CANCELLED);
        assertThat(bag.getCurrentLocation()).isEqualTo("KIA");

        service.correct(1L, new CorrectRequest("sealed", "r", null));
        assertThat(bag.getStatus()).isEqualTo(BagStatus.SEALED);
    }

    @Test
    void correctValidatesReasonAndStatus() {
        bag(BagStatus.STORED);
        api(() -> service.correct(1L, new CorrectRequest("BOOKED", " ", null)), HttpStatus.BAD_REQUEST, "Correction reason is required");
        api(() -> service.correct(1L, new CorrectRequest(null, "why", null)), HttpStatus.BAD_REQUEST, "Status is required");
        api(() -> service.correct(1L, new CorrectRequest("LOST", "why", null)), HttpStatus.BAD_REQUEST, "Status is not recognised");
        verify(journal, never()).custody(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
