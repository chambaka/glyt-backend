package tz.co.chambaka.glyt.bootstrap;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tz.co.chambaka.glyt.domain.Airline;
import tz.co.chambaka.glyt.repo.AirlineRepository;

/**
 * Seeds airlines that operate into / out of Tanzania (JRO, DAR, ZNZ corridor).
 * Runs whenever the airlines table is empty, independent of user seeding.
 */
@Component
@Order(20)
public class AirlineSeeder implements CommandLineRunner {

    private final AirlineRepository airlines;

    public AirlineSeeder(AirlineRepository airlines) {
        this.airlines = airlines;
    }

    @Override
    public void run(String... args) {
        if (airlines.count() > 0) {
            return;
        }
        int order = 10;
        // Domestic / regional carriers serving JRO–ZNZ and DAR
        order = add("PW", "Precision Air", order);
        order = add("TC", "Air Tanzania", order);
        order = add("CQ", "Coastal Aviation", order);
        order = add("UI", "Auric Air", order);
        order = add("YS", "Flightlink", order);
        order = add("B4", "ZanAir", order);
        order = add("TCA", "Tropical Air", order);
        order = add("REG", "Regional Air Services", order);
        order = add("FN", "Fastjet Tanzania", order);
        // East / Southern Africa carriers common at JRO / DAR / ZNZ
        order = add("KQ", "Kenya Airways", order);
        order = add("ET", "Ethiopian Airlines", order);
        order = add("WB", "RwandAir", order);
        order = add("UR", "Uganda Airlines", order);
        order = add("SA", "South African Airways", order);
        order = add("TM", "LAM Mozambique Airlines", order);
        order = add("3W", "Malawian Airlines", order);
        // Long-haul carriers serving Tanzania gateways
        order = add("QR", "Qatar Airways", order);
        order = add("TK", "Turkish Airlines", order);
        order = add("EK", "Emirates", order);
        order = add("EY", "Etihad Airways", order);
        order = add("BA", "British Airways", order);
        order = add("KL", "KLM Royal Dutch Airlines", order);
        order = add("LX", "SWISS", order);
        order = add("OS", "Austrian Airlines", order);
        order = add("DE", "Condor", order);
        order = add("WK", "Edelweiss Air", order);
        order = add("OU", "Croatia Airlines", order);
        order = add("TU", "Tunisair", order);
        order = add("MS", "EgyptAir", order);
        add("OTHER", "Other / charter", order);
    }

    private int add(String code, String name, int sortOrder) {
        Airline airline = new Airline();
        airline.setCode(code);
        airline.setName(name);
        airline.setCountry("TZ");
        airline.setActive(true);
        airline.setSortOrder(sortOrder);
        airlines.save(airline);
        return sortOrder + 10;
    }
}
