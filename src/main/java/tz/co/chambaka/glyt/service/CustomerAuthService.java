package tz.co.chambaka.glyt.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.domain.Customer;
import tz.co.chambaka.glyt.domain.CustomerAccount;
import tz.co.chambaka.glyt.domain.CustomerRefreshToken;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.repo.CustomerAccountRepository;
import tz.co.chambaka.glyt.repo.CustomerRefreshTokenRepository;
import tz.co.chambaka.glyt.repo.CustomerRepository;
import tz.co.chambaka.glyt.security.AuthUser;
import tz.co.chambaka.glyt.security.CurrentUser;
import tz.co.chambaka.glyt.security.JwtService;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerAuthResponse;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerProfileView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerRegisterRequest;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class CustomerAuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CustomerAccountRepository accounts;
    private final CustomerRefreshTokenRepository refreshTokens;
    private final CustomerRepository customers;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final AppProperties properties;
    private final CodeGenerator codes;
    private final NotificationService notifications;

    public CustomerAuthService(
            CustomerAccountRepository accounts,
            CustomerRefreshTokenRepository refreshTokens,
            CustomerRepository customers,
            PasswordEncoder passwords,
            JwtService jwt,
            AppProperties properties,
            CodeGenerator codes,
            NotificationService notifications
    ) {
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
        this.customers = customers;
        this.passwords = passwords;
        this.jwt = jwt;
        this.properties = properties;
        this.codes = codes;
        this.notifications = notifications;
    }

    @Transactional
    public CustomerAuthResponse register(CustomerRegisterRequest request) {
        String email = Texts.required(request.email(), "Email").trim().toLowerCase();
        if (accounts.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "That email is already registered.");
        }
        Customer customer = new Customer();
        customer.setCustomerNumber(codes.next("customer", "CU-", 6));
        customer.setFullName(Texts.required(request.fullName(), "Full name"));
        customer.setPhone(Texts.required(request.phone(), "Mobile number"));
        customer.setEmail(email);
        customer.setNationality(Texts.required(request.nationality(), "Nationality"));
        customer.setPassportNumber(Texts.required(request.passportNumber(), "Passport number"));
        customers.save(customer);

        CustomerAccount account = new CustomerAccount();
        account.setCustomer(customer);
        account.setEmail(email);
        account.setPhone(customer.getPhone());
        account.setFullName(customer.getFullName());
        account.setPasswordHash(passwords.encode(Texts.password(request.password(), email, customer.getFullName())));
        account.setPreferredLanguage(request.preferredLanguage() == null || request.preferredLanguage().isBlank()
                ? "en"
                : request.preferredLanguage().trim().toLowerCase());
        accounts.save(account);

        notifications.enqueue("EMAIL", account.getEmail(), "CUSTOMER_WELCOME",
                "{\"name\":\"" + escape(account.getFullName()) + "\"}");
        return issue(account);
    }

    @Transactional
    public CustomerAuthResponse login(String email, String password) {
        CustomerAccount account = accounts.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is not correct."));
        if (!account.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This account is disabled.");
        }
        if (!passwords.matches(password == null ? "" : password, account.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is not correct.");
        }
        return issue(account);
    }

    @Transactional
    public CustomerAuthResponse refresh(String rawToken) {
        CustomerRefreshToken stored = refreshTokens.findByTokenHash(hash(Texts.required(rawToken, "Refresh token")))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to continue."));
        if (stored.getRevokedAt() != null || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
        }
        CustomerAccount account = stored.getAccount();
        if (!account.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This account is disabled.");
        }
        stored.setRevokedAt(Instant.now());
        return issue(account);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(hash(rawToken.trim())).ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    @Transactional(readOnly = true)
    public CustomerProfileView me() {
        return toProfile(requireAccount());
    }

    public CustomerAccount requireAccount() {
        AuthUser principal = CurrentUser.get();
        if (principal.role() != Role.CUSTOMER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Customer sign-in is required for this action.");
        }
        return accounts.findById(principal.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to continue."));
    }

    private CustomerAuthResponse issue(CustomerAccount account) {
        String rawRefresh = HexFormat.of().formatHex(RANDOM.generateSeed(32));
        CustomerRefreshToken refresh = new CustomerRefreshToken();
        refresh.setAccount(account);
        refresh.setTokenHash(hash(rawRefresh));
        refresh.setExpiresAt(Instant.now().plus(properties.jwt().refreshTokenTtl()));
        refreshTokens.save(refresh);
        AuthUser principal = new AuthUser(
                account.getId(),
                account.getEmail(),
                account.getFullName(),
                Role.CUSTOMER,
                null,
                account.getTokenVersion(),
                false
        );
        return new CustomerAuthResponse(
                jwt.generate(principal),
                rawRefresh,
                jwt.ttlSeconds(),
                toProfile(account)
        );
    }

    private CustomerProfileView toProfile(CustomerAccount account) {
        Long customerId = account.getCustomer() == null ? null : account.getCustomer().getId();
        return new CustomerProfileView(
                account.getId(),
                customerId,
                account.getFullName(),
                account.getEmail(),
                account.getPhone(),
                account.getPreferredLanguage(),
                account.isActive()
        );
    }

    private static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
