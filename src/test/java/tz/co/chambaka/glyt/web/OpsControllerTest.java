package tz.co.chambaka.glyt.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.service.AirlineService;
import tz.co.chambaka.glyt.service.DashboardService;
import tz.co.chambaka.glyt.service.ExceptionService;
import tz.co.chambaka.glyt.service.NotificationService;
import tz.co.chambaka.glyt.service.PortalDepthService;
import tz.co.chambaka.glyt.service.ReportService;
import tz.co.chambaka.glyt.service.StaffContext;
import tz.co.chambaka.glyt.service.StorageService;
import tz.co.chambaka.glyt.service.UserAdminService;
import tz.co.chambaka.glyt.web.dto.GlytDtos.AirlineView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.AirlineWrite;
import tz.co.chambaka.glyt.web.dto.GlytDtos.AuditView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.DashboardView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ExceptionRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ExceptionUpdateRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ExceptionView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.LocationView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.LocationWrite;
import tz.co.chambaka.glyt.web.dto.GlytDtos.MessageResponse;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ResolveRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ServiceAdminView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ServiceWrite;
import tz.co.chambaka.glyt.web.dto.GlytDtos.StorageAdminView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.StorageSlot;
import tz.co.chambaka.glyt.web.dto.GlytDtos.StorageWrite;
import tz.co.chambaka.glyt.web.dto.GlytDtos.SupportAdminView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.SupportAssignRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.SupportResolveRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.UserView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.UserWrite;
import tz.co.chambaka.glyt.web.dto.GlytDtos.WorkloadView;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpsControllerTest {

    @Mock
    DashboardService dashboard;
    @Mock
    StorageService storage;
    @Mock
    ExceptionService exceptions;
    @Mock
    ReportService reports;
    @Mock
    UserAdminService users;
    @Mock
    StaffContext staff;
    @Mock
    NotificationService notifications;
    @Mock
    PortalDepthService portal;
    @Mock
    AirlineService airlines;
    @InjectMocks
    OpsController controller;

    // ---- dashboard / workload / storage board ----

    @Test
    void dashboardRequiresStaff() {
        DashboardView view = mock(DashboardView.class);
        when(dashboard.load()).thenReturn(view);
        assertThat(controller.dashboard()).isSameAs(view);
        verify(staff).requireStaff();
    }

    @Test
    void dashboardRejectsNonStaff() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "no")).when(staff).requireStaff();
        assertThatThrownBy(controller::dashboard).isInstanceOf(ApiException.class);
        verifyNoInteractions(dashboard);
    }

    @Test
    void workloadDelegates() {
        WorkloadView view = mock(WorkloadView.class);
        when(portal.workload()).thenReturn(view);
        assertThat(controller.workload()).isSameAs(view);
    }

    @Test
    void storageBoardRequiresStaff() {
        List<StorageSlot> slots = List.of(mock(StorageSlot.class));
        when(storage.board()).thenReturn(slots);
        assertThat(controller.storage()).isSameAs(slots);
        verify(staff).requireStaff();
    }

    // ---- facilities: locations ----

    @Test
    void locationsCrud() {
        List<LocationView> rows = List.of(mock(LocationView.class));
        LocationView one = mock(LocationView.class);
        LocationWrite write = mock(LocationWrite.class);
        when(portal.listLocations()).thenReturn(rows);
        when(portal.createLocation(write)).thenReturn(one);
        when(portal.updateLocation(1L, write)).thenReturn(one);

        assertThat(controller.locations()).isSameAs(rows);
        assertThat(controller.createLocation(write)).isSameAs(one);
        assertThat(controller.updateLocation(1L, write)).isSameAs(one);

        MessageResponse deleted = controller.deleteLocation(1L);
        verify(portal).deleteLocation(1L);
        assertThat(deleted.message()).isEqualTo("Facility deleted.");
    }

    @Test
    void deleteLocationPropagatesErrors() {
        doThrow(new ApiException(HttpStatus.CONFLICT, "in use")).when(portal).deleteLocation(3L);
        assertThatThrownBy(() -> controller.deleteLocation(3L)).isInstanceOf(ApiException.class);
    }

    // ---- facilities: storage ----

    @Test
    void storageAdminCrud() {
        List<StorageAdminView> rows = List.of(mock(StorageAdminView.class));
        StorageAdminView one = mock(StorageAdminView.class);
        StorageWrite write = mock(StorageWrite.class);
        when(portal.listStorage()).thenReturn(rows);
        when(portal.createStorage(write)).thenReturn(one);
        when(portal.updateStorage(2L, write)).thenReturn(one);

        assertThat(controller.storageAdmin()).isSameAs(rows);
        assertThat(controller.createStorage(write)).isSameAs(one);
        assertThat(controller.updateStorage(2L, write)).isSameAs(one);

        MessageResponse deleted = controller.deleteStorage(2L);
        verify(portal).deleteStorage(2L);
        assertThat(deleted.message()).isEqualTo("Storage slot deleted.");
    }

    // ---- airlines ----

    @Test
    void airlinesCrud() {
        List<AirlineView> rows = List.of(mock(AirlineView.class));
        AirlineView one = mock(AirlineView.class);
        AirlineWrite write = mock(AirlineWrite.class);
        when(airlines.list(true)).thenReturn(rows);
        when(airlines.create(write)).thenReturn(one);
        when(airlines.update(3L, write)).thenReturn(one);

        assertThat(controller.listAirlines(true)).isSameAs(rows);
        assertThat(controller.createAirline(write)).isSameAs(one);
        assertThat(controller.updateAirline(3L, write)).isSameAs(one);

        MessageResponse deleted = controller.deleteAirline(3L);
        verify(airlines).delete(3L);
        assertThat(deleted.message()).isEqualTo("Airline deleted.");
    }

    @Test
    void listAirlinesAllowsNullFilter() {
        when(airlines.list(null)).thenReturn(List.of());
        assertThat(controller.listAirlines(null)).isEmpty();
    }

    // ---- service catalogue ----

    @Test
    void catalogServicesCrud() {
        List<ServiceAdminView> rows = List.of(mock(ServiceAdminView.class));
        ServiceAdminView one = mock(ServiceAdminView.class);
        ServiceWrite write = mock(ServiceWrite.class);
        when(portal.listServicesAdmin()).thenReturn(rows);
        when(portal.createService(write)).thenReturn(one);
        when(portal.updateService(4L, write)).thenReturn(one);

        assertThat(controller.catalogServices()).isSameAs(rows);
        assertThat(controller.createService(write)).isSameAs(one);
        assertThat(controller.updateService(4L, write)).isSameAs(one);

        MessageResponse deleted = controller.deleteService(4L);
        verify(portal).deleteService(4L);
        assertThat(deleted.message()).isEqualTo("Service deleted.");
    }

    // ---- support inbox / audits ----

    @Test
    void supportInboxFlow() {
        List<SupportAdminView> rows = List.of(mock(SupportAdminView.class));
        SupportAdminView one = mock(SupportAdminView.class);
        SupportAssignRequest assign = mock(SupportAssignRequest.class);
        SupportResolveRequest resolve = mock(SupportResolveRequest.class);
        when(portal.listSupport("OPEN")).thenReturn(rows);
        when(portal.assignSupport(5L, assign)).thenReturn(one);
        when(portal.resolveSupport(5L, resolve)).thenReturn(one);

        assertThat(controller.supportInbox("OPEN")).isSameAs(rows);
        assertThat(controller.assignSupport(5L, assign)).isSameAs(one);
        assertThat(controller.resolveSupport(5L, resolve)).isSameAs(one);
    }

    @Test
    void auditsPassLimit() {
        List<AuditView> rows = List.of(mock(AuditView.class));
        when(portal.listAudits(25)).thenReturn(rows);
        assertThat(controller.audits(25)).isSameAs(rows);
    }

    // ---- exceptions ----

    @Test
    void exceptionsListRequiresStaff() {
        List<ExceptionView> rows = List.of(mock(ExceptionView.class));
        when(exceptions.list("OPEN")).thenReturn(rows);
        assertThat(controller.exceptions("OPEN")).isSameAs(rows);
        verify(staff).requireStaff();
    }

    @Test
    void raiseUpdateResolveException() {
        ExceptionView view = mock(ExceptionView.class);
        ExceptionRequest raise = mock(ExceptionRequest.class);
        ExceptionUpdateRequest update = mock(ExceptionUpdateRequest.class);
        when(exceptions.raise(raise)).thenReturn(view);
        when(exceptions.update(6L, update)).thenReturn(view);
        when(exceptions.resolve(6L, "fixed")).thenReturn(view);

        assertThat(controller.raise(raise)).isSameAs(view);
        assertThat(controller.updateException(6L, update)).isSameAs(view);
        assertThat(controller.resolve(6L, new ResolveRequest("fixed"))).isSameAs(view);
        verify(staff, org.mockito.Mockito.times(3)).requireStaff();
    }

    @Test
    void raiseRejectsNonStaff() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "no")).when(staff).requireStaff();
        ExceptionRequest request = mock(ExceptionRequest.class);
        assertThatThrownBy(() -> controller.raise(request)).isInstanceOf(ApiException.class);
        verifyNoInteractions(exceptions);
    }

    // ---- reports ----

    @Test
    void reportReturnsCsvAttachment() {
        when(reports.csv("bags", "2026-01-01", "2026-01-31", "OPEN", "KQ")).thenReturn("a,b\n1,2\n");
        ResponseEntity<byte[]> response = controller.report("bags", "2026-01-01", "2026-01-31", "OPEN", "KQ");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=bags.csv");
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("text/csv");
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).isEqualTo("a,b\n1,2\n");
        verify(staff).requireStaff();
    }

    @Test
    void reportPropagatesBadType() {
        when(reports.csv("nope", null, null, null, null))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "Unknown report"));
        assertThatThrownBy(() -> controller.report("nope", null, null, null, null))
                .isInstanceOf(ApiException.class);
    }

    // ---- users ----

    @Test
    void usersCrud() {
        List<UserView> rows = List.of(mock(UserView.class));
        UserView one = mock(UserView.class);
        UserWrite write = mock(UserWrite.class);
        when(users.list()).thenReturn(rows);
        when(users.create(write)).thenReturn(one);
        when(users.update(7L, write)).thenReturn(one);

        assertThat(controller.users()).isSameAs(rows);
        assertThat(controller.createUser(write)).isSameAs(one);
        assertThat(controller.updateUser(7L, write)).isSameAs(one);

        MessageResponse deleted = controller.deleteUser(7L);
        verify(users).delete(7L);
        assertThat(deleted.message()).isEqualTo("User deleted.");
        verify(staff, org.mockito.Mockito.times(4)).requireStaff();
    }

    @Test
    void deleteUserRejectsNonStaff() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "no")).when(staff).requireStaff();
        assertThatThrownBy(() -> controller.deleteUser(7L)).isInstanceOf(ApiException.class);
        verifyNoInteractions(users);
    }

    // ---- notifications ----

    @Test
    void processNotificationsRequiresAdmin() {
        when(notifications.processPending(50)).thenReturn(3);
        Map<String, Object> body = controller.processNotifications();
        verify(staff).require(Role.ADMIN);
        assertThat(body).containsEntry("processed", 3);
    }

    @Test
    void processNotificationsRejectsNonAdmin() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "admin only")).when(staff).require(Role.ADMIN);
        assertThatThrownBy(controller::processNotifications).isInstanceOf(ApiException.class);
        verifyNoInteractions(notifications);
    }
}
