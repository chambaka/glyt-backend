package tz.co.chambaka.glyt.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.service.DashboardService;
import tz.co.chambaka.glyt.service.ExceptionService;
import tz.co.chambaka.glyt.service.NotificationService;
import tz.co.chambaka.glyt.service.PortalDepthService;
import tz.co.chambaka.glyt.service.ReportService;
import tz.co.chambaka.glyt.service.StaffContext;
import tz.co.chambaka.glyt.service.StorageService;
import tz.co.chambaka.glyt.service.UserAdminService;
import tz.co.chambaka.glyt.web.dto.GlytDtos.AuditView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.DashboardView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ExceptionRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ExceptionUpdateRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ExceptionView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.LocationView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.MessageResponse;
import tz.co.chambaka.glyt.web.dto.GlytDtos.LocationWrite;
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

@RestController
@RequestMapping("/api/v1")
public class OpsController {

    private final DashboardService dashboard;
    private final StorageService storage;
    private final ExceptionService exceptions;
    private final ReportService reports;
    private final UserAdminService users;
    private final StaffContext staff;
    private final NotificationService notifications;
    private final PortalDepthService portal;

    public OpsController(
            DashboardService dashboard,
            StorageService storage,
            ExceptionService exceptions,
            ReportService reports,
            UserAdminService users,
            StaffContext staff,
            NotificationService notifications,
            PortalDepthService portal
    ) {
        this.dashboard = dashboard;
        this.storage = storage;
        this.exceptions = exceptions;
        this.reports = reports;
        this.users = users;
        this.staff = staff;
        this.notifications = notifications;
        this.portal = portal;
    }

    @GetMapping("/dashboard")
    public DashboardView dashboard() {
        staff.requireStaff();
        return dashboard.load();
    }

    @GetMapping("/workload")
    public WorkloadView workload() {
        return portal.workload();
    }

    @GetMapping("/storage")
    public List<StorageSlot> storage() {
        staff.requireStaff();
        return storage.board();
    }

    @GetMapping("/facilities/locations")
    public List<LocationView> locations() {
        return portal.listLocations();
    }

    @PostMapping("/facilities/locations")
    public LocationView createLocation(@RequestBody LocationWrite request) {
        return portal.createLocation(request);
    }

    @PatchMapping("/facilities/locations/{id}")
    public LocationView updateLocation(@PathVariable Long id, @RequestBody LocationWrite request) {
        return portal.updateLocation(id, request);
    }

    @DeleteMapping("/facilities/locations/{id}")
    public MessageResponse deleteLocation(@PathVariable Long id) {
        portal.deleteLocation(id);
        return new MessageResponse("Facility deleted.", null);
    }

    @GetMapping("/facilities/storage")
    public List<StorageAdminView> storageAdmin() {
        return portal.listStorage();
    }

    @PostMapping("/facilities/storage")
    public StorageAdminView createStorage(@RequestBody StorageWrite request) {
        return portal.createStorage(request);
    }

    @PatchMapping("/facilities/storage/{id}")
    public StorageAdminView updateStorage(@PathVariable Long id, @RequestBody StorageWrite request) {
        return portal.updateStorage(id, request);
    }

    @DeleteMapping("/facilities/storage/{id}")
    public MessageResponse deleteStorage(@PathVariable Long id) {
        portal.deleteStorage(id);
        return new MessageResponse("Storage slot deleted.", null);
    }

    @GetMapping("/catalog/services")
    public List<ServiceAdminView> catalogServices() {
        return portal.listServicesAdmin();
    }

    @PostMapping("/catalog/services")
    public ServiceAdminView createService(@RequestBody ServiceWrite request) {
        return portal.createService(request);
    }

    @PatchMapping("/catalog/services/{id}")
    public ServiceAdminView updateService(@PathVariable Long id, @RequestBody ServiceWrite request) {
        return portal.updateService(id, request);
    }

    @DeleteMapping("/catalog/services/{id}")
    public MessageResponse deleteService(@PathVariable Long id) {
        portal.deleteService(id);
        return new MessageResponse("Service deleted.", null);
    }

    @GetMapping("/support/inbox")
    public List<SupportAdminView> supportInbox(@RequestParam(required = false) String status) {
        return portal.listSupport(status);
    }

    @PostMapping("/support/inbox/{id}/assign")
    public SupportAdminView assignSupport(@PathVariable Long id, @RequestBody SupportAssignRequest request) {
        return portal.assignSupport(id, request);
    }

    @PostMapping("/support/inbox/{id}/resolve")
    public SupportAdminView resolveSupport(@PathVariable Long id, @RequestBody SupportResolveRequest request) {
        return portal.resolveSupport(id, request);
    }

    @GetMapping("/audits")
    public List<AuditView> audits(@RequestParam(required = false, defaultValue = "100") int limit) {
        return portal.listAudits(limit);
    }

    @GetMapping("/exceptions")
    public List<ExceptionView> exceptions(@RequestParam(required = false) String status) {
        staff.requireStaff();
        return exceptions.list(status);
    }

    @PostMapping("/exceptions")
    public ExceptionView raise(@RequestBody ExceptionRequest request) {
        staff.requireStaff();
        return exceptions.raise(request);
    }

    @PatchMapping("/exceptions/{id}")
    public ExceptionView updateException(@PathVariable Long id, @RequestBody ExceptionUpdateRequest request) {
        staff.requireStaff();
        return exceptions.update(id, request);
    }

    @PostMapping("/exceptions/{id}/resolve")
    public ExceptionView resolve(@PathVariable Long id, @RequestBody ResolveRequest request) {
        staff.requireStaff();
        return exceptions.resolve(id, request.notes());
    }

    @GetMapping("/reports/{type}")
    public ResponseEntity<byte[]> report(
            @PathVariable String type,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String airline
    ) {
        staff.requireStaff();
        byte[] body = reports.csv(type, from, to, status, airline).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + type + ".csv")
                .contentType(new MediaType("text", "csv"))
                .body(body);
    }

    @GetMapping("/users")
    public List<UserView> users() {
        staff.requireStaff();
        return users.list();
    }

    @PostMapping("/users")
    public UserView createUser(@RequestBody UserWrite request) {
        staff.requireStaff();
        return users.create(request);
    }

    @PatchMapping("/users/{id}")
    public UserView updateUser(@PathVariable Long id, @RequestBody UserWrite request) {
        staff.requireStaff();
        return users.update(id, request);
    }

    @DeleteMapping("/users/{id}")
    public MessageResponse deleteUser(@PathVariable Long id) {
        staff.requireStaff();
        users.delete(id);
        return new MessageResponse("User deleted.", null);
    }

    @PostMapping("/notifications/process")
    public Map<String, Object> processNotifications() {
        staff.require(Role.ADMIN);
        int processed = notifications.processPending(50);
        return Map.of("processed", processed);
    }
}
