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
import tz.co.chambaka.glyt.domain.Airline;
import tz.co.chambaka.glyt.domain.UserAccount;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.repo.AirlineRepository;
import tz.co.chambaka.glyt.repo.ManifestRepository;
import tz.co.chambaka.glyt.support.ServiceTestData;
import tz.co.chambaka.glyt.support.TestFixtures;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.AirlineView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.AirlineWrite;

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
class AirlineServiceTest {

    @Mock StaffContext staff;
    @Mock AirlineRepository airlines;
    @Mock ManifestRepository manifests;
    @Mock Journal journal;

    private AirlineService service;
    private UserAccount admin;

    @BeforeEach
    void setUp() {
        service = new AirlineService(staff, airlines, manifests, journal);
        admin = TestFixtures.staff(Role.ADMIN, "KIA");
        when(staff.account()).thenReturn(admin);
        when(airlines.save(any(Airline.class))).thenAnswer(ServiceTestData.assignId(1L));
    }

    private static void api(Runnable action, HttpStatus status, String message) {
        ApiException ex = ServiceTestData.expectApi(action);
        assertThat(ex.status()).isEqualTo(status);
        assertThat(ex.getMessage()).contains(message);
    }

    private static Airline airline(long id, String code, String name, boolean active) {
        Airline airline = new Airline();
        airline.setId(id);
        airline.setCode(code);
        airline.setName(name);
        airline.setActive(active);
        return airline;
    }

    // ---- list ----

    @Test
    void listReturnsAllOrActiveOnly() {
        when(airlines.findAllByOrderBySortOrderAscNameAsc()).thenReturn(List.of(airline(1, "PW", "Precision Air", true), airline(2, "XX", "Old", false)));
        when(airlines.findByActiveTrueOrderBySortOrderAscNameAsc()).thenReturn(List.of(airline(1, "PW", "Precision Air", true)));

        assertThat(service.list(null)).hasSize(2);
        assertThat(service.list(false)).hasSize(2);
        List<AirlineView> active = service.list(true);
        assertThat(active).hasSize(1);
        assertThat(active.get(0).code()).isEqualTo("PW");
        assertThat(active.get(0).country()).isEqualTo("TZ");
        assertThat(active.get(0).sortOrder()).isEqualTo(100);
        verify(staff, org.mockito.Mockito.times(3)).requireStaff();
    }

    // ---- create ----

    @Test
    void createNormalisesAndAudits() {
        when(airlines.findByCodeIgnoreCase("PW")).thenReturn(Optional.empty());

        AirlineView view = service.create(new AirlineWrite(" pw ", " Precision Air ", " ke ", false, 5));

        ArgumentCaptor<Airline> saved = ArgumentCaptor.forClass(Airline.class);
        verify(airlines).save(saved.capture());
        assertThat(saved.getValue().getCode()).isEqualTo("PW");
        assertThat(saved.getValue().getName()).isEqualTo("Precision Air");
        assertThat(saved.getValue().getCountry()).isEqualTo("KE");
        assertThat(saved.getValue().isActive()).isFalse();
        assertThat(saved.getValue().getSortOrder()).isEqualTo(5);
        assertThat(view.code()).isEqualTo("PW");
        verify(staff).require(Role.ADMIN);
        verify(journal).audit(isNull(), eq(admin), eq("OPS"), eq("airline created PW"), isNull(), eq("Precision Air"));
    }

    @Test
    void createAppliesDefaults() {
        when(airlines.findByCodeIgnoreCase("PW")).thenReturn(Optional.empty());
        AirlineView view = service.create(new AirlineWrite("pw", "Precision Air", null, null, null));
        assertThat(view.country()).isEqualTo("TZ");
        assertThat(view.active()).isTrue();
        assertThat(view.sortOrder()).isEqualTo(100);
        assertThat(service.create(new AirlineWrite("pw", "Precision Air", "  ", true, null)).country()).isEqualTo("TZ");
    }

    @Test
    void createRejectsDuplicateCodeAndMissingFields() {
        when(airlines.findByCodeIgnoreCase("PW")).thenReturn(Optional.of(airline(1, "PW", "Precision Air", true)));
        api(() -> service.create(new AirlineWrite("pw", "x", null, null, null)), HttpStatus.CONFLICT, "already exists");
        api(() -> service.create(new AirlineWrite(null, "x", null, null, null)), HttpStatus.BAD_REQUEST, "Code is required");
        api(() -> service.create(new AirlineWrite("zz", " ", null, null, null)), HttpStatus.BAD_REQUEST, "Name is required");
        verify(airlines, never()).save(any());
    }

    @Test
    void createRequiresAdmin() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "no")).when(staff).require(Role.ADMIN);
        api(() -> service.create(new AirlineWrite("pw", "x", null, null, null)), HttpStatus.FORBIDDEN, "no");
    }

    // ---- update ----

    @Test
    void updateAppliesProvidedFields() {
        Airline airline = airline(1, "PW", "Precision Air", true);
        when(airlines.findById(1L)).thenReturn(Optional.of(airline));

        AirlineView view = service.update(1L, new AirlineWrite("ignored", " New Name ", " ke ", false, 7));

        assertThat(view.name()).isEqualTo("New Name");
        assertThat(view.country()).isEqualTo("KE");
        assertThat(view.active()).isFalse();
        assertThat(view.sortOrder()).isEqualTo(7);
        assertThat(view.code()).isEqualTo("PW");
        verify(journal).audit(isNull(), eq(admin), eq("OPS"), eq("airline updated PW"), isNull(), eq("New Name"));
    }

    @Test
    void updateIgnoresBlankAndMissingFields() {
        Airline airline = airline(1, "PW", "Precision Air", true);
        when(airlines.findById(1L)).thenReturn(Optional.of(airline));

        AirlineView view = service.update(1L, new AirlineWrite(null, " ", " ", null, null));
        assertThat(view.name()).isEqualTo("Precision Air");
        assertThat(view.country()).isEqualTo("TZ");
        assertThat(view.active()).isTrue();

        assertThat(service.update(1L, new AirlineWrite(null, null, null, null, null)).name()).isEqualTo("Precision Air");
    }

    @Test
    void updateNotFound() {
        when(airlines.findById(9L)).thenReturn(Optional.empty());
        api(() -> service.update(9L, new AirlineWrite(null, null, null, null, null)), HttpStatus.NOT_FOUND, "Airline was not found");
    }

    // ---- delete ----

    @Test
    void deleteRemovesUnusedAirline() {
        Airline airline = airline(1, "PW", "Precision Air", true);
        when(airlines.findById(1L)).thenReturn(Optional.of(airline));

        service.delete(1L);

        verify(airlines).delete(airline);
        verify(journal).audit(isNull(), eq(admin), eq("OPS"), eq("airline deleted PW"), eq("PW"), isNull());
    }

    @Test
    void deleteRefusesWhenManifestsReferenceAirlineByNameOrCode() {
        Airline airline = airline(1, "PW", "Precision Air", true);
        when(airlines.findById(1L)).thenReturn(Optional.of(airline));
        when(manifests.countByAirlineIgnoreCase("Precision Air")).thenReturn(2L);
        when(manifests.countByAirlineIgnoreCase("PW")).thenReturn(1L);

        api(() -> service.delete(1L), HttpStatus.CONFLICT, "3 manifest(s)");
        verify(airlines, never()).delete(any());

        when(manifests.countByAirlineIgnoreCase("Precision Air")).thenReturn(0L);
        api(() -> service.delete(1L), HttpStatus.CONFLICT, "1 manifest(s)");
    }

    @Test
    void deleteNotFound() {
        when(airlines.findById(9L)).thenReturn(Optional.empty());
        api(() -> service.delete(9L), HttpStatus.NOT_FOUND, "Airline was not found");
    }

    // ---- resolveName ----

    @Test
    void resolveNameMatchesActiveCodeOrName() {
        when(airlines.findByCodeIgnoreCase("pw")).thenReturn(Optional.of(airline(1, "PW", "Precision Air", true)));
        when(airlines.findByCodeIgnoreCase("Air Tanzania")).thenReturn(Optional.empty());
        when(airlines.findByNameIgnoreCase("Air Tanzania")).thenReturn(Optional.of(airline(2, "TC", "Air Tanzania", true)));

        assertThat(service.resolveName(" pw ")).isEqualTo("Precision Air");
        assertThat(service.resolveName("Air Tanzania")).isEqualTo("Air Tanzania");
    }

    @Test
    void resolveNameRejectsUnknownInactiveAndBlank() {
        when(airlines.findByCodeIgnoreCase("old")).thenReturn(Optional.of(airline(3, "OLD", "Old Air", false)));
        when(airlines.findByNameIgnoreCase("old")).thenReturn(Optional.of(airline(3, "OLD", "Old Air", false)));
        when(airlines.findByCodeIgnoreCase("nope")).thenReturn(Optional.empty());
        when(airlines.findByNameIgnoreCase("nope")).thenReturn(Optional.empty());

        api(() -> service.resolveName("old"), HttpStatus.BAD_REQUEST, "Choose an airline");
        api(() -> service.resolveName("nope"), HttpStatus.BAD_REQUEST, "Choose an airline");
        api(() -> service.resolveName(null), HttpStatus.BAD_REQUEST, "Airline is required");
    }
}
