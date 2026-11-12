package tz.co.chambaka.glyt.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tz.co.chambaka.glyt.domain.UserAccount;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.repo.UserAccountRepository;
import tz.co.chambaka.glyt.security.AuthUser;
import tz.co.chambaka.glyt.security.CurrentUser;
import tz.co.chambaka.glyt.web.ApiException;

import java.util.Arrays;

@Service
public class StaffContext {

    private final UserAccountRepository users;

    public StaffContext(UserAccountRepository users) {
        this.users = users;
    }

    public AuthUser principal() {
        return CurrentUser.get();
    }

    public UserAccount account() {
        AuthUser principal = principal();
        return users.findById(principal.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to continue."));
    }

    public void requireStaff() {
        if (principal().role() == Role.CUSTOMER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Staff credentials are required for this action.");
        }
    }

    public void require(Role... roles) {
        requireStaff();
        Role role = principal().role();
        if (Arrays.stream(roles).noneMatch(candidate -> candidate == role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission for this action.");
        }
    }

    public boolean kia() {
        Role role = principal().role();
        return role == Role.ADMIN || role == Role.KIA_OFFICER;
    }

    public boolean znz() {
        Role role = principal().role();
        return role == Role.ADMIN || role == Role.ZNZ_OFFICER;
    }

    public void requireWriter() {
        requireStaff();
        if (principal().role() == Role.READONLY) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission for this action.");
        }
    }

    public void requireCustomer() {
        if (principal().role() != Role.CUSTOMER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Customer sign-in is required for this action.");
        }
    }
}
