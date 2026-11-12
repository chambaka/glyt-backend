package tz.co.chambaka.glyt.security;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.chambaka.glyt.domain.CustomerAccount;
import tz.co.chambaka.glyt.domain.UserAccount;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.repo.CustomerAccountRepository;
import tz.co.chambaka.glyt.repo.UserAccountRepository;

@Service
public class AccountLookup {

    private final UserAccountRepository users;
    private final CustomerAccountRepository customers;

    public AccountLookup(UserAccountRepository users, CustomerAccountRepository customers) {
        this.users = users;
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    public AuthUser load(AuthUser parsed) {
        if (parsed.role() == Role.CUSTOMER) {
            CustomerAccount account = customers.findById(parsed.id()).orElse(null);
            if (account == null || !account.isActive() || account.getTokenVersion() != parsed.tokenVersion()) {
                return null;
            }
            return new AuthUser(
                    account.getId(),
                    account.getEmail(),
                    account.getFullName(),
                    Role.CUSTOMER,
                    null,
                    account.getTokenVersion(),
                    false
            );
        }
        UserAccount account = users.findById(parsed.id()).orElse(null);
        if (account == null || !account.isActive() || account.getTokenVersion() != parsed.tokenVersion()) {
            return null;
        }
        account.locationCode();
        return new AuthUser(
                account.getId(),
                account.getEmail(),
                account.getFullName(),
                account.getRole(),
                account.locationCode(),
                account.getTokenVersion(),
                account.isMustChangePassword()
        );
    }
}
