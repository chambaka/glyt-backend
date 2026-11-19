package tz.co.chambaka.glyt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.BaggageException;
import tz.co.chambaka.glyt.domain.Manifest;
import tz.co.chambaka.glyt.domain.ManifestBag;
import tz.co.chambaka.glyt.domain.StorageLocation;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionType;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ManifestStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.Priority;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.repo.BagRepository;
import tz.co.chambaka.glyt.repo.BaggageExceptionRepository;
import tz.co.chambaka.glyt.repo.ManifestBagRepository;
import tz.co.chambaka.glyt.repo.ManifestRepository;
import tz.co.chambaka.glyt.repo.StorageLocationRepository;
import tz.co.chambaka.glyt.support.ServiceTestData;
import tz.co.chambaka.glyt.support.TestFixtures;
import tz.co.chambaka.glyt.web.ApiException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceTest {

    @Mock BagRepository bags;
    @Mock ManifestRepository manifests;
    @Mock BaggageExceptionRepository exceptions;
    @Mock StorageLocationRepository storage;
    @Mock ManifestBagRepository links;
    @Mock PortalDepthService portal;

    private ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportService(bags, manifests, exceptions, storage, links, portal);
    }

    private Bag bag(long id, BagStatus status, String updated) {
        Bag bag = ServiceTestData.bag(id, status);
        bag.setUpdatedAt(Instant.parse(updated));
        // The movement CSV builds its rows with List.of(...), which rejects a null seal number.
        bag.setSealNumber("SEAL-" + id);
        return bag;
    }

    private static String[] lines(String csv) {
        return csv.split("\n");
    }

    // ---- movement ----

    @Test
    void movementReportListsAllBagsWithoutFilters() {
        Bag plain = bag(1, BagStatus.SEALED, "2026-01-05T10:00:00Z");
        plain.setSealNumber("S1");
        Bag rich = bag(2, BagStatus.STORED, "2026-01-06T10:00:00Z");
        Manifest manifest = new Manifest();
        manifest.setNumber("MF-1");
        ManifestBag link = new ManifestBag();
        link.setManifest(manifest);
        rich.setManifestLink(link);
        StorageLocation slot = new StorageLocation();
        slot.setCode("A1");
        rich.setStorageLocation(slot);
        rich.getCustomer().setFullName("Jane \"JJ\" Traveler");
        when(bags.findAll()).thenReturn(List.of(plain, rich));

        String csv = service.csv("movement", null, null, null, null);

        String[] rows = lines(csv);
        assertThat(rows[0]).isEqualTo("bag_id,customer,status,location,weight_kg,seal,manifest,storage,updated");
        assertThat(rows).hasSize(3);
        assertThat(rows[1]).startsWith("\"GL-1\",\"Jane Traveler\",\"Sealed / ready for transfer\",\"KIA\",\"15.0\",\"S1\",\"\",\"\",");
        assertThat(rows[2]).contains("\"Jane \"\"JJ\"\" Traveler\"").contains("\"MF-1\",\"A1\"");
    }

    @Test
    void movementReportTreatsBlankFiltersAsAbsent() {
        when(bags.findAll()).thenReturn(List.of(bag(1, BagStatus.SEALED, "2026-01-05T10:00:00Z")));
        String csv = service.csv("movements", " ", "", " ", null);
        assertThat(lines(csv)[1]).contains("\"15.0\",\"SEAL-1\",\"\",\"\"");
    }

    @Test
    void movementReportFiltersByDateRangeInDarEsSalaamTime() {
        // 21:30Z on 4 Jan is already 5 Jan in Dar es Salaam.
        Bag lateNight = bag(1, BagStatus.SEALED, "2026-01-04T21:30:00Z");
        Bag middle = bag(2, BagStatus.SEALED, "2026-01-10T10:00:00Z");
        Bag later = bag(3, BagStatus.SEALED, "2026-01-20T10:00:00Z");
        when(bags.findAll()).thenReturn(List.of(lateNight, middle, later));

        assertThat(service.csv("movement", "2026-01-05", "2026-01-10", null, null)).contains("GL-1", "GL-2").doesNotContain("GL-3");
        assertThat(service.csv("movement", "2026-01-06", null, null, null)).doesNotContain("GL-1").contains("GL-2", "GL-3");
        assertThat(service.csv("movement", null, "2026-01-05", null, null)).contains("GL-1").doesNotContain("GL-2");
    }

    @Test
    void movementReportFiltersByStatusCaseInsensitively() {
        when(bags.findAll()).thenReturn(List.of(
                bag(1, BagStatus.SEALED, "2026-01-05T10:00:00Z"), bag(2, BagStatus.STORED, "2026-01-05T10:00:00Z")));
        String csv = service.csv("movement", null, null, "stored", null);
        assertThat(csv).contains("GL-2").doesNotContain("GL-1");
    }

    @Test
    void dailyReportOnlyIncludesToday() {
        Bag today = ServiceTestData.bag(1L, BagStatus.SEALED);
        today.setSealNumber("S1");
        today.setUpdatedAt(Instant.now());
        Bag old = bag(2, BagStatus.SEALED, "2020-01-01T00:00:00Z");
        when(bags.findAll()).thenReturn(List.of(today, old));
        String csv = service.csv("daily", "1999-01-01", "1999-01-02", null, null);
        assertThat(csv).contains("GL-1").doesNotContain("GL-2");
    }

    // ---- manifests ----

    private Manifest manifest(long id, String date, String airline) {
        Manifest manifest = new Manifest();
        manifest.setId(id);
        manifest.setNumber("MF-" + id);
        manifest.setManifestDate(date);
        manifest.setAirline(airline);
        manifest.setFlightNumber("PW 1");
        manifest.setRoute("JRO-ZNZ");
        manifest.setStatus(ManifestStatus.FINALISED);
        manifest.setPreparedBy(TestFixtures.staff(Role.KIA_OFFICER, "KIA"));
        return manifest;
    }

    private ManifestBag link(Bag bag) {
        ManifestBag link = new ManifestBag();
        link.setBag(bag);
        return link;
    }

    @Test
    void manifestReportSumsBagsAndWeights() {
        Manifest manifest = manifest(1, "2026-01-10", "Precision Air");
        when(manifests.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(manifest));
        when(links.findByManifestId(1L)).thenReturn(List.of(
                link(ServiceTestData.bag(1L, BagStatus.SEALED)), link(ServiceTestData.bag(2L, BagStatus.SEALED))));

        String csv = service.csv("manifests", null, null, null, null);

        assertThat(lines(csv)[0]).isEqualTo("manifest,date,airline,flight,route,status,bags,weight_kg,prepared_by");
        assertThat(lines(csv)[1]).isEqualTo("\"MF-1\",\"2026-01-10\",\"Precision Air\",\"PW 1\",\"JRO-ZNZ\",\"FINALISED\",\"2\",\"30.0\",\"Test User\"");
    }

    @Test
    void manifestReportFiltersByDateAndAirline() {
        when(manifests.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(
                manifest(1, "2026-01-10", "Precision Air"),
                manifest(2, "2026-02-10", "Air Tanzania"),
                manifest(3, "2025-12-31", "Precision Air")));
        when(links.findByManifestId(org.mockito.ArgumentMatchers.anyLong())).thenReturn(List.of());

        String byDate = service.csv("manifests", "2026-01-01", "2026-01-31", null, "");
        assertThat(byDate).contains("MF-1").doesNotContain("MF-2").doesNotContain("MF-3");

        String byAirline = service.csv("manifests", null, null, null, "air tanz");
        assertThat(byAirline).contains("MF-2").doesNotContain("MF-1");

        assertThat(service.csv("manifests", null, null, null, "  ")).contains("MF-1", "MF-2", "MF-3");
    }

    // ---- storage ----

    @Test
    void storageReportShowsOccupiedAndAvailableSlots() {
        StorageLocation free = new StorageLocation();
        free.setId(1L);
        free.setCode("A1");
        StorageLocation taken = new StorageLocation();
        taken.setId(2L);
        taken.setCode("A2");
        when(storage.findAllByOrderByCodeAsc()).thenReturn(List.of(free, taken));
        when(bags.findByStorageLocationId(1L)).thenReturn(Optional.empty());
        when(bags.findByStorageLocationId(2L)).thenReturn(Optional.of(ServiceTestData.bag(5L, BagStatus.STORED)));

        String[] rows = lines(service.csv("storage", null, null, null, null));

        assertThat(rows[0]).isEqualTo("position,status,bag_id,customer,expected_collection");
        assertThat(rows[1]).isEqualTo("\"A1\",\"Available\",\"\",\"\",\"\"");
        assertThat(rows[2]).isEqualTo("\"A2\",\"Occupied\",\"GL-5\",\"Jane Traveler\",\"2026-01-11\"");
    }

    // ---- exceptions / collections / audits ----

    @Test
    void exceptionReportListsRows() {
        BaggageException row = ServiceTestData.exception(7, ServiceTestData.bag(1L, BagStatus.STORED),
                ExceptionType.DAMAGED, Priority.HIGH, ExceptionStatus.OPEN);
        row.setDescription("Strap \"torn\"");
        when(exceptions.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(row));

        String[] rows = lines(service.csv("exceptions", null, null, null, null));

        assertThat(rows[0]).isEqualTo("id,bag_id,type,priority,status,location,raised_at,description");
        assertThat(rows[1]).startsWith("\"7\",\"GL-1\",\"Bag damaged\",\"HIGH\",\"OPEN\",\"KIA\",");
        assertThat(rows[1]).endsWith("\"Strap \"\"torn\"\"\"");
    }

    @Test
    void collectionsReportFiltersByRange() {
        when(bags.findByStatus(BagStatus.COLLECTED)).thenReturn(List.of(
                bag(1, BagStatus.COLLECTED, "2026-01-05T10:00:00Z"), bag(2, BagStatus.COLLECTED, "2026-03-05T10:00:00Z")));

        String csv = service.csv("collections", "2026-01-01", "2026-01-31", null, null);

        assertThat(lines(csv)[0]).isEqualTo("bag_id,customer,collected_at,storage_freed");
        assertThat(csv).contains("\"GL-1\"").doesNotContain("GL-2");
        assertThat(csv).contains("\"yes\"");
    }

    @Test
    void auditsReportDelegatesToPortalService() {
        when(portal.auditsCsv()).thenReturn("audit,csv\n");
        assertThat(service.csv("audits", null, null, null, null)).isEqualTo("audit,csv\n");
    }

    // ---- errors ----

    @Test
    void unknownReportTypeIsRejected() {
        ApiException ex = ServiceTestData.expectApi(() -> service.csv("bogus", null, null, null, null));
        assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("not recognised");
    }

    @Test
    void malformedDatesAreRejectedByParser() {
        assertThatThrownBy(() -> service.csv("movement", "yesterday", null, null, null)).isInstanceOf(DateTimeParseException.class);
        assertThatThrownBy(() -> service.csv("movement", null, "tomorrow", null, null)).isInstanceOf(DateTimeParseException.class);
    }
}
