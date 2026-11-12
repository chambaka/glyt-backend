package tz.co.chambaka.glyt.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.chambaka.glyt.config.AppProperties;
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
import tz.co.chambaka.glyt.repo.BagRepository;
import tz.co.chambaka.glyt.repo.BagSealRepository;
import tz.co.chambaka.glyt.repo.BookingRepository;
import tz.co.chambaka.glyt.repo.PhotoRepository;
import tz.co.chambaka.glyt.repo.StorageLocationRepository;
import tz.co.chambaka.glyt.repo.TrackingTokenRepository;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BagDetail;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BagInput;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CorrectRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CreateBagRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ReceiveRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ReleaseRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.SealRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ZnzReceiveRequest;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
public class BagService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final BagRepository bags;
    private final BookingRepository bookings;
    private final BagSealRepository seals;
    private final TrackingTokenRepository trackingTokens;
    private final PhotoRepository photos;
    private final StorageLocationRepository storage;
    private final StaffContext staff;
    private final Journal journal;
    private final CodeGenerator codes;
    private final ExceptionService exceptions;
    private final BagQueryService queries;
    private final AppProperties properties;
    private final NotificationService notifications;

    public BagService(
            BagRepository bags,
            BookingRepository bookings,
            BagSealRepository seals,
            TrackingTokenRepository trackingTokens,
            PhotoRepository photos,
            StorageLocationRepository storage,
            StaffContext staff,
            Journal journal,
            CodeGenerator codes,
            ExceptionService exceptions,
            BagQueryService queries,
            AppProperties properties,
            NotificationService notifications
    ) {
        this.bags = bags;
        this.bookings = bookings;
        this.seals = seals;
        this.trackingTokens = trackingTokens;
        this.photos = photos;
        this.storage = storage;
        this.staff = staff;
        this.journal = journal;
        this.codes = codes;
        this.exceptions = exceptions;
        this.queries = queries;
        this.properties = properties;
        this.notifications = notifications;
    }

    @Transactional
    public BagDetail create(CreateBagRequest request) {
        staff.require(tz.co.chambaka.glyt.domain.enums.Role.ADMIN, tz.co.chambaka.glyt.domain.enums.Role.KIA_OFFICER);
        UserAccount actor = staff.account();
        if (request.bag() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bag details are required.");
        }
        if (request.bookingId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Booking is required.");
        }
        Booking booking = bookings.findById(request.bookingId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking was not found."));
        if (!"OPEN".equals(booking.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bags can only be registered on confirmed (paid) open bookings.");
        }
        Bag bag = new Bag();
        bag.setCode(codes.next("bag", "BG-", 6));
        bag.setCustomer(booking.getCustomer());
        bag.setBooking(booking);
        applyBagFields(bag, request.bag());
        bag.setStatus(BagStatus.BOOKED);
        bag.setExceptionState(ExceptionState.NONE);
        bag.setCurrentLocation("KIA");
        bag.setUpdatedAt(Instant.now());
        bags.save(bag);
        TrackingToken token = new TrackingToken();
        token.setBag(bag);
        token.setToken(HexFormat.of().formatHex(RANDOM.generateSeed(18)));
        trackingTokens.save(token);
        bag.setTracking(token);
        journal.audit(bag, actor, "KIA", "bag registered", null, bag.getCode());
        return queries.toDetail(bag);
    }

    @Transactional
    public BagDetail receive(Long id, ReceiveRequest request) {
        staff.require(tz.co.chambaka.glyt.domain.enums.Role.ADMIN, tz.co.chambaka.glyt.domain.enums.Role.KIA_OFFICER);
        UserAccount actor = staff.account();
        Bag bag = load(id);
        expect(bag, BagStatus.BOOKED);
        if (!request.prohibitedConfirmed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Customer must confirm the bag contains personal effects and no prohibited items.");
        }
        requirePhoto(id, PhotoStage.KIA_FULL, "Full baggage photo");
        requirePhoto(id, PhotoStage.KIA_TAG, "Baggage tag photo");
        double weight = weight(request.weightKg());
        BagCondition condition = Texts.parse(BagCondition.class, request.condition(), "Bag condition");
        if (condition == BagCondition.DAMAGED) {
            Texts.required(request.damageNote(), "Damage note");
        }
        BagStatus previous = bag.getStatus();
        bag.setWeightKg(weight);
        bag.setCondition(condition);
        bag.setProhibitedConfirmed(true);
        bag.setStatus(BagStatus.RECEIVED_KIA);
        bag.setCurrentLocation("KIA");
        bag.setUpdatedAt(Instant.now());
        journal.custody(bag, actor, CustodyEventType.KIA_INTAKE, "KIA", null, null, previous, BagStatus.RECEIVED_KIA);
        journal.audit(bag, actor, "KIA", "bag received at KIA", previous.name(), BagStatus.RECEIVED_KIA.name());
        if (condition == BagCondition.DAMAGED) {
            exceptions.open(bag, ExceptionType.DAMAGED, Priority.HIGH, request.damageNote().trim(), actor);
        }
        return queries.toDetail(bag);
    }

    @Transactional
    public BagDetail seal(Long id, SealRequest request) {
        staff.require(tz.co.chambaka.glyt.domain.enums.Role.ADMIN, tz.co.chambaka.glyt.domain.enums.Role.KIA_OFFICER);
        UserAccount actor = staff.account();
        Bag bag = load(id);
        expect(bag, BagStatus.RECEIVED_KIA);
        requirePhoto(id, PhotoStage.KIA_SEAL, "Security seal photo");
        String seal = Texts.required(request.sealNumber(), "Seal number").toUpperCase();
        BagCondition condition = Texts.parse(BagCondition.class, request.condition(), "Bag condition");
        if (bag.getWeightKg() > properties.ops().overweightKg() && !request.acceptOverweight()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bag exceeds the " + (int) properties.ops().overweightKg() + " kg limit. Do not accept it, or accept it with a supervisor exception.");
        }
        if (condition == BagCondition.DAMAGED) {
            Texts.required(request.damageNote(), "Damage note");
        }
        String previousSeal = bag.getSealNumber() == null ? "blank" : bag.getSealNumber();
        BagStatus previous = bag.getStatus();
        for (BagSeal oldSeal : seals.findByBagIdAndCurrentSealTrue(bag.getId())) {
            oldSeal.setCurrentSeal(false);
        }
        BagSeal applied = new BagSeal();
        applied.setBag(bag);
        applied.setSealNumber(seal);
        applied.setAppliedBy(actor);
        applied.setCurrentSeal(true);
        seals.save(applied);
        bag.setSealNumber(seal);
        bag.setCondition(condition);
        bag.setStatus(BagStatus.SEALED);
        bag.setUpdatedAt(Instant.now());
        journal.custody(bag, actor, CustodyEventType.SECURITY_SEAL, "KIA", null, seal, previous, BagStatus.SEALED);
        journal.audit(bag, actor, "KIA", "seal number changed from " + previousSeal + " to " + seal, previousSeal, seal);
        if (bag.getWeightKg() > properties.ops().overweightKg()) {
            exceptions.open(bag, ExceptionType.OVERWEIGHT, Priority.HIGH, "Bag weight " + bag.getWeightKg() + " kg exceeds " + (int) properties.ops().overweightKg() + " kg.", actor);
        }
        if (condition == BagCondition.DAMAGED) {
            exceptions.open(bag, ExceptionType.DAMAGED, Priority.HIGH, request.damageNote().trim(), actor);
        }
        return queries.toDetail(bag);
    }

    @Transactional
    public BagDetail receiveZnz(Long id, ZnzReceiveRequest request) {
        staff.require(tz.co.chambaka.glyt.domain.enums.Role.ADMIN, tz.co.chambaka.glyt.domain.enums.Role.ZNZ_OFFICER);
        UserAccount actor = staff.account();
        Bag bag = load(id);
        expect(bag, BagStatus.HANDED_AIRLINE);
        requirePhoto(id, PhotoStage.ZNZ_CONDITION, "Arrival condition photo");
        requirePhoto(id, PhotoStage.ZNZ_SEAL, "Arrival seal photo");
        if (!request.sealIntact() && !request.sealDamaged()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Record whether the seal is intact or damaged.");
        }
        String observed = Texts.required(request.sealObserved(), "Seal observed").toUpperCase();
        BagCondition condition = Texts.parse(BagCondition.class, request.condition(), "Bag condition");
        String expected = bag.getSealNumber() == null ? "" : bag.getSealNumber().trim().toUpperCase();
        boolean mismatch = !expected.equals(observed);
        boolean damagedSeal = request.sealDamaged();
        boolean damagedBag = condition == BagCondition.DAMAGED;
        if (mismatch || damagedSeal || damagedBag) {
            Texts.required(request.exceptionNote(), "Exception note");
        }
        BagStatus previous = bag.getStatus();
        bag.setCondition(condition);
        bag.setStatus(BagStatus.RECEIVED_ZNZ);
        bag.setCurrentLocation("ZNZ");
        bag.setUpdatedAt(Instant.now());
        journal.custody(bag, actor, CustodyEventType.ZNZ_RECEIPT, "ZNZ", "Seal observed " + observed, observed, previous, BagStatus.RECEIVED_ZNZ);
        journal.audit(bag, actor, "ZNZ", "bag received in Zanzibar", previous.name(), BagStatus.RECEIVED_ZNZ.name());
        String note = request.exceptionNote() == null ? "" : request.exceptionNote().trim();
        if (mismatch) {
            exceptions.open(bag, ExceptionType.SEAL_MISMATCH, Priority.CRITICAL, "Expected " + expected + ", observed " + observed + ". " + note, actor);
        }
        if (damagedSeal) {
            exceptions.open(bag, ExceptionType.SEAL_DAMAGED, Priority.CRITICAL, note, actor);
        }
        if (damagedBag) {
            exceptions.open(bag, ExceptionType.DAMAGED, Priority.HIGH, note, actor);
        }
        return queries.toDetail(bag);
    }

    @Transactional
    public BagDetail store(Long id, String storageCode) {
        staff.require(tz.co.chambaka.glyt.domain.enums.Role.ADMIN, tz.co.chambaka.glyt.domain.enums.Role.ZNZ_OFFICER);
        UserAccount actor = staff.account();
        Bag bag = load(id);
        expect(bag, BagStatus.RECEIVED_ZNZ);
        place(bag, storageCode);
        BagStatus previous = bag.getStatus();
        bag.setStatus(BagStatus.STORED);
        bag.setCurrentLocation("ZNZ");
        bag.setUpdatedAt(Instant.now());
        String code = bag.getStorageLocation().getCode();
        journal.custody(bag, actor, CustodyEventType.STORAGE, "ZNZ", "Stored at " + code, bag.getSealNumber(), previous, BagStatus.STORED);
        journal.audit(bag, actor, "ZNZ", "storage assigned", null, code);
        return queries.toDetail(bag);
    }

    @Transactional
    public BagDetail ready(Long id) {
        staff.require(tz.co.chambaka.glyt.domain.enums.Role.ADMIN, tz.co.chambaka.glyt.domain.enums.Role.ZNZ_OFFICER);
        UserAccount actor = staff.account();
        Bag bag = load(id);
        expect(bag, BagStatus.STORED);
        BagStatus previous = bag.getStatus();
        bag.setStatus(BagStatus.READY);
        bag.setUpdatedAt(Instant.now());
        journal.custody(bag, actor, CustodyEventType.READY_FOR_COLLECTION, "ZNZ", null, bag.getSealNumber(), previous, BagStatus.READY);
        journal.audit(bag, actor, "ZNZ", "marked ready for collection", previous.name(), BagStatus.READY.name());
        notifications.enqueue("EMAIL", bag.getCustomer().getEmail(), "BAG_READY",
                "{\"bagCode\":\"" + bag.getCode() + "\",\"trackUrl\":\"" + queries.trackingUrl(bag) + "\"}");
        return queries.toDetail(bag);
    }

    @Transactional
    public BagDetail release(Long id, ReleaseRequest request) {
        staff.require(tz.co.chambaka.glyt.domain.enums.Role.ADMIN, tz.co.chambaka.glyt.domain.enums.Role.ZNZ_OFFICER);
        UserAccount actor = staff.account();
        Bag bag = load(id);
        expect(bag, BagStatus.READY);
        if (!request.confirmed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Confirm the release before completing collection.");
        }
        if (!request.idVerified()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Customer identification must be verified before release.");
        }
        String collectedBy = Texts.required(request.collectedBy(), "Collected by");
        String position = bag.getStorageLocation() == null ? null : bag.getStorageLocation().getCode();
        BagStatus previous = bag.getStatus();
        bag.setStorageLocation(null);
        bag.setStatus(BagStatus.COLLECTED);
        bag.setCurrentLocation("CUSTOMER");
        bag.setUpdatedAt(Instant.now());
        String notes = "Collected by " + collectedBy + (request.notes() == null || request.notes().isBlank() ? "" : ". " + request.notes().trim());
        journal.custody(bag, actor, CustodyEventType.CUSTOMER_RELEASE, "ZNZ", notes, bag.getSealNumber(), previous, BagStatus.COLLECTED);
        journal.audit(bag, actor, "ZNZ", "bag released to customer", position, collectedBy);
        notifications.enqueue("EMAIL", bag.getCustomer().getEmail(), "BAG_COLLECTED",
                "{\"bagCode\":\"" + bag.getCode() + "\"}");
        refreshBookingStatus(bag.getBooking());
        return queries.toDetail(bag);
    }

    @Transactional
    public BagDetail cancel(Long id) {
        staff.require(tz.co.chambaka.glyt.domain.enums.Role.ADMIN, tz.co.chambaka.glyt.domain.enums.Role.KIA_OFFICER);
        UserAccount actor = staff.account();
        Bag bag = load(id);
        expect(bag, BagStatus.BOOKED);
        BagStatus previous = bag.getStatus();
        bag.setStatus(BagStatus.CANCELLED);
        bag.setUpdatedAt(Instant.now());
        journal.custody(bag, actor, CustodyEventType.CANCELLED, "KIA", null, null, previous, BagStatus.CANCELLED);
        journal.audit(bag, actor, "KIA", "booking cancelled", previous.name(), BagStatus.CANCELLED.name());
        refreshBookingStatus(bag.getBooking());
        return queries.toDetail(bag);
    }

    private void refreshBookingStatus(Booking booking) {
        if (booking == null || booking.getId() == null) {
            return;
        }
        List<Bag> related = bags.findByBookingId(booking.getId());
        if (related.isEmpty()) {
            return;
        }
        boolean allCancelled = related.stream().allMatch(row -> row.getStatus() == BagStatus.CANCELLED);
        boolean allTerminal = related.stream().allMatch(row ->
                row.getStatus() == BagStatus.COLLECTED || row.getStatus() == BagStatus.CANCELLED);
        String next = allCancelled ? "CANCELLED" : allTerminal ? "CLOSED" : "OPEN";
        if (!next.equals(booking.getStatus())) {
            booking.setStatus(next);
            bookings.save(booking);
        }
    }

    @Transactional
    public BagDetail correct(Long id, CorrectRequest request) {
        staff.require(tz.co.chambaka.glyt.domain.enums.Role.ADMIN);
        UserAccount actor = staff.account();
        Bag bag = load(id);
        String reason = Texts.required(request.reason(), "Correction reason");
        BagStatus next = Texts.parse(BagStatus.class, request.status(), "Status");
        BagStatus previous = bag.getStatus();
        if (next == BagStatus.STORED || next == BagStatus.READY) {
            if (request.storageCode() != null && !request.storageCode().isBlank()) {
                place(bag, request.storageCode());
            } else if (bag.getStorageLocation() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "A storage location is required for this status.");
            }
            bag.setCurrentLocation("ZNZ");
        } else if (next == BagStatus.COLLECTED) {
            bag.setStorageLocation(null);
            bag.setCurrentLocation("CUSTOMER");
        } else if (next == BagStatus.HANDED_AIRLINE) {
            bag.setStorageLocation(null);
            bag.setCurrentLocation("AIRLINE");
        } else if (next == BagStatus.RECEIVED_ZNZ) {
            bag.setStorageLocation(null);
            bag.setCurrentLocation("ZNZ");
        } else {
            bag.setStorageLocation(null);
            bag.setCurrentLocation(next == BagStatus.CANCELLED ? "KIA" : "KIA");
        }
        bag.setStatus(next);
        bag.setUpdatedAt(Instant.now());
        journal.custody(bag, actor, CustodyEventType.STATUS_CORRECTION, actor.locationCode(), reason, bag.getSealNumber(), previous, next);
        journal.audit(bag, actor, actor.locationCode(), "status corrected: " + reason, previous.name(), next.name());
        return queries.toDetail(bag);
    }

    private void applyBagFields(Bag bag, BagInput input) {
        bag.setBagType(Texts.parse(BagType.class, input.bagType(), "Bag type"));
        bag.setColor(Texts.required(input.color(), "Bag colour"));
        bag.setWeightKg(weight(input.weightKg()));
        bag.setDescription(Texts.required(input.description(), "Bag description"));
        bag.setOriginalAirlineTag(Texts.optional(input.originalAirlineTag()));
    }

    private double weight(Double value) {
        if (value == null || value <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Weight is required.");
        }
        return value;
    }

    private Bag load(Long id) {
        return bags.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bag was not found."));
    }

    private void expect(Bag bag, BagStatus status) {
        if (bag.getStatus() != status) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This bag is " + bag.getStatus().staffLabel() + ". The next step does not match that stage.");
        }
    }

    private void requirePhoto(Long bagId, PhotoStage stage, String label) {
        if (!photos.existsByBagIdAndStage(bagId, stage)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + " is required.");
        }
    }

    private void place(Bag bag, String storageCode) {
        String code = Texts.required(storageCode, "Storage location");
        StorageLocation location = storage.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Storage location was not found."));
        bags.findByStorageLocationId(location.getId()).ifPresent(other -> {
            if (!other.getId().equals(bag.getId())) {
                throw new ApiException(HttpStatus.CONFLICT, location.getCode() + " already holds " + other.getCode() + ".");
            }
        });
        bag.setStorageLocation(location);
    }
}
