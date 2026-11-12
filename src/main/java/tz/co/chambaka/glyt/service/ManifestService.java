package tz.co.chambaka.glyt.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.Manifest;
import tz.co.chambaka.glyt.domain.UserAccount;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.CustodyEventType;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionType;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ManifestStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.Priority;
import tz.co.chambaka.glyt.repo.BagRepository;
import tz.co.chambaka.glyt.repo.BaggageExceptionRepository;
import tz.co.chambaka.glyt.repo.ManifestBagRepository;
import tz.co.chambaka.glyt.repo.ManifestRepository;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CreateManifestRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ManifestBag;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ManifestView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class ManifestService {

    private static final ZoneId DAR = ZoneId.of("Africa/Dar_es_Salaam");

    private final ManifestRepository manifests;
    private final ManifestBagRepository links;
    private final BagRepository bags;
    private final BaggageExceptionRepository exceptions;
    private final StaffContext staff;
    private final Journal journal;
    private final CodeGenerator codes;
    private final ExceptionService exceptionService;
    private final AirlineService airlineService;

    public ManifestService(
            ManifestRepository manifests,
            ManifestBagRepository links,
            BagRepository bags,
            BaggageExceptionRepository exceptions,
            StaffContext staff,
            Journal journal,
            CodeGenerator codes,
            ExceptionService exceptionService,
            AirlineService airlineService
    ) {
        this.manifests = manifests;
        this.links = links;
        this.bags = bags;
        this.exceptions = exceptions;
        this.staff = staff;
        this.journal = journal;
        this.codes = codes;
        this.exceptionService = exceptionService;
        this.airlineService = airlineService;
    }

    @Transactional
    public ManifestView create(CreateManifestRequest request) {
        staff.require(Role.ADMIN, Role.KIA_OFFICER);
        UserAccount actor = staff.account();
        Manifest manifest = new Manifest();
        manifest.setNumber(codes.next("manifest", "MF-JRO-ZNZ-", 5));
        manifest.setAirline(airlineService.resolveName(request.airline()));
        manifest.setFlightNumber(Texts.required(request.flightNumber(), "Flight number"));
        manifest.setManifestDate(request.manifestDate() == null || request.manifestDate().isBlank()
                ? LocalDate.now(DAR).toString()
                : Texts.date(request.manifestDate(), "Manifest date"));
        manifest.setRoute(request.route() == null || request.route().isBlank() ? "JRO-ZNZ" : request.route().trim());
        manifest.setNotes(Texts.optional(request.notes()));
        manifest.setPreparedBy(actor);
        manifest.setStatus(ManifestStatus.DRAFT);
        manifests.save(manifest);
        journal.audit(null, actor, "KIA", "manifest created " + manifest.getNumber(), null, manifest.getNumber());
        return toView(manifest);
    }

    @Transactional(readOnly = true)
    public List<ManifestView> list() {
        return manifests.findAllByOrderByCreatedAtDesc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public ManifestView get(Long id) {
        return toView(load(id));
    }

    @Transactional
    public ManifestView addBags(Long id, List<Long> bagIds) {
        staff.require(Role.ADMIN, Role.KIA_OFFICER);
        UserAccount actor = staff.account();
        Manifest manifest = load(id);
        if (manifest.getStatus() != ManifestStatus.DRAFT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only a draft manifest can be changed.");
        }
        if (bagIds == null || bagIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Select at least one bag.");
        }
        for (Long bagId : bagIds) {
            Bag bag = bags.findById(bagId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bag was not found."));
            if (bag.getStatus() != BagStatus.SEALED || bag.getSealNumber() == null || bag.getSealNumber().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, bag.getCode() + " cannot be handed to the airline without a seal.");
            }
            if (bag.getManifest() != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, bag.getCode() + " is already on a manifest.");
            }
            tz.co.chambaka.glyt.domain.ManifestBag link = new tz.co.chambaka.glyt.domain.ManifestBag();
            link.setManifest(manifest);
            link.setBag(bag);
            links.save(link);
            bag.setManifestLink(link);
            bag.setUpdatedAt(Instant.now());
            journal.audit(bag, actor, "KIA", "added to manifest " + manifest.getNumber(), null, manifest.getNumber());
        }
        return toView(manifest);
    }

    @Transactional
    public ManifestView removeBag(Long id, Long bagId) {
        staff.require(Role.ADMIN, Role.KIA_OFFICER);
        UserAccount actor = staff.account();
        Manifest manifest = load(id);
        if (manifest.getStatus() != ManifestStatus.DRAFT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only a draft manifest can be changed.");
        }
        Bag bag = bags.findById(bagId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bag was not found."));
        if (bag.getManifest() == null || !bag.getManifest().getId().equals(manifest.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That bag is not on this manifest.");
        }
        links.deleteByBagId(bag.getId());
        bag.setManifestLink(null);
        bag.setUpdatedAt(Instant.now());
        journal.audit(bag, actor, "KIA", "removed from manifest " + manifest.getNumber(), manifest.getNumber(), null);
        return toView(manifest);
    }

    @Transactional
    public ManifestView finalise(Long id) {
        staff.require(Role.ADMIN, Role.KIA_OFFICER);
        UserAccount actor = staff.account();
        Manifest manifest = load(id);
        if (manifest.getStatus() != ManifestStatus.DRAFT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only a draft manifest can be finalised.");
        }
        if (onManifest(id).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Add at least one bag before finalising.");
        }
        manifest.setStatus(ManifestStatus.FINALISED);
        journal.audit(null, actor, "KIA", "manifest finalised " + manifest.getNumber(), ManifestStatus.DRAFT.name(), ManifestStatus.FINALISED.name());
        return toView(manifest);
    }

    @Transactional
    public ManifestView handover(Long id) {
        staff.require(Role.ADMIN, Role.KIA_OFFICER);
        UserAccount actor = staff.account();
        Manifest manifest = load(id);
        if (manifest.getStatus() != ManifestStatus.FINALISED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Finalise the manifest before airline handover.");
        }
        List<Bag> included = onManifest(id);
        if (included.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This manifest has no bags.");
        }
        for (Bag bag : included) {
            if (bag.getStatus() != BagStatus.SEALED || bag.getSealNumber() == null || bag.getSealNumber().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, bag.getCode() + " cannot be handed to the airline without a seal.");
            }
            BagStatus previous = bag.getStatus();
            bag.setStatus(BagStatus.HANDED_AIRLINE);
            bag.setCurrentLocation("AIRLINE");
            bag.setUpdatedAt(Instant.now());
            journal.custody(bag, actor, CustodyEventType.AIRLINE_HANDOVER, "AIRLINE", manifest.getNumber(), bag.getSealNumber(), previous, BagStatus.HANDED_AIRLINE);
            journal.audit(bag, actor, "KIA", "handed to airline on " + manifest.getNumber(), previous.name(), BagStatus.HANDED_AIRLINE.name());
        }
        manifest.setStatus(ManifestStatus.HANDED_OVER);
        manifest.setHandoverAt(Instant.now());
        return toView(manifest);
    }

    @Transactional
    public ManifestView flagMissing(Long id) {
        staff.require(Role.ADMIN, Role.ZNZ_OFFICER);
        UserAccount actor = staff.account();
        Manifest manifest = load(id);
        if (manifest.getStatus() != ManifestStatus.HANDED_OVER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Missing bags can be flagged after airline handover.");
        }
        for (Bag bag : onManifest(id)) {
            if (bag.getStatus() == BagStatus.HANDED_AIRLINE
                    && !exceptions.existsByBagIdAndTypeAndStatus(bag.getId(), ExceptionType.NOT_RECEIVED, tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionStatus.OPEN)) {
                exceptionService.open(bag, ExceptionType.NOT_RECEIVED, Priority.CRITICAL, "Not received from manifest " + manifest.getNumber(), actor);
            }
        }
        return toView(manifest);
    }

    private Manifest load(Long id) {
        return manifests.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Manifest was not found."));
    }

    private List<Bag> onManifest(Long id) {
        return links.findByManifestId(id).stream().map(tz.co.chambaka.glyt.domain.ManifestBag::getBag).toList();
    }

    private ManifestView toView(Manifest manifest) {
        List<Bag> included = onManifest(manifest.getId());
        List<ManifestBag> eligible = manifest.getStatus() == ManifestStatus.DRAFT
                ? bags.findEligibleForManifest(BagStatus.SEALED).stream().map(this::bagRow).toList()
                : List.of();
        int received = (int) included.stream().filter(bag -> bag.getStatus().rank() >= BagStatus.RECEIVED_ZNZ.rank()).count();
        double weight = included.stream().mapToDouble(Bag::getWeightKg).sum();
        return new ManifestView(
                manifest.getId(),
                manifest.getNumber(),
                manifest.getManifestDate(),
                manifest.getRoute(),
                manifest.getAirline(),
                manifest.getFlightNumber(),
                manifest.getNotes(),
                manifest.getStatus().name(),
                manifest.getPreparedBy().getFullName(),
                manifest.getHandoverAt() == null ? null : manifest.getHandoverAt().toString(),
                included.size(),
                weight,
                received,
                included.stream().map(this::bagRow).toList(),
                eligible
        );
    }

    private ManifestBag bagRow(Bag bag) {
        return new ManifestBag(
                bag.getId(),
                bag.getCode(),
                bag.getCustomer().getFullName(),
                bag.getWeightKg(),
                bag.getSealNumber(),
                bag.getStatus().name(),
                bag.getStatus().staffLabel()
        );
    }
}
