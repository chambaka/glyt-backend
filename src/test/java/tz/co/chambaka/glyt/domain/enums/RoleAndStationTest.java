package tz.co.chambaka.glyt.domain.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleAndStationTest {

    @Test
    void roleValues() {
        assertThat(Role.values()).containsExactly(
                Role.ADMIN, Role.KIA_OFFICER, Role.ZNZ_OFFICER, Role.READONLY, Role.CUSTOMER);
    }

    @Test
    void roleValueOf() {
        assertThat(Role.valueOf("KIA_OFFICER")).isSameAs(Role.KIA_OFFICER);
        assertThatThrownBy(() -> Role.valueOf("ROOT")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stationValues() {
        assertThat(Station.values()).containsExactly(Station.KIA, Station.ZNZ, Station.ALL);
        assertThat(Station.valueOf("ALL")).isSameAs(Station.ALL);
    }

    @Test
    void stationValueOfRejectsUnknown() {
        assertThatThrownBy(() -> Station.valueOf("DAR")).isInstanceOf(IllegalArgumentException.class);
    }
}
