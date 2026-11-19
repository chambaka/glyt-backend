package tz.co.chambaka.glyt.security;

import org.junit.jupiter.api.Test;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.support.TestFixtures;

import static org.assertj.core.api.Assertions.assertThat;

class AuthUserTest {

    @Test
    void recordAccessors() {
        AuthUser user = new AuthUser(1L, "a@b.co", "Ann", Role.KIA_OFFICER, "KIA", 4, true);

        assertThat(user.id()).isEqualTo(1L);
        assertThat(user.email()).isEqualTo("a@b.co");
        assertThat(user.fullName()).isEqualTo("Ann");
        assertThat(user.role()).isEqualTo(Role.KIA_OFFICER);
        assertThat(user.location()).isEqualTo("KIA");
        assertThat(user.tokenVersion()).isEqualTo(4);
        assertThat(user.mustChangePassword()).isTrue();
    }

    @Test
    void equalityIsValueBased() {
        AuthUser a = new AuthUser(1L, "a@b.co", "Ann", Role.ADMIN, "ALL", 0, false);
        AuthUser b = new AuthUser(1L, "a@b.co", "Ann", Role.ADMIN, "ALL", 0, false);
        AuthUser c = new AuthUser(2L, "a@b.co", "Ann", Role.ADMIN, "ALL", 0, false);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(c);
        assertThat(a.toString()).contains("a@b.co");
    }

    @Test
    void fixtureBuildsFromAccount() {
        AuthUser user = TestFixtures.authUser(TestFixtures.staff(Role.ZNZ_OFFICER, "ZNZ"));

        assertThat(user.role()).isEqualTo(Role.ZNZ_OFFICER);
        assertThat(user.location()).isEqualTo("ZNZ");
        assertThat(user.tokenVersion()).isEqualTo(1);
    }
}
