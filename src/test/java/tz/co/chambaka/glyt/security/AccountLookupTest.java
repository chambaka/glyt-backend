package tz.co.chambaka.glyt.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.chambaka.glyt.domain.CustomerAccount;
import tz.co.chambaka.glyt.domain.UserAccount;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.repo.CustomerAccountRepository;
import tz.co.chambaka.glyt.repo.UserAccountRepository;
import tz.co.chambaka.glyt.support.TestFixtures;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountLookupTest {

    @Mock
    UserAccountRepository users;

    @Mock
    CustomerAccountRepository customers;

    private AccountLookup lookup() {
        return new AccountLookup(users, customers);
    }

    private CustomerAccount customer(long version, boolean active) {
        CustomerAccount account = new CustomerAccount();
        account.setId(5L);
        account.setEmail("traveler@example.com");
        account.setFullName("Trav Eler");
        account.setActive(active);
        account.setTokenVersion((int) version);
        return account;
    }

    // ---- staff ----

    @Test
    void staffLoadsFreshStateFromDatabase() {
        UserAccount account = TestFixtures.staff(Role.KIA_OFFICER, "KIA");
        account.setMustChangePassword(true);
        account.setFullName("Fresh Name");
        when(users.findById(10L)).thenReturn(Optional.of(account));
        AuthUser parsed = new AuthUser(10L, "old@x.com", "Old", Role.ADMIN, "ALL", 1, false);

        AuthUser loaded = lookup().load(parsed);

        assertThat(loaded).isNotNull();
        assertThat(loaded.id()).isEqualTo(10L);
        assertThat(loaded.email()).isEqualTo("test@golyt.co.tz");
        assertThat(loaded.fullName()).isEqualTo("Fresh Name");
        assertThat(loaded.role()).isEqualTo(Role.KIA_OFFICER);
        assertThat(loaded.location()).isEqualTo("KIA");
        assertThat(loaded.tokenVersion()).isEqualTo(1);
        assertThat(loaded.mustChangePassword()).isTrue();
        verifyNoInteractions(customers);
    }

    @Test
    void staffWithoutLocationGetsAll() {
        UserAccount account = TestFixtures.staff(Role.ADMIN, "KIA");
        account.setLocation(null);
        when(users.findById(10L)).thenReturn(Optional.of(account));

        AuthUser loaded = lookup().load(new AuthUser(10L, "x", "x", Role.ADMIN, "ALL", 1, false));

        assertThat(loaded.location()).isEqualTo("ALL");
    }

    @Test
    void staffMissingReturnsNull() {
        when(users.findById(99L)).thenReturn(Optional.empty());

        assertThat(lookup().load(new AuthUser(99L, "x", "x", Role.ADMIN, "ALL", 1, false))).isNull();
    }

    @Test
    void staffInactiveReturnsNull() {
        UserAccount account = TestFixtures.staff(Role.ADMIN, "KIA");
        account.setActive(false);
        when(users.findById(10L)).thenReturn(Optional.of(account));

        assertThat(lookup().load(new AuthUser(10L, "x", "x", Role.ADMIN, "ALL", 1, false))).isNull();
    }

    @Test
    void staffTokenVersionMismatchReturnsNull() {
        UserAccount account = TestFixtures.staff(Role.ADMIN, "KIA");
        account.setTokenVersion(2);
        when(users.findById(10L)).thenReturn(Optional.of(account));

        assertThat(lookup().load(new AuthUser(10L, "x", "x", Role.ADMIN, "ALL", 1, false))).isNull();
    }

    // ---- customers ----

    @Test
    void customerLoadsAndNeverMustChangePassword() {
        when(customers.findById(5L)).thenReturn(Optional.of(customer(2, true)));

        AuthUser loaded = lookup().load(new AuthUser(5L, "old@x.com", "Old", Role.CUSTOMER, null, 2, true));

        assertThat(loaded).isEqualTo(new AuthUser(5L, "traveler@example.com", "Trav Eler", Role.CUSTOMER, null, 2, false));
        verify(customers).findById(5L);
        verifyNoInteractions(users);
    }

    @Test
    void customerMissingReturnsNull() {
        when(customers.findById(5L)).thenReturn(Optional.empty());

        assertThat(lookup().load(new AuthUser(5L, "x", "x", Role.CUSTOMER, null, 0, false))).isNull();
    }

    @Test
    void customerInactiveReturnsNull() {
        when(customers.findById(5L)).thenReturn(Optional.of(customer(0, false)));

        assertThat(lookup().load(new AuthUser(5L, "x", "x", Role.CUSTOMER, null, 0, false))).isNull();
    }

    @Test
    void customerTokenVersionMismatchReturnsNull() {
        when(customers.findById(5L)).thenReturn(Optional.of(customer(3, true)));

        assertThat(lookup().load(new AuthUser(5L, "x", "x", Role.CUSTOMER, null, 0, false))).isNull();
    }
}
