package tz.co.chambaka.glyt.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.Customer;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.repo.AuditLogRepository;
import tz.co.chambaka.glyt.repo.BagRepository;
import tz.co.chambaka.glyt.repo.BaggageExceptionRepository;
import tz.co.chambaka.glyt.repo.CustodyEventRepository;
import tz.co.chambaka.glyt.repo.PhotoRepository;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.AuditView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BagDetail;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BagSummary;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustodyView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ManifestBrief;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PhotoView;

import java.util.ArrayList;
import java.util.List;

@Service
public class BagQueryService {

    private final BagRepository bags;
    private final PhotoRepository photos;
    private final CustodyEventRepository custody;
    private final AuditLogRepository audits;
    private final BaggageExceptionRepository exceptions;
    private final ExceptionService exceptionService;
    private final StaffContext staff;
    private final AppProperties properties;

    public BagQueryService(
            BagRepository bags,
            PhotoRepository photos,
            CustodyEventRepository custody,
            AuditLogRepository audits,
            BaggageExceptionRepository exceptions,
            ExceptionService exceptionService,
            StaffContext staff,
            AppProperties properties
    ) {
        this.bags = bags;
        this.photos = photos;
        this.custody = custody;
        this.audits = audits;
        this.exceptions = exceptions;
        this.exceptionService = exceptionService;
        this.staff = staff;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public BagDetail detail(Long id) {
        staff.requireStaff();
        Bag bag = bags.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bag was not found."));
        return toDetail(bag);
    }

    @Transactional(readOnly = true)
    public BagDetail byCode(String code) {
        staff.requireStaff();
        Bag bag = bags.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bag was not found."));
        return toDetail(bag);
    }

    @Transactional(readOnly = true)
    public List<BagSummary> search(String q) {
        staff.requireStaff();
        String term = q == null ? "" : q.trim().toLowerCase();
        if (term.isEmpty()) {
            return bags.findTop12ByOrderByUpdatedAtDesc().stream().map(this::summary).toList();
        }
        return bags.search("%" + term + "%", PageRequest.of(0, 40)).stream().map(this::summary).toList();
    }

    public BagDetail toDetail(Bag bag) {
        boolean showPassport = staff.principal().role() == Role.ADMIN || staff.principal().role() == Role.KIA_OFFICER;
        String url = trackingUrl(bag);
        return new BagDetail(
                bag.getId(),
                bag.getCode(),
                url,
                "Your luggage " + bag.getCode() + " has been registered successfully. You can track its journey here: " + url,
                bag.getStatus().name(),
                bag.getStatus().staffLabel(),
                bag.getStatus().customerLabel(),
                bag.getExceptionState().name(),
                bag.getBagType().name(),
                bag.getColor(),
                bag.getWeightKg(),
                bag.getDescription(),
                bag.getOriginalAirlineTag(),
                bag.getSealNumber(),
                bag.getCondition() == null ? null : bag.getCondition().name(),
                bag.isProhibitedConfirmed(),
                bag.getCurrentLocation(),
                bag.getStorageLocation() == null ? null : bag.getStorageLocation().getCode(),
                customer(bag, showPassport),
                bag.getManifest() == null ? null : new ManifestBrief(
                        bag.getManifest().getId(),
                        bag.getManifest().getNumber(),
                        bag.getManifest().getAirline(),
                        bag.getManifest().getFlightNumber(),
                        bag.getManifest().getStatus().name()
                ),
                photos.findByBagIdOrderByCapturedAtAsc(bag.getId()).stream()
                        .map(photo -> new PhotoView(
                                photo.getId(),
                                photo.getStage().name(),
                                photo.getCapturedAt().toString(),
                                photo.getCapturedBy().getFullName()
                        ))
                        .toList(),
                custody.findByBagIdOrderByCreatedAtAsc(bag.getId()).stream()
                        .map(event -> new CustodyView(
                                event.getId(),
                                event.getEventType().name(),
                                event.getUser().getFullName(),
                                event.getLocation(),
                                event.getNotes(),
                                event.getSealNumber(),
                                event.getPreviousStatus() == null ? null : event.getPreviousStatus().name(),
                                event.getNewStatus() == null ? null : event.getNewStatus().name(),
                                event.getCreatedAt().toString()
                        ))
                        .toList(),
                audits.findByBagIdOrderByCreatedAtAsc(bag.getId()).stream()
                        .map(row -> new AuditView(
                                row.getId(),
                                row.getUser().getFullName(),
                                row.getLocation(),
                                row.getAction(),
                                row.getPreviousValue(),
                                row.getNewValue(),
                                row.getCreatedAt().toString()
                        ))
                        .toList(),
                exceptions.findByBagIdOrderByCreatedAtDesc(bag.getId()).stream().map(exceptionService::toView).toList(),
                actions(bag),
                bag.getCreatedAt().toString(),
                bag.getUpdatedAt().toString()
        );
    }

    public BagSummary summary(Bag bag) {
        return new BagSummary(
                bag.getId(),
                bag.getCode(),
                bag.getCustomer().getFullName(),
                bag.getCustomer().getPhone(),
                bag.getStatus().name(),
                bag.getStatus().staffLabel(),
                bag.getExceptionState().name(),
                bag.getCurrentLocation(),
                bag.getStorageLocation() == null ? null : bag.getStorageLocation().getCode(),
                bag.getSealNumber(),
                bag.getWeightKg(),
                bag.getUpdatedAt().toString(),
                bag.getManifest() == null ? null : bag.getManifest().getNumber(),
                travelDate(bag)
        );
    }

    public String trackingUrl(Bag bag) {
        String base = properties.ops().publicBaseUrl().replaceAll("/$", "");
        String token = bag.trackingValue();
        return base + "/track/" + bag.getCode() + "/" + (token == null ? "" : token);
    }

    private String travelDate(Bag bag) {
        return bag.getBooking() == null ? null : bag.getBooking().getZanzibarTravelDate();
    }

    private CustomerView customer(Bag bag, boolean showPassport) {
        Customer customer = bag.getCustomer();
        var booking = bag.getBooking();
        return new CustomerView(
                customer.getId(),
                customer.getFullName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getNationality(),
                showPassport ? customer.getPassportNumber() : Texts.maskPassport(customer.getPassportNumber()),
                booking == null ? null : booking.getInternationalArrivalFlight(),
                booking == null ? null : booking.getKiaArrivalDate(),
                booking == null ? null : booking.getZanzibarTravelDate(),
                booking == null ? null : booking.getZanzibarFlight(),
                booking == null ? null : booking.getTourOperator(),
                booking == null ? null : booking.getBookingReference(),
                booking == null ? null : booking.getZanzibarHotel(),
                booking == null ? null : booking.getNotes(),
                booking == null ? 0 : booking.getId(),
                booking == null ? null : booking.getBookingNumber()
        );
    }

    private List<String> actions(Bag bag) {
        List<String> actions = new ArrayList<>();
        boolean kia = staff.kia();
        boolean znz = staff.znz();
        switch (bag.getStatus()) {
            case BOOKED -> {
                if (kia) {
                    actions.add("receive");
                    actions.add("cancel");
                }
            }
            case RECEIVED_KIA -> {
                if (kia) {
                    actions.add("seal");
                }
            }
            case SEALED -> {
                if (kia && bag.getManifest() == null) {
                    actions.add("addToManifest");
                }
            }
            case HANDED_AIRLINE -> {
                if (znz) {
                    actions.add("receiveZnz");
                }
            }
            case RECEIVED_ZNZ -> {
                if (znz) {
                    actions.add("store");
                }
            }
            case STORED -> {
                if (znz) {
                    actions.add("ready");
                }
            }
            case READY -> {
                if (znz) {
                    actions.add("release");
                }
            }
            case COLLECTED, CANCELLED -> {
            }
        }
        if (staff.principal().role() != Role.READONLY
                && bag.getStatus() != BagStatus.COLLECTED
                && bag.getStatus() != BagStatus.CANCELLED) {
            actions.add("exception");
        }
        if (staff.principal().role() == Role.ADMIN) {
            actions.add("correct");
        }
        actions.add("share");
        return actions;
    }
}
