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
import tz.co.chambaka.glyt.domain.Manifest;
import tz.co.chambaka.glyt.domain.ManifestBag;
import tz.co.chambaka.glyt.domain.UserAccount;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.CustodyEventType;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionType;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ManifestStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.Priority;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.repo.BagRepository;
import tz.co.chambaka.glyt.repo.BaggageExceptionRepository;
import tz.co.chambaka.glyt.repo.ManifestBagRepository;
import tz.co.chambaka.glyt.repo.ManifestRepository;
import tz.co.chambaka.glyt.support.ServiceTestData;
import tz.co.chambaka.glyt.support.TestFixtures;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CreateManifestRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ManifestView;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManifestServiceTest {

    @Mock ManifestRepository manifests;
    @Mock ManifestBagRepository links;
    @Mock BagRepository bags;
    @Mock BaggageExceptionRepository exceptions;
    @Mock StaffContext staff;
    @Mock Journal journal;
    @Mock CodeGenerator codes;
    @Mock ExceptionService exceptionService;
    @Mock AirlineService airlineService;

    private ManifestService service;
    private UserAccount actor;
    private Manifest manifest;

    @BeforeEach
    void setUp() {
        service = new ManifestService(manifests, links, bags, exceptions, staff, journal, codes, exceptionService, airlineService);
        actor = TestFixtures.staff(Role.KIA_OFFICER, "KIA");
        when(staff.account()).thenReturn(actor);
        when(codes.next("manifest", "MF-JRO-ZNZ-", 5)).thenReturn("MF-JRO-ZNZ-00001");
        when(airlineService.resolveName(any())).thenReturn("Precision Air");
        when(manifests.save(any(Manifest.class))).thenAnswer(ServiceTestData.assignId(1L));
        when(bags.findEligibleForManifest(BagStatus.SEALED)).thenReturn(List.of());
        when(links.findByManifestId(any())).thenReturn(List.of());

        manifest = manifest(ManifestStatus.DRAFT);
    }

    private Manifest manifest(ManifestStatus status) {
        Manifest m = new Manifest();
        m.setId(1L);
        m.setNumber("MF-JRO-ZNZ-00001");
        m.setManifestDate("2026-01-10");
        m.setAirline("Precision Air");
        m.setFlightNumber("PW 100");
        m.setStatus(status);
        m.setPreparedBy(actor);
        when(manifests.findById(1L)).thenReturn(Optional.of(m));
        return m;
    }

    private static void api(Runnable action, HttpStatus status, String message) {
        ApiException ex = ServiceTestData.expectApi(action);
        assertThat(ex.status()).isEqualTo(status);
        assertThat(ex.getMessage()).contains(message);
    }

    private Bag sealed(long id) {
        Bag bag = ServiceTestData.bag(id, BagStatus.SEALED);
        bag.setSealNumber("SEAL-" + id);
        when(bags.findById(id)).thenReturn(Optional.of(bag));
        return bag;
    }

    private ManifestBag link(Bag bag, Manifest on) {
        ManifestBag link = new ManifestBag();
        link.setManifest(on);
        link.setBag(bag);
        bag.setManifestLink(link);
        return link;
    }

    // ---- create ----

    @Test
    void createBuildsDraftManifestWithDefaults() {
        ManifestView view = service.create(new CreateManifestRequest("pw", " PW 100 ", null, null, null));

        ArgumentCaptor<Manifest> saved = ArgumentCaptor.forClass(Manifest.class);
        verify(manifests).save(saved.capture());
        Manifest m = saved.getValue();
        assertThat(m.getNumber()).isEqualTo("MF-JRO-ZNZ-00001");
        assertThat(m.getAirline()).isEqualTo("Precision Air");
        assertThat(m.getFlightNumber()).isEqualTo("PW 100");
        assertThat(m.getRoute()).isEqualTo("JRO-ZNZ");
        assertThat(m.getManifestDate()).isEqualTo(LocalDate.now(ZoneId.of("Africa/Dar_es_Salaam")).toString());
        assertThat(m.getNotes()).isNull();
        assertThat(m.getStatus()).isEqualTo(ManifestStatus.DRAFT);
        assertThat(m.getPreparedBy()).isSameAs(actor);
        assertThat(view.status()).isEqualTo("DRAFT");
        assertThat(view.preparedBy()).isEqualTo("Test User");
        verify(staff).require(Role.ADMIN, Role.KIA_OFFICER);
        verify(journal).audit(isNull(), eq(actor), eq("KIA"), eq("manifest created MF-JRO-ZNZ-00001"), isNull(), eq("MF-JRO-ZNZ-00001"));
    }

    @Test
    void createHonoursExplicitDateRouteAndNotes() {
        service.create(new CreateManifestRequest("pw", "PW 1", " 2026-02-01 ", " JRO-DAR ", " handle with care "));
        ArgumentCaptor<Manifest> saved = ArgumentCaptor.forClass(Manifest.class);
        verify(manifests).save(saved.capture());
        assertThat(saved.getValue().getManifestDate()).isEqualTo("2026-02-01");
        assertThat(saved.getValue().getRoute()).isEqualTo("JRO-DAR");
        assertThat(saved.getValue().getNotes()).isEqualTo("handle with care");
    }

    @Test
    void createTreatsBlankDateAndRouteAsMissing() {
        service.create(new CreateManifestRequest("pw", "PW 1", "  ", "  ", null));
        ArgumentCaptor<Manifest> saved = ArgumentCaptor.forClass(Manifest.class);
        verify(manifests).save(saved.capture());
        assertThat(saved.getValue().getRoute()).isEqualTo("JRO-ZNZ");
    }

    @Test
    void createValidatesInput() {
        api(() -> service.create(new CreateManifestRequest("pw", " ", null, null, null)), HttpStatus.BAD_REQUEST, "Flight number is required");
        api(() -> service.create(new CreateManifestRequest("pw", "PW 1", "01/02/2026", null, null)), HttpStatus.BAD_REQUEST, "YYYY-MM-DD");
        when(airlineService.resolveName("zz")).thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "Choose an airline"));
        api(() -> service.create(new CreateManifestRequest("zz", "PW 1", null, null, null)), HttpStatus.BAD_REQUEST, "Choose an airline");
        verify(manifests, never()).save(any());
    }

    @Test
    void createRequiresKiaOrAdmin() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "no")).when(staff).require(Role.ADMIN, Role.KIA_OFFICER);
        api(() -> service.create(new CreateManifestRequest("pw", "PW 1", null, null, null)), HttpStatus.FORBIDDEN, "no");
    }

    // ---- list / get ----

    @Test
    void listAndGetBuildViewsWithEligibleBagsForDrafts() {
        Bag eligible = ServiceTestData.bag(9L, BagStatus.SEALED);
        when(bags.findEligibleForManifest(BagStatus.SEALED)).thenReturn(List.of(eligible));
        Bag onBoard = ServiceTestData.bag(1L, BagStatus.RECEIVED_ZNZ);
        Bag notYet = ServiceTestData.bag(2L, BagStatus.HANDED_AIRLINE);
        when(links.findByManifestId(1L)).thenReturn(List.of(link(onBoard, manifest), link(notYet, manifest)));
        when(manifests.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(manifest));

        ManifestView draft = service.list().get(0);

        assertThat(draft.bagCount()).isEqualTo(2);
        assertThat(draft.totalWeight()).isEqualTo(30.0);
        assertThat(draft.receivedCount()).isEqualTo(1);
        assertThat(draft.eligible()).hasSize(1);
        assertThat(draft.eligible().get(0).code()).isEqualTo("GL-9");
        assertThat(draft.bags()).hasSize(2);
        assertThat(draft.handoverAt()).isNull();

        Manifest handed = manifest(ManifestStatus.HANDED_OVER);
        handed.setHandoverAt(java.time.Instant.parse("2026-01-10T10:00:00Z"));
        ManifestView handedView = service.get(1L);
        assertThat(handedView.eligible()).isEmpty();
        assertThat(handedView.handoverAt()).isEqualTo("2026-01-10T10:00:00Z");
    }

    @Test
    void getUnknownManifest() {
        when(manifests.findById(404L)).thenReturn(Optional.empty());
        api(() -> service.get(404L), HttpStatus.NOT_FOUND, "Manifest was not found");
    }

    // ---- addBags ----

    @Test
    void addBagsLinksSealedBagsAndAudits() {
        Bag first = sealed(1);
        Bag second = sealed(2);

        ManifestView view = service.addBags(1L, new ArrayList<>(List.of(1L, 2L)));

        ArgumentCaptor<ManifestBag> saved = ArgumentCaptor.forClass(ManifestBag.class);
        verify(links, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(ManifestBag::getManifest).containsOnly(manifest);
        assertThat(first.getManifest()).isSameAs(manifest);
        assertThat(second.getManifest()).isSameAs(manifest);
        verify(journal).audit(first, actor, "KIA", "added to manifest MF-JRO-ZNZ-00001", null, "MF-JRO-ZNZ-00001");
        assertThat(view.number()).isEqualTo("MF-JRO-ZNZ-00001");
    }

    @Test
    void addBagsRejectsNonDraftManifestAndEmptySelection() {
        Manifest finalised = manifest(ManifestStatus.FINALISED);
        api(() -> service.addBags(1L, List.of(1L)), HttpStatus.BAD_REQUEST, "Only a draft manifest");
        finalised.setStatus(ManifestStatus.DRAFT);
        api(() -> service.addBags(1L, null), HttpStatus.BAD_REQUEST, "Select at least one bag");
        api(() -> service.addBags(1L, List.of()), HttpStatus.BAD_REQUEST, "Select at least one bag");
    }

    @Test
    void addBagsRejectsBagProblems() {
        when(bags.findById(404L)).thenReturn(Optional.empty());
        api(() -> service.addBags(1L, List.of(404L)), HttpStatus.NOT_FOUND, "Bag was not found");

        Bag unsealedStatus = ServiceTestData.bag(3L, BagStatus.RECEIVED_KIA);
        unsealedStatus.setSealNumber("S");
        when(bags.findById(3L)).thenReturn(Optional.of(unsealedStatus));
        api(() -> service.addBags(1L, List.of(3L)), HttpStatus.BAD_REQUEST, "without a seal");

        Bag noSeal = sealed(4);
        noSeal.setSealNumber(null);
        api(() -> service.addBags(1L, List.of(4L)), HttpStatus.BAD_REQUEST, "without a seal");
        noSeal.setSealNumber("  ");
        api(() -> service.addBags(1L, List.of(4L)), HttpStatus.BAD_REQUEST, "without a seal");

        Bag already = sealed(5);
        link(already, manifest);
        api(() -> service.addBags(1L, List.of(5L)), HttpStatus.BAD_REQUEST, "already on a manifest");
        verify(links, never()).save(any());
    }

    // ---- removeBag ----

    @Test
    void removeBagUnlinksBag() {
        Bag bag = sealed(1);
        link(bag, manifest).setId(1L);
        manifest.setId(1L);

        service.removeBag(1L, 1L);

        verify(links).deleteByBagId(1L);
        assertThat(bag.getManifestLink()).isNull();
        verify(journal).audit(bag, actor, "KIA", "removed from manifest MF-JRO-ZNZ-00001", "MF-JRO-ZNZ-00001", null);
    }

    @Test
    void removeBagRejectsInvalidRequests() {
        manifest(ManifestStatus.FINALISED);
        api(() -> service.removeBag(1L, 1L), HttpStatus.BAD_REQUEST, "Only a draft manifest");

        Manifest draft = manifest(ManifestStatus.DRAFT);
        when(bags.findById(404L)).thenReturn(Optional.empty());
        api(() -> service.removeBag(1L, 404L), HttpStatus.NOT_FOUND, "Bag was not found");

        Bag unlinked = sealed(2);
        api(() -> service.removeBag(1L, 2L), HttpStatus.BAD_REQUEST, "not on this manifest");

        Bag other = sealed(3);
        Manifest otherManifest = new Manifest();
        otherManifest.setId(99L);
        link(other, otherManifest);
        api(() -> service.removeBag(1L, 3L), HttpStatus.BAD_REQUEST, "not on this manifest");
        assertThat(draft.getStatus()).isEqualTo(ManifestStatus.DRAFT);
        assertThat(unlinked.getManifestLink()).isNull();
        verify(links, never()).deleteByBagId(any());
    }

    // ---- finalise ----

    @Test
    void finaliseMovesDraftToFinalised() {
        ManifestBag onBoard = link(sealed(1), manifest);
        when(links.findByManifestId(1L)).thenReturn(List.of(onBoard));
        ManifestView view = service.finalise(1L);
        assertThat(manifest.getStatus()).isEqualTo(ManifestStatus.FINALISED);
        assertThat(view.status()).isEqualTo("FINALISED");
        verify(journal).audit(null, actor, "KIA", "manifest finalised MF-JRO-ZNZ-00001", "DRAFT", "FINALISED");
    }

    @Test
    void finaliseRejectsNonDraftAndEmptyManifests() {
        manifest(ManifestStatus.HANDED_OVER);
        api(() -> service.finalise(1L), HttpStatus.BAD_REQUEST, "Only a draft manifest can be finalised");
        manifest(ManifestStatus.DRAFT);
        api(() -> service.finalise(1L), HttpStatus.BAD_REQUEST, "Add at least one bag");
    }

    // ---- handover ----

    @Test
    void handoverMovesBagsToAirlineCustody() {
        Manifest finalised = manifest(ManifestStatus.FINALISED);
        Bag bag = sealed(1);
        when(links.findByManifestId(1L)).thenReturn(List.of(link(bag, finalised)));

        ManifestView view = service.handover(1L);

        assertThat(bag.getStatus()).isEqualTo(BagStatus.HANDED_AIRLINE);
        assertThat(bag.getCurrentLocation()).isEqualTo("AIRLINE");
        assertThat(finalised.getStatus()).isEqualTo(ManifestStatus.HANDED_OVER);
        assertThat(finalised.getHandoverAt()).isNotNull();
        assertThat(view.status()).isEqualTo("HANDED_OVER");
        verify(journal).custody(bag, actor, CustodyEventType.AIRLINE_HANDOVER, "AIRLINE", "MF-JRO-ZNZ-00001", "SEAL-1",
                BagStatus.SEALED, BagStatus.HANDED_AIRLINE);
        verify(journal).audit(bag, actor, "KIA", "handed to airline on MF-JRO-ZNZ-00001", "SEALED", "HANDED_AIRLINE");
    }

    @Test
    void handoverRejectsInvalidManifests() {
        manifest(ManifestStatus.DRAFT);
        api(() -> service.handover(1L), HttpStatus.BAD_REQUEST, "Finalise the manifest");

        Manifest finalised = manifest(ManifestStatus.FINALISED);
        api(() -> service.handover(1L), HttpStatus.BAD_REQUEST, "has no bags");

        Bag wrongStatus = ServiceTestData.bag(2L, BagStatus.RECEIVED_KIA);
        wrongStatus.setSealNumber("S");
        when(links.findByManifestId(1L)).thenReturn(List.of(link(wrongStatus, finalised)));
        api(() -> service.handover(1L), HttpStatus.BAD_REQUEST, "without a seal");

        Bag noSeal = ServiceTestData.bag(3L, BagStatus.SEALED);
        noSeal.setSealNumber(" ");
        when(links.findByManifestId(1L)).thenReturn(List.of(link(noSeal, finalised)));
        api(() -> service.handover(1L), HttpStatus.BAD_REQUEST, "without a seal");

        Bag nullSeal = ServiceTestData.bag(4L, BagStatus.SEALED);
        when(links.findByManifestId(1L)).thenReturn(List.of(link(nullSeal, finalised)));
        api(() -> service.handover(1L), HttpStatus.BAD_REQUEST, "without a seal");
        assertThat(finalised.getStatus()).isEqualTo(ManifestStatus.FINALISED);
    }

    // ---- flagMissing ----

    @Test
    void flagMissingOpensExceptionsOnlyForBagsStillInAirlineCustody() {
        Manifest handed = manifest(ManifestStatus.HANDED_OVER);
        Bag missing = ServiceTestData.bag(1L, BagStatus.HANDED_AIRLINE);
        Bag alreadyFlagged = ServiceTestData.bag(2L, BagStatus.HANDED_AIRLINE);
        Bag arrived = ServiceTestData.bag(3L, BagStatus.RECEIVED_ZNZ);
        when(links.findByManifestId(1L)).thenReturn(List.of(link(missing, handed), link(alreadyFlagged, handed), link(arrived, handed)));
        when(exceptions.existsByBagIdAndTypeAndStatus(2L, ExceptionType.NOT_RECEIVED, ExceptionStatus.OPEN)).thenReturn(true);

        service.flagMissing(1L);

        verify(staff).require(Role.ADMIN, Role.ZNZ_OFFICER);
        verify(exceptionService).open(missing, ExceptionType.NOT_RECEIVED, Priority.CRITICAL,
                "Not received from manifest MF-JRO-ZNZ-00001", actor);
        verify(exceptionService, never()).open(eq(alreadyFlagged), any(), any(), any(), any());
        verify(exceptionService, never()).open(eq(arrived), any(), any(), any(), any());
    }

    @Test
    void flagMissingRequiresHandedOverManifest() {
        manifest(ManifestStatus.FINALISED);
        api(() -> service.flagMissing(1L), HttpStatus.BAD_REQUEST, "after airline handover");
    }
}
