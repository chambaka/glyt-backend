package tz.co.chambaka.glyt.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.domain.UserAccount;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.repo.UserAccountRepository;
import tz.co.chambaka.glyt.security.AuthUser;
import tz.co.chambaka.glyt.support.ServiceTestData;
import tz.co.chambaka.glyt.support.TestFixtures;
import tz.co.chambaka.glyt.web.ApiException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffContextTest {

    @Mock UserAccountRepository users;
    @InjectMocks StaffContext staff;

    @AfterEach
    void tearDown() {
        ServiceTestData.clearAuth();
    }

    private void as(Role role) {
        ServiceTestData.authenticate(TestFixtures.staff(role, "KIA"));
    }

    private static void assertForbidden(Runnable action, String message) {
        ApiException ex = ServiceTestData.expectApi(action);
        assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getMessage()).contains(message);
    }

    @Test
    void principalRequiresAuthentication() {
        assertThatThrownBy(staff::principal).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).status()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void principalReturnsAuthUser() {
        UserAccount account = TestFixtures.staff(Role.ADMIN, "KIA");
        ServiceTestData.authenticate(account);
        AuthUser principal = staff.principal();
        assertThat(principal.id()).isEqualTo(10L);
        assertThat(principal.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void accountLoadsUserFromRepository() {
        UserAccount account = TestFixtures.staff(Role.ADMIN, "KIA");
        ServiceTestData.authenticate(account);
        when(users.findById(10L)).thenReturn(Optional.of(account));
        assertThat(staff.account()).isSameAs(account);
    }

    @Test
    void accountThrowsWhenUserMissing() {
        as(Role.ADMIN);
        when(users.findById(10L)).thenReturn(Optional.empty());
        ApiException ex = ServiceTestData.expectApi(staff::account);
        assertThat(ex.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void requireStaffRejectsCustomers() {
        as(Role.CUSTOMER);
        assertForbidden(staff::requireStaff, "Staff credentials");
    }

    @Test
    void requireStaffAllowsEveryStaffRole() {
        for (Role role : new Role[]{Role.ADMIN, Role.KIA_OFFICER, Role.ZNZ_OFFICER, Role.READONLY}) {
            as(role);
            assertThatCode(staff::requireStaff).doesNotThrowAnyException();
        }
    }

    @Test
    void requireChecksRoleList() {
        as(Role.KIA_OFFICER);
        assertThatCode(() -> staff.require(Role.ADMIN, Role.KIA_OFFICER)).doesNotThrowAnyException();
        assertForbidden(() -> staff.require(Role.ADMIN), "permission");
        assertForbidden(() -> staff.require(), "permission");
    }

    @Test
    void requireRejectsCustomerBeforeRoleList() {
        as(Role.CUSTOMER);
        assertForbidden(() -> staff.require(Role.CUSTOMER), "Staff credentials");
    }

    @Test
    void kiaAndZnzFlags() {
        as(Role.ADMIN);
        assertThat(staff.kia()).isTrue();
        assertThat(staff.znz()).isTrue();
        as(Role.KIA_OFFICER);
        assertThat(staff.kia()).isTrue();
        assertThat(staff.znz()).isFalse();
        as(Role.ZNZ_OFFICER);
        assertThat(staff.kia()).isFalse();
        assertThat(staff.znz()).isTrue();
        as(Role.READONLY);
        assertThat(staff.kia()).isFalse();
        assertThat(staff.znz()).isFalse();
    }

    @Test
    void requireWriterRejectsReadonlyAndCustomers() {
        as(Role.READONLY);
        assertForbidden(staff::requireWriter, "permission");
        as(Role.CUSTOMER);
        assertForbidden(staff::requireWriter, "Staff credentials");
        as(Role.ZNZ_OFFICER);
        assertThatCode(staff::requireWriter).doesNotThrowAnyException();
    }

    @Test
    void requireCustomerOnlyAllowsCustomers() {
        as(Role.CUSTOMER);
        assertThatCode(staff::requireCustomer).doesNotThrowAnyException();
        as(Role.ADMIN);
        assertForbidden(staff::requireCustomer, "Customer sign-in");
    }
}
