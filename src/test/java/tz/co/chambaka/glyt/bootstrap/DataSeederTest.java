package tz.co.chambaka.glyt.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.domain.Counter;
import tz.co.chambaka.glyt.domain.Location;
import tz.co.chambaka.glyt.domain.StorageLocation;
import tz.co.chambaka.glyt.domain.UserAccount;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.repo.CounterRepository;
import tz.co.chambaka.glyt.repo.LocationRepository;
import tz.co.chambaka.glyt.repo.StorageLocationRepository;
import tz.co.chambaka.glyt.repo.UserAccountRepository;
import tz.co.chambaka.glyt.support.TestFixtures;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    UserAccountRepository users;
    @Mock
    StorageLocationRepository storage;
    @Mock
    LocationRepository locations;
    @Mock
    CounterRepository counters;
    @Mock
    PasswordEncoder passwords;

    private static AppProperties seedProps(boolean enabled) {
        AppProperties base = TestFixtures.appProperties();
        return new AppProperties(
                base.jwt(), base.cors(), base.storage(), base.ops(),
                new AppProperties.Seed(enabled), base.mail(), base.payments());
    }

    private DataSeeder seeder(boolean enabled) {
        return new DataSeeder(seedProps(enabled), users, storage, locations, counters, passwords);
    }

    @Test
    void doesNothingWhenSeedingDisabled() {
        seeder(false).run();

        verifyNoInteractions(storage, locations, counters, passwords);
        verify(users, never()).save(any());
    }

    @Test
    void doesNothingWhenUsersAlreadyExist() {
        when(users.count()).thenReturn(3L);

        seeder(true).run();

        verify(users).count();
        verifyNoMoreInteractions(users);
        verifyNoInteractions(storage, locations, counters, passwords);
    }

    @Test
    void seedsLocationsUsersStorageAndCounters() {
        when(users.count()).thenReturn(0L);
        when(locations.save(any(Location.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwords.encode("Golyt#2026")).thenReturn("ENCODED");

        seeder(true).run(new String[0]);

        // locations
        ArgumentCaptor<Location> locCaptor = ArgumentCaptor.forClass(Location.class);
        verify(locations, times(2)).save(locCaptor.capture());
        Map<String, Location> byCode = locCaptor.getAllValues().stream()
                .collect(Collectors.toMap(Location::getCode, Function.identity()));
        assertThat(byCode).containsOnlyKeys("KIA", "ZNZ");
        assertThat(byCode.get("KIA").getName()).isEqualTo("Kilimanjaro International Airport");
        assertThat(byCode.get("ZNZ").getLocationType()).isEqualTo("AIRPORT");

        // users
        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(users, times(4)).save(userCaptor.capture());
        Map<String, UserAccount> byEmail = userCaptor.getAllValues().stream()
                .collect(Collectors.toMap(UserAccount::getEmail, Function.identity()));
        assertThat(byEmail).containsOnlyKeys(
                "lead@golyt.co.tz", "kia@golyt.co.tz", "znz@golyt.co.tz", "board@golyt.co.tz");
        assertThat(byEmail.get("lead@golyt.co.tz").getRole()).isEqualTo(Role.ADMIN);
        assertThat(byEmail.get("lead@golyt.co.tz").getLocation()).isNull();
        assertThat(byEmail.get("kia@golyt.co.tz").getRole()).isEqualTo(Role.KIA_OFFICER);
        assertThat(byEmail.get("kia@golyt.co.tz").getLocation()).isSameAs(byCode.get("KIA"));
        assertThat(byEmail.get("znz@golyt.co.tz").getRole()).isEqualTo(Role.ZNZ_OFFICER);
        assertThat(byEmail.get("znz@golyt.co.tz").getLocation()).isSameAs(byCode.get("ZNZ"));
        assertThat(byEmail.get("board@golyt.co.tz").getRole()).isEqualTo(Role.READONLY);
        assertThat(byEmail.values()).allSatisfy(u -> {
            assertThat(u.getPasswordHash()).isEqualTo("ENCODED");
            assertThat(u.isActive()).isTrue();
            assertThat(u.isMustChangePassword()).isFalse();
            assertThat(u.getPhone()).startsWith("+2557");
        });
        verify(passwords, times(4)).encode("Golyt#2026");

        // storage locations A-01..A-06, B-01..B-06
        ArgumentCaptor<StorageLocation> storageCaptor = ArgumentCaptor.forClass(StorageLocation.class);
        verify(storage, times(12)).save(storageCaptor.capture());
        List<String> codes = storageCaptor.getAllValues().stream().map(StorageLocation::getCode).toList();
        assertThat(codes).containsExactly(
                "A-01", "A-02", "A-03", "A-04", "A-05", "A-06",
                "B-01", "B-02", "B-03", "B-04", "B-05", "B-06");

        // counters
        ArgumentCaptor<Counter> counterCaptor = ArgumentCaptor.forClass(Counter.class);
        verify(counters, times(5)).save(counterCaptor.capture());
        assertThat(counterCaptor.getAllValues()).extracting(Counter::getName)
                .containsExactly("bag", "manifest", "customer", "booking", "receipt");
        assertThat(counterCaptor.getAllValues()).allSatisfy(c -> assertThat(c.getValue()).isZero());
    }
}
