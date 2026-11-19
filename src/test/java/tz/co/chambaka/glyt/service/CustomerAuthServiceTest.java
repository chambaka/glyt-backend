package tz.co.chambaka.glyt.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.domain.Customer;
import tz.co.chambaka.glyt.domain.CustomerAccount;
import tz.co.chambaka.glyt.domain.CustomerRefreshToken;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.repo.CustomerAccountRepository;
import tz.co.chambaka.glyt.repo.CustomerRefreshTokenRepository;
import tz.co.chambaka.glyt.repo.CustomerRepository;
import tz.co.chambaka.glyt.security.AuthUser;
import tz.co.chambaka.glyt.security.JwtService;
import tz.co.chambaka.glyt.support.ServiceTestData;
import tz.co.chambaka.glyt.support.TestFixtures;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerAuthResponse;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerProfileView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerRegisterRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerAuthServiceTest {

    private static final String GOOD_PASSWORD = "Str0ng!Passw0rd";

    @Mock CustomerAccountRepository accounts;
    @Mock CustomerRefreshTokenRepository refreshTokens;
    @Mock CustomerRepository customers;
    @Mock PasswordEncoder passwords;
    @Mock CodeGenerator codes;
    @Mock NotificationService notifications;

    private AppProperties properties;
    private JwtService jwt;
    private CustomerAuthService service;
    private CustomerAccount account;

    @BeforeEach
    void setUp() {
        properties = TestFixtures.appProperties();
        jwt = new JwtService(properties);
        service = new CustomerAuthService(accounts, refreshTokens, customers, passwords, jwt, properties, codes, notifications);
        Customer customer = ServiceTestData.customer(5L);
        account = new CustomerAccount();
        account.setId(7L);
        account.setCustomer(customer);
        account.setEmail("jane@example.com");
        account.setFullName("Jane Traveler");
        account.setPhone("+255712000111");
        account.setPasswordHash("hashed");
        account.setTokenVersion(2);
        when(passwords.encode(anyString())).thenReturn("ENCODED");
        when(accounts.save(any(CustomerAccount.class))).thenAnswer(ServiceTestData.assignId(7L));
        when(customers.save(any(Customer.class))).thenAnswer(ServiceTestData.assignId(5L));
        when(customers.findByPassportNumberIgnoreCaseAndNationalityIgnoreCase(any(), any())).thenReturn(Optional.empty());
        when(codes.next("customer", "CU-", 6)).thenReturn("CU-000001");
    }

    @AfterEach
    void tearDown() {
        ServiceTestData.clearAuth();
    }

    private static String sha(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static ApiException api(Runnable action, HttpStatus status, String message) {
        ApiException ex = ServiceTestData.expectApi(action);
        assertThat(ex.status()).isEqualTo(status);
        assertThat(ex.getMessage()).contains(message);
        return ex;
    }

    private CustomerRegisterRequest registration(String email, String language) {
        return new CustomerRegisterRequest("Jane \"JJ\" Traveler", email, "+255712000111", GOOD_PASSWORD, "TZ", "AB1234567", language);
    }

    // ---- register ----

    @Test
    void registerCreatesCustomerAccountAndTokens() {
        when(accounts.existsByEmailIgnoreCase("jane@example.com")).thenReturn(false);

        CustomerAuthResponse response = service.register(registration("  Jane@Example.com ", " SW "));

        ArgumentCaptor<Customer> customer = ArgumentCaptor.forClass(Customer.class);
        verify(customers).save(customer.capture());
        assertThat(customer.getValue().getCustomerNumber()).isEqualTo("CU-000001");
        assertThat(customer.getValue().getEmail()).isEqualTo("jane@example.com");

        ArgumentCaptor<CustomerAccount> saved = ArgumentCaptor.forClass(CustomerAccount.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("ENCODED");
        assertThat(saved.getValue().getPreferredLanguage()).isEqualTo("sw");

        verify(notifications).enqueue(eq("EMAIL"), eq("jane@example.com"), eq("CUSTOMER_WELCOME"),
                eq("{\"name\":\"Jane \\\"JJ\\\" Traveler\"}"));
        assertThat(response.token()).isNotBlank();
        assertThat(response.refreshToken()).hasSize(64);
        verify(refreshTokens).save(any(CustomerRefreshToken.class));
    }

    @Test
    void registerDefaultsLanguageToEnglish() {
        service.register(registration("jane@example.com", null));
        service.register(registration("jane@example.com", "  "));
        ArgumentCaptor<CustomerAccount> saved = ArgumentCaptor.forClass(CustomerAccount.class);
        verify(accounts, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allMatch(a -> "en".equals(a.getPreferredLanguage()));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(accounts.existsByEmailIgnoreCase("jane@example.com")).thenReturn(true);
        api(() -> service.register(registration("jane@example.com", null)), HttpStatus.CONFLICT, "already registered");
        verify(customers, never()).save(any());
    }

    @Test
    void registerReusesExistingCustomerWithoutAccount() {
        Customer existing = ServiceTestData.customer(4L);
        when(customers.findByPassportNumberIgnoreCaseAndNationalityIgnoreCase("AB1234567", "TZ"))
                .thenReturn(Optional.of(existing));
        when(accounts.existsByCustomerId(4L)).thenReturn(false);
        when(customers.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerAuthResponse response = service.register(registration("new@example.com", "en"));

        ArgumentCaptor<CustomerAccount> saved = ArgumentCaptor.forClass(CustomerAccount.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().getCustomer().getId()).isEqualTo(4L);
        assertThat(response.user().customerId()).isEqualTo(4L);
        verify(codes, never()).next("customer", "CU-", 6);
    }

    @Test
    void registerRejectsPassportAlreadyLinkedToAccount() {
        Customer existing = ServiceTestData.customer(4L);
        when(customers.findByPassportNumberIgnoreCaseAndNationalityIgnoreCase("AB1234567", "TZ"))
                .thenReturn(Optional.of(existing));
        when(accounts.existsByCustomerId(4L)).thenReturn(true);

        api(() -> service.register(registration("new@example.com", null)),
                HttpStatus.CONFLICT, "passport and nationality");
        verify(accounts, never()).save(any());
    }

    @Test
    void registerValidatesRequiredFields() {
        api(() -> service.register(registration(null, null)), HttpStatus.BAD_REQUEST, "Email is required");
        api(() -> service.register(new CustomerRegisterRequest(" ", "a@b.com", "+255712000111", GOOD_PASSWORD, "TZ", "P1", null)),
                HttpStatus.BAD_REQUEST, "Full name");
        api(() -> service.register(new CustomerRegisterRequest("A", "a@b.com", null, GOOD_PASSWORD, "TZ", "P1", null)),
                HttpStatus.BAD_REQUEST, "Mobile number");
        api(() -> service.register(new CustomerRegisterRequest("A", "a@b.com", "1", GOOD_PASSWORD, "", "P1", null)),
                HttpStatus.BAD_REQUEST, "Nationality");
        api(() -> service.register(new CustomerRegisterRequest("A", "a@b.com", "1", GOOD_PASSWORD, "TZ", null, null)),
                HttpStatus.BAD_REQUEST, "Passport number");
        api(() -> service.register(new CustomerRegisterRequest("Alice", "a@b.com", "1", "weak", "TZ", "P1", null)),
                HttpStatus.BAD_REQUEST, "at least 10");
    }

    // ---- login ----

    @Test
    void loginSucceeds() {
        when(accounts.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(account));
        when(passwords.matches("pw", "hashed")).thenReturn(true);

        CustomerAuthResponse response = service.login(" jane@example.com ", "pw");

        AuthUser parsed = jwt.parse(response.token());
        assertThat(parsed.role()).isEqualTo(Role.CUSTOMER);
        assertThat(parsed.id()).isEqualTo(7L);
        assertThat(parsed.tokenVersion()).isEqualTo(2);
        assertThat(response.user().customerId()).isEqualTo(5L);
        assertThat(response.expiresIn()).isEqualTo(900L);
    }

    @Test
    void loginRejectsUnknownEmail() {
        when(accounts.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        api(() -> service.login(null, "pw"), HttpStatus.UNAUTHORIZED, "Email or password");
        api(() -> service.login("x@y.com", "pw"), HttpStatus.UNAUTHORIZED, "Email or password");
    }

    @Test
    void loginRejectsDisabledAccount() {
        account.setActive(false);
        when(accounts.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(account));
        api(() -> service.login("jane@example.com", "pw"), HttpStatus.FORBIDDEN, "disabled");
    }

    @Test
    void loginRejectsWrongPassword() {
        when(accounts.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(account));
        when(passwords.matches(anyString(), anyString())).thenReturn(false);
        api(() -> service.login("jane@example.com", null), HttpStatus.UNAUTHORIZED, "Email or password");
        verify(passwords).matches("", "hashed");
    }

    // ---- refresh / logout ----

    private CustomerRefreshToken stored(Instant expires, Instant revoked) {
        CustomerRefreshToken token = new CustomerRefreshToken();
        token.setAccount(account);
        token.setExpiresAt(expires);
        token.setRevokedAt(revoked);
        return token;
    }

    @Test
    void refreshRotatesToken() {
        CustomerRefreshToken token = stored(Instant.now().plusSeconds(60), null);
        when(refreshTokens.findByTokenHash(sha("raw"))).thenReturn(Optional.of(token));

        CustomerAuthResponse response = service.refresh("raw");

        assertThat(token.getRevokedAt()).isNotNull();
        assertThat(response.user().email()).isEqualTo("jane@example.com");
    }

    @Test
    void refreshRequiresToken() {
        api(() -> service.refresh(null), HttpStatus.BAD_REQUEST, "Refresh token is required");
    }

    @Test
    void refreshRejectsUnknownRevokedExpiredAndDisabled() {
        when(refreshTokens.findByTokenHash(sha("unknown"))).thenReturn(Optional.empty());
        api(() -> service.refresh("unknown"), HttpStatus.UNAUTHORIZED, "Sign in");

        when(refreshTokens.findByTokenHash(sha("revoked")))
                .thenReturn(Optional.of(stored(Instant.now().plusSeconds(60), Instant.now())));
        api(() -> service.refresh("revoked"), HttpStatus.UNAUTHORIZED, "Sign in");

        when(refreshTokens.findByTokenHash(sha("expired")))
                .thenReturn(Optional.of(stored(Instant.now().minusSeconds(60), null)));
        api(() -> service.refresh("expired"), HttpStatus.UNAUTHORIZED, "Sign in");

        account.setActive(false);
        when(refreshTokens.findByTokenHash(sha("disabled")))
                .thenReturn(Optional.of(stored(Instant.now().plusSeconds(60), null)));
        api(() -> service.refresh("disabled"), HttpStatus.FORBIDDEN, "disabled");
    }

    @Test
    void logoutHandlesBlankKnownAndUnknownTokens() {
        service.logout(null);
        service.logout(" ");
        verify(refreshTokens, never()).findByTokenHash(anyString());

        CustomerRefreshToken token = stored(Instant.now().plusSeconds(60), null);
        when(refreshTokens.findByTokenHash(sha("known"))).thenReturn(Optional.of(token));
        service.logout(" known ");
        assertThat(token.getRevokedAt()).isNotNull();

        when(refreshTokens.findByTokenHash(sha("unknown"))).thenReturn(Optional.empty());
        service.logout("unknown");
    }

    // ---- me / requireAccount ----

    @Test
    void meReturnsProfile() {
        ServiceTestData.authenticate(new AuthUser(7L, "jane@example.com", "Jane", Role.CUSTOMER, null, 2, false));
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        CustomerProfileView view = service.me();
        assertThat(view.id()).isEqualTo(7L);
        assertThat(view.customerId()).isEqualTo(5L);
        assertThat(view.preferredLanguage()).isEqualTo("en");
    }

    @Test
    void profileHasNullCustomerIdWhenNoCustomerLinked() {
        account.setCustomer(null);
        ServiceTestData.authenticate(new AuthUser(7L, "jane@example.com", "Jane", Role.CUSTOMER, null, 2, false));
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        assertThat(service.me().customerId()).isNull();
    }

    @Test
    void requireAccountRejectsStaffAndMissingAccounts() {
        ServiceTestData.authenticate(TestFixtures.staff(Role.ADMIN, "KIA"));
        api(service::requireAccount, HttpStatus.FORBIDDEN, "Customer sign-in");

        ServiceTestData.authenticate(new AuthUser(99L, "x", "x", Role.CUSTOMER, null, 0, false));
        when(accounts.findById(99L)).thenReturn(Optional.empty());
        api(service::requireAccount, HttpStatus.UNAUTHORIZED, "Sign in");
    }

    @Test
    void requireAccountRequiresAuthentication() {
        api(service::requireAccount, HttpStatus.UNAUTHORIZED, "Sign in");
        verify(accounts, never()).findById(any());
    }
}
