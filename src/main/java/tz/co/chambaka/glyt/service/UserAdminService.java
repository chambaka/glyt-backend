package tz.co.chambaka.glyt.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.domain.Location;
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
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.UserView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.UserWrite;

import java.util.List;

@Service
public class UserAdminService {

    private static final String WELCOME_SMS_TAG = "GOLYT_WELCOME";
    private static final String PASSWORD_RESET_SMS_TAG = "GOLYT_ADMIN_PASSWORD_RESET";

    private final UserAccountRepository users;
    private final LocationRepository locations;
    private final PasswordEncoder passwords;
    private final StaffContext staff;
    private final Journal journal;
    private final SmsGatewayClient sms;
    private final AppProperties properties;
    private final CustodyEventRepository custodyEvents;
    private final BaggageExceptionRepository exceptions;
    private final ManifestRepository manifests;
    private final PhotoRepository photos;
    private final BagSealRepository seals;
    private final SupportTicketRepository tickets;
    private final AuditLogRepository audits;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository passwordResets;

    public UserAdminService(
            UserAccountRepository users,
            LocationRepository locations,
            PasswordEncoder passwords,
            StaffContext staff,
            Journal journal,
            SmsGatewayClient sms,
            AppProperties properties,
            CustodyEventRepository custodyEvents,
            BaggageExceptionRepository exceptions,
            ManifestRepository manifests,
            PhotoRepository photos,
            BagSealRepository seals,
            SupportTicketRepository tickets,
            AuditLogRepository audits,
            RefreshTokenRepository refreshTokens,
            PasswordResetTokenRepository passwordResets
    ) {
        this.users = users;
        this.locations = locations;
        this.passwords = passwords;
        this.staff = staff;
        this.journal = journal;
        this.sms = sms;
        this.properties = properties;
        this.custodyEvents = custodyEvents;
        this.exceptions = exceptions;
        this.manifests = manifests;
        this.photos = photos;
        this.seals = seals;
        this.tickets = tickets;
        this.audits = audits;
        this.refreshTokens = refreshTokens;
        this.passwordResets = passwordResets;
    }

    @Transactional(readOnly = true)
    public List<UserView> list() {
        staff.require(Role.ADMIN);
        return users.findAll().stream().map(AuthService::toView).toList();
    }

    @Transactional
    public UserView create(UserWrite request) {
        staff.require(Role.ADMIN);
        String email = Texts.required(request.email(), "Email").toLowerCase();
        String fullName = Texts.required(request.fullName(), "Name");
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "That email is already in use.");
        }
        String phone = PhoneNumbers.required(request.phone());
        String plainPassword = Texts.password(request.password(), email, fullName);

        UserAccount user = new UserAccount();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwords.encode(plainPassword));
        user.setRole(Texts.parse(Role.class, request.role(), "Role"));
        user.setLocation(resolveLocation(request.location()));
        user.setActive(request.active() == null || request.active());
        user.setMustChangePassword(true);
        users.save(user);
        journal.audit(null, staff.account(), staff.account().locationCode(), "user created", null, user.getEmail());
        queueWelcomeSms(user, plainPassword);
        return AuthService.toView(user);
    }

    @Transactional
    public UserView update(Long id, UserWrite request) {
        staff.require(Role.ADMIN);
        UserAccount actor = staff.account();
        UserAccount user = users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User was not found."));
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.phone() != null) {
            if (request.phone().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "A valid Tanzania phone number is required.");
            }
            user.setPhone(PhoneNumbers.required(request.phone()));
        }
        if (request.role() != null && !request.role().isBlank()) {
            user.setRole(Texts.parse(Role.class, request.role(), "Role"));
        }
        if (request.location() != null && !request.location().isBlank()) {
            user.setLocation(resolveLocation(request.location()));
        }
        if (request.active() != null) {
            if (!request.active() && actor.getId().equals(user.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot disable your own account.");
            }
            user.setActive(request.active());
        }
        if (request.password() != null && !request.password().isBlank()) {
            String plainPassword = Texts.password(request.password(), user.getEmail(), user.getFullName());
            user.setPasswordHash(passwords.encode(plainPassword));
            user.setMustChangePassword(true);
            user.setTokenVersion(user.getTokenVersion() + 1);
            queueAdminResetSms(user, plainPassword);
        }
        journal.audit(null, actor, actor.locationCode(), "user updated", null, user.getEmail());
        return AuthService.toView(user);
    }

    private void queueWelcomeSms(UserAccount user, String plainPassword) {
        String phone = PhoneNumbers.normalize(user.getPhone());
        if (phone == null || !StringUtils.hasText(plainPassword)) {
            return;
        }
        String loginUrl = properties.ops().publicBaseUrl().replaceAll("/$", "") + "/admin/";
        String body = "Welcome to GoLyt. Login: " + loginUrl
                + " Email: " + user.getEmail()
                + " Password: " + plainPassword
                + " Please change your password after first login.";
        sms.queueSms(
                phone,
                body,
                "GoLyt welcome",
                WELCOME_SMS_TAG,
                "golyt-welcome-" + user.getId()
        );
    }

    private void queueAdminResetSms(UserAccount user, String plainPassword) {
        String phone = PhoneNumbers.normalize(user.getPhone());
        if (phone == null || !StringUtils.hasText(plainPassword)) {
            return;
        }
        String loginUrl = properties.ops().publicBaseUrl().replaceAll("/$", "") + "/admin/";
        String desk = properties.ops().pickupPhone();
        String body = "GoLyt: Your password was reset. Login: " + loginUrl
                + " Email: " + user.getEmail()
                + " Temporary password: " + plainPassword
                + " Please change it after signing in."
                + (StringUtils.hasText(desk)
                ? " If you did not initiate this, call " + desk + "."
                : "");
        sms.queueSms(
                phone,
                body,
                "GoLyt password reset",
                PASSWORD_RESET_SMS_TAG,
                "golyt-admin-reset-" + user.getId() + "-" + System.currentTimeMillis()
        );
    }


    @Transactional
    public void delete(Long id) {
        staff.require(Role.ADMIN);
        UserAccount actor = staff.account();
        UserAccount user = users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User was not found."));
        if (actor.getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot delete your own account.");
        }
        long used = custodyEvents.countByUserId(id)
                + exceptions.countByRaisedById(id)
                + exceptions.countByAssignedToId(id)
                + exceptions.countByResolvedById(id)
                + manifests.countByPreparedById(id)
                + photos.countByCapturedById(id)
                + seals.countByAppliedById(id)
                + tickets.countByAssignedToId(id);
        if (used > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Cannot delete " + user.getEmail() + ": the account has operational history. Deactivate it instead."
            );
        }
        String email = user.getEmail();
        refreshTokens.deleteByUserId(id);
        passwordResets.deleteByUserId(id);
        audits.deleteByUserId(id);
        users.delete(user);
        journal.audit(null, actor, actor.locationCode(), "user deleted", email, null);
    }

    private Location resolveLocation(String code) {
        if (code == null || code.isBlank() || "ALL".equalsIgnoreCase(code.trim())) {
            return null;
        }
        return locations.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Location was not found."));
    }
}
