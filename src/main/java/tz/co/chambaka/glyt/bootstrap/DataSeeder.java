package tz.co.chambaka.glyt.bootstrap;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
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

@Component
public class DataSeeder implements CommandLineRunner {

    private final AppProperties properties;
    private final UserAccountRepository users;
    private final StorageLocationRepository storage;
    private final LocationRepository locations;
    private final CounterRepository counters;
    private final PasswordEncoder passwords;

    public DataSeeder(
            AppProperties properties,
            UserAccountRepository users,
            StorageLocationRepository storage,
            LocationRepository locations,
            CounterRepository counters,
            PasswordEncoder passwords
    ) {
        this.properties = properties;
        this.users = users;
        this.storage = storage;
        this.locations = locations;
        this.counters = counters;
        this.passwords = passwords;
    }

    @Override
    public void run(String... args) {
        if (!properties.seed().enabled() || users.count() > 0) {
            return;
        }
        Location kia = place("KIA", "Kilimanjaro International Airport", "AIRPORT");
        Location znz = place("ZNZ", "Abeid Amani Karume International Airport", "AIRPORT");
        user("Amina Hassan", "lead@golyt.co.tz", "+255700000001", Role.ADMIN, null);
        user("John Mrema", "kia@golyt.co.tz", "+255700000002", Role.KIA_OFFICER, kia);
        user("Fatma Ali", "znz@golyt.co.tz", "+255700000003", Role.ZNZ_OFFICER, znz);
        user("Peter Mgaya", "board@golyt.co.tz", "+255700000004", Role.READONLY, null);
        for (String row : new String[]{"A", "B"}) {
            for (int i = 1; i <= 6; i++) {
                StorageLocation location = new StorageLocation();
                location.setCode(row + "-" + String.format("%02d", i));
                storage.save(location);
            }
        }
        counter("bag");
        counter("manifest");
        counter("customer");
        counter("booking");
        counter("receipt");
    }

    private Location place(String code, String name, String type) {
        Location location = new Location();
        location.setCode(code);
        location.setName(name);
        location.setLocationType(type);
        return locations.save(location);
    }

    private void user(String name, String email, String phone, Role role, Location location) {
        UserAccount user = new UserAccount();
        user.setFullName(name);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwords.encode("Golyt#2026"));
        user.setRole(role);
        user.setLocation(location);
        user.setActive(true);
        user.setMustChangePassword(false);
        users.save(user);
    }

    private void counter(String name) {
        Counter counter = new Counter();
        counter.setName(name);
        counter.setValue(0);
        counters.save(counter);
    }
}
