package tz.co.chambaka.glyt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.domain.UserAccount;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.repo.AuditLogRepository;
import tz.co.chambaka.glyt.repo.BagSealRepository;
import tz.co.chambaka.glyt.repo.BaggageExceptionRepository;
import tz.co.chambaka.glyt.repo.CustodyEventRepository;
import tz.co.chambaka.glyt.repo.LocationRepository;
import tz.co.chambaka.glyt.repo.ManifestRepository;
import tz.co.chambaka.glyt.repo.PasswordResetTokenRepository;
import tz.co.chambaka.glyt.repo.PhotoRepository;
import tz.co.chambaka.glyt.repo.RefreshTokenRepository;
import tz.co.chambaka.glyt.repo.SupportTicketRepository;
import tz.co.chambaka.glyt.repo.UserAccountRepository;
import tz.co.chambaka.glyt.support.ServiceTestData;
import tz.co.chambaka.glyt.support.TestFixtures;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.UserView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.UserWrite;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserAdminServiceTest {

    private static final String PASSWORD = "Kilimanjaro#7Peak";

    @Mock UserAccountRepository users;
    @Mock LocationRepository locations;
    @Mock PasswordEncoder passwords;
    @Mock StaffContext staff;
    @Mock Journal journal;
    @Mock SmsGatewayClient sms;
    @Mock CustodyEventRepository custodyEvents;
    @Mock BaggageExceptionRepository exceptions;
    @Mock ManifestRepository manifests;
    @Mock PhotoRepository photos;
    @Mock BagSealRepository seals;
    @Mock SupportTicketRepository tickets;
    @Mock AuditLogRepository audits;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock PasswordResetTokenRepository passwordResets;

    private UserAdminService service;
    private UserAccount actor;

    @BeforeEach
    void setUp() {
        service = build(TestFixtures.appProperties());
        actor = TestFixtures.staff(Role.ADMIN, "KIA");
        when(staff.account()).thenReturn(actor);
        when(passwords.encode(anyString())).thenAnswer(inv -> "enc(" + inv.getArgument(0) + ")");
        when(users.save(any(UserAccount.class))).thenAnswer(ServiceTestData.assignId(20L));
    }

    private UserAdminService build(AppProperties properties) {
        return new UserAdminService(users, locations, passwords, staff, journal, sms, properties, custodyEvents,
                exceptions, manifests, photos, seals, tickets, audits, refreshTokens, passwordResets);
    }

    private static void api(Runnable action, HttpStatus status, String message) {
        ApiException ex = ServiceTestData.expectApi(action);
        assertThat(ex.status()).isEqualTo(status);
        assertThat(ex.getMessage()).contains(message);
    }

    private UserWrite create(String name, String email, String phone, String password, String role, String location, Boolean active) {
        return new UserWrite(name, email, phone, password, role, location, active);
    }

    private UserAccount existing() {
        UserAccount user = TestFixtures.staff(Role.ZNZ_OFFICER, "ZNZ");
        user.setId(20L);
        user.setEmail("target@golyt.co.tz");
        user.setFullName("Target Person");
        user.setPhone("+255712000999");
        when(users.findById(20L)).thenReturn(Optional.of(user));
        return user;
    }

    // ---- list ----

    @Test
    void listReturnsAllUsersForAdmin() {
        when(users.findAll()).thenReturn(List.of(actor, TestFixtures.staff(Role.READONLY, "KIA")));
        List<UserView> views = service.list();
        assertThat(views).hasSize(2);
        assertThat(views.get(0).role()).isEqualTo("ADMIN");
        verify(staff).require(Role.ADMIN);
    }

    @Test
    void everyOperationRequiresAdmin() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "admin only")).when(staff).require(Role.ADMIN);
        api(() -> service.list(), HttpStatus.FORBIDDEN, "admin only");
        api(() -> service.create(create("A", "a@b.c", "0712345678", PASSWORD, "ADMIN", null, null)), HttpStatus.FORBIDDEN, "admin only");
        api(() -> service.update(1L, create(null, null, null, null, null, null, null)), HttpStatus.FORBIDDEN, "admin only");
        api(() -> service.delete(1L), HttpStatus.FORBIDDEN, "admin only");
    }

    // ---- create ----

    @Test
    void createBuildsActiveUserWithTemporaryPasswordAndWelcomeSms() {
        when(locations.findByCodeIgnoreCase("KIA")).thenReturn(Optional.of(TestFixtures.location("KIA")));

        UserView view = service.create(create(" Amina Juma ", " Amina@GoLyt.co.tz ", "0712 345 678", PASSWORD, "kia officer", " KIA ", null));

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).save(saved.capture());
        UserAccount user = saved.getValue();
        assertThat(user.getEmail()).isEqualTo("amina@golyt.co.tz");
        assertThat(user.getFullName()).isEqualTo("Amina Juma");
        assertThat(user.getPhone()).isEqualTo("+255712345678");
        assertThat(user.getPasswordHash()).isEqualTo("enc(" + PASSWORD + ")");
        assertThat(user.getRole()).isEqualTo(Role.KIA_OFFICER);
        assertThat(user.getLocation().getCode()).isEqualTo("KIA");
        assertThat(user.isActive()).isTrue();
        assertThat(user.isMustChangePassword()).isTrue();
        assertThat(view.email()).isEqualTo("amina@golyt.co.tz");
        verify(journal).audit(null, actor, "KIA", "user created", null, "amina@golyt.co.tz");
        verify(sms).queueSms(eq("+255712345678"),
                eq("Welcome to GoLyt. Login: http://localhost:8080/admin/ Email: amina@golyt.co.tz Password: " + PASSWORD
                        + " Please change your password after first login."),
                eq("GoLyt welcome"), eq("GOLYT_WELCOME"), eq("golyt-welcome-20"));
    }

    @Test
    void createHonoursInactiveFlagAndAllLocations() {
        service.create(create("Amina Juma", "amina@golyt.co.tz", "0712345678", PASSWORD, "READONLY", "all", false));
        service.create(create("Amina Juma", "amina2@golyt.co.tz", "0712345678", PASSWORD, "READONLY", " ", true));
        service.create(create("Amina Juma", "amina3@golyt.co.tz", "0712345678", PASSWORD, "READONLY", null, true));
        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(users, org.mockito.Mockito.times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(u -> assertThat(u.getLocation()).isNull());
        assertThat(saved.getAllValues().get(0).isActive()).isFalse();
        assertThat(saved.getAllValues().get(1).isActive()).isTrue();
    }

    @Test
    void createStripsTrailingSlashFromLoginUrl() {
        AppProperties.Ops base = TestFixtures.appProperties().ops();
        service = build(ServiceTestData.withOps(new AppProperties.Ops(base.overweightKg(), base.uncollectedDays(),
                "https://golyt.co.tz/", base.pickupName(), base.pickupPlace(), base.pickupHours(), base.pickupPhone(), base.pickupWhatsapp())));
        service.create(create("Amina Juma", "amina@golyt.co.tz", "0712345678", PASSWORD, "ADMIN", null, null));
        verify(sms).queueSms(any(), contains("Login: https://golyt.co.tz/admin/ "), any(), any(), any());
    }

    @Test
    void createValidatesRequiredFields() {
        api(() -> service.create(create("A B", null, "0712345678", PASSWORD, "ADMIN", null, null)), HttpStatus.BAD_REQUEST, "Email is required");
        api(() -> service.create(create(" ", "a@b.co", "0712345678", PASSWORD, "ADMIN", null, null)), HttpStatus.BAD_REQUEST, "Name is required");
        api(() -> service.create(create("Amina Juma", "a@b.co", null, PASSWORD, "ADMIN", null, null)), HttpStatus.BAD_REQUEST, "valid Tanzania phone");
        api(() -> service.create(create("Amina Juma", "a@b.co", "12", PASSWORD, "ADMIN", null, null)), HttpStatus.BAD_REQUEST, "valid Tanzania phone");
        api(() -> service.create(create("Amina Juma", "a@b.co", "0712345678", null, "ADMIN", null, null)), HttpStatus.BAD_REQUEST, "Password is required");
        api(() -> service.create(create("Amina Juma", "a@b.co", "0712345678", "short", "ADMIN", null, null)), HttpStatus.BAD_REQUEST, "at least 10 characters");
        api(() -> service.create(create("Amina Juma", "a@b.co", "0712345678", PASSWORD, null, null, null)), HttpStatus.BAD_REQUEST, "Role is required");
        api(() -> service.create(create("Amina Juma", "a@b.co", "0712345678", PASSWORD, "boss", null, null)), HttpStatus.BAD_REQUEST, "Role is not recognised");
        when(locations.findByCodeIgnoreCase("MARS")).thenReturn(Optional.empty());
        api(() -> service.create(create("Amina Juma", "a@b.co", "0712345678", PASSWORD, "ADMIN", "MARS", null)), HttpStatus.BAD_REQUEST, "Location was not found");
        verify(users, never()).save(any());
    }

    @Test
    void createRejectsDuplicateEmail() {
        when(users.findByEmailIgnoreCase("amina@golyt.co.tz")).thenReturn(Optional.of(actor));
        api(() -> service.create(create("Amina Juma", "AMINA@golyt.co.tz", "0712345678", PASSWORD, "ADMIN", null, null)),
                HttpStatus.CONFLICT, "already in use");
        verify(users, never()).save(any());
    }

    @Test
    void createSkipsSmsWhenStoredPhoneCannotBeNormalised() {
        when(users.save(any(UserAccount.class))).thenAnswer(inv -> {
            UserAccount saved = inv.getArgument(0);
            saved.setPhone("bad");
            saved.setId(20L);
            return saved;
        });
        service.create(create("Amina Juma", "amina@golyt.co.tz", "0712345678", PASSWORD, "ADMIN", null, null));
        verify(sms, never()).queueSms(any(), any(), any(), any(), any());
    }

    // ---- update ----

    @Test
    void updateAppliesProvidedFieldsOnly() {
        UserAccount user = existing();
        when(locations.findByCodeIgnoreCase("KIA")).thenReturn(Optional.of(TestFixtures.location("KIA")));

        UserView view = service.update(20L, create(" New Name ", "ignored@x.co", "0755 111 222", null, "admin", "KIA", true));

        assertThat(user.getFullName()).isEqualTo("New Name");
        assertThat(user.getEmail()).isEqualTo("target@golyt.co.tz");
        assertThat(user.getPhone()).isEqualTo("+255755111222");
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.getLocation().getCode()).isEqualTo("KIA");
        assertThat(user.isActive()).isTrue();
        assertThat(view.fullName()).isEqualTo("New Name");
        verify(journal).audit(null, actor, "KIA", "user updated", null, "target@golyt.co.tz");
        verify(sms, never()).queueSms(any(), any(), any(), any(), any());
        verify(passwords, never()).encode(any());
    }

    @Test
    void updateWithNothingChangesNothing() {
        UserAccount user = existing();
        service.update(20L, create(null, null, null, null, null, null, null));
        service.update(20L, create("  ", null, null, " ", " ", " ", null));
        assertThat(user.getFullName()).isEqualTo("Target Person");
        assertThat(user.getRole()).isEqualTo(Role.ZNZ_OFFICER);
        assertThat(user.getLocation().getCode()).isEqualTo("ZNZ");
        assertThat(user.getPhone()).isEqualTo("+255712000999");
    }

    @Test
    void updateCanClearLocationWithAll() {
        UserAccount user = existing();
        service.update(20L, create(null, null, null, null, null, "ALL", null));
        assertThat(user.getLocation()).isNull();
    }

    @Test
    void updatePasswordResetsFlagsBumpsTokenVersionAndSendsSms() {
        UserAccount user = existing();
        user.setMustChangePassword(false);
        int version = user.getTokenVersion();

        service.update(20L, create(null, null, null, PASSWORD, null, null, null));

        assertThat(user.getPasswordHash()).isEqualTo("enc(" + PASSWORD + ")");
        assertThat(user.isMustChangePassword()).isTrue();
        assertThat(user.getTokenVersion()).isEqualTo(version + 1);
        verify(sms).queueSms(eq("+255712000999"),
                eq("GoLyt: Your password was reset. Login: http://localhost:8080/admin/ Email: target@golyt.co.tz Temporary password: "
                        + PASSWORD + " Please change it after signing in. If you did not initiate this, call +255 777 000 111."),
                eq("GoLyt password reset"), eq("GOLYT_ADMIN_PASSWORD_RESET"), contains("golyt-admin-reset-20-"));
    }

    @Test
    void updatePasswordOmitsDeskPhoneWhenNotConfigured() {
        AppProperties.Ops base = TestFixtures.appProperties().ops();
        service = build(ServiceTestData.withOps(new AppProperties.Ops(base.overweightKg(), base.uncollectedDays(),
                base.publicBaseUrl(), base.pickupName(), base.pickupPlace(), base.pickupHours(), "", base.pickupWhatsapp())));
        existing();
        service.update(20L, create(null, null, null, PASSWORD, null, null, null));
        verify(sms).queueSms(any(), eq("GoLyt: Your password was reset. Login: http://localhost:8080/admin/ Email: target@golyt.co.tz"
                + " Temporary password: " + PASSWORD + " Please change it after signing in."), any(), any(), any());
    }

    @Test
    void updatePasswordSkipsSmsWhenUserPhoneInvalid() {
        UserAccount user = existing();
        user.setPhone("bad");
        service.update(20L, create(null, null, null, PASSWORD, null, null, null));
        verify(passwords).encode(PASSWORD);
        verify(sms, never()).queueSms(any(), any(), any(), any(), any());
    }

    @Test
    void updateValidatesInput() {
        UserAccount user = existing();
        when(users.findById(99L)).thenReturn(Optional.empty());
        api(() -> service.update(99L, create(null, null, null, null, null, null, null)), HttpStatus.NOT_FOUND, "User was not found");
        api(() -> service.update(20L, create(null, null, " ", null, null, null, null)), HttpStatus.BAD_REQUEST, "valid Tanzania phone");
        api(() -> service.update(20L, create(null, null, "1", null, null, null, null)), HttpStatus.BAD_REQUEST, "valid Tanzania phone");
        api(() -> service.update(20L, create(null, null, null, null, "boss", null, null)), HttpStatus.BAD_REQUEST, "Role is not recognised");
        when(locations.findByCodeIgnoreCase("MARS")).thenReturn(Optional.empty());
        api(() -> service.update(20L, create(null, null, null, null, null, "MARS", null)), HttpStatus.BAD_REQUEST, "Location was not found");
        api(() -> service.update(20L, create(null, null, null, "weak", null, null, null)), HttpStatus.BAD_REQUEST, "Password must");
        api(() -> service.update(20L, create(null, null, null, "Target#Person99", null, null, null)), HttpStatus.BAD_REQUEST, "must not contain your username");
        assertThat(user.getPasswordHash()).isEqualTo("$2a$10$hashed");
    }

    @Test
    void adminCannotDisableOwnAccountButCanDisableOthers() {
        actor.setId(20L);
        UserAccount user = existing();
        api(() -> service.update(20L, create(null, null, null, null, null, null, false)), HttpStatus.BAD_REQUEST, "cannot disable your own account");
        assertThat(user.isActive()).isTrue();
        service.update(20L, create(null, null, null, null, null, null, true));

        actor.setId(10L);
        service.update(20L, create(null, null, null, null, null, null, false));
        assertThat(user.isActive()).isFalse();
    }

    // ---- delete ----

    @Test
    void deleteRemovesUserAndDependentRowsWhenNoHistory() {
        UserAccount user = existing();

        service.delete(20L);

        verify(refreshTokens).deleteByUserId(20L);
        verify(passwordResets).deleteByUserId(20L);
        verify(audits).deleteByUserId(20L);
        verify(users).delete(user);
        verify(journal).audit(null, actor, "KIA", "user deleted", "target@golyt.co.tz", null);
    }

    @Test
    void deleteRejectsUnknownUserAndSelfDeletion() {
        when(users.findById(99L)).thenReturn(Optional.empty());
        api(() -> service.delete(99L), HttpStatus.NOT_FOUND, "User was not found");
        actor.setId(20L);
        existing();
        api(() -> service.delete(20L), HttpStatus.BAD_REQUEST, "cannot delete your own account");
        verify(users, never()).delete(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void deleteRefusesWhenAnyHistoryCounterIsPositive(int counter) {
        existing();
        switch (counter) {
            case 0 -> when(custodyEvents.countByUserId(20L)).thenReturn(1L);
            case 1 -> when(exceptions.countByRaisedById(20L)).thenReturn(1L);
            case 2 -> when(exceptions.countByAssignedToId(20L)).thenReturn(1L);
            case 3 -> when(exceptions.countByResolvedById(20L)).thenReturn(1L);
            case 4 -> when(manifests.countByPreparedById(20L)).thenReturn(1L);
            case 5 -> when(photos.countByCapturedById(20L)).thenReturn(1L);
            case 6 -> when(seals.countByAppliedById(20L)).thenReturn(1L);
            default -> when(tickets.countByAssignedToId(20L)).thenReturn(1L);
        }

        api(() -> service.delete(20L), HttpStatus.CONFLICT, "Cannot delete target@golyt.co.tz: the account has operational history");

        verify(users, never()).delete(any());
        verify(refreshTokens, never()).deleteByUserId(any());
        verify(audits, never()).deleteByUserId(any());
    }
}
