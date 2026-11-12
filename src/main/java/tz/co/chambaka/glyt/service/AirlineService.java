package tz.co.chambaka.glyt.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.chambaka.glyt.domain.Airline;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.repo.AirlineRepository;
import tz.co.chambaka.glyt.repo.ManifestRepository;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.AirlineView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.AirlineWrite;

import java.util.List;
import java.util.Locale;

@Service
public class AirlineService {

    private final StaffContext staff;
    private final AirlineRepository airlines;
    private final ManifestRepository manifests;
    private final Journal journal;

    public AirlineService(
            StaffContext staff,
            AirlineRepository airlines,
            ManifestRepository manifests,
            Journal journal
    ) {
        this.staff = staff;
        this.airlines = airlines;
        this.manifests = manifests;
        this.journal = journal;
    }

    @Transactional(readOnly = true)
    public List<AirlineView> list(Boolean activeOnly) {
        staff.requireStaff();
        List<Airline> rows = Boolean.TRUE.equals(activeOnly)
                ? airlines.findByActiveTrueOrderBySortOrderAscNameAsc()
                : airlines.findAllByOrderBySortOrderAscNameAsc();
        return rows.stream().map(this::toView).toList();
    }

    @Transactional
    public AirlineView create(AirlineWrite request) {
        staff.require(Role.ADMIN);
        String code = Texts.required(request.code(), "Code").toUpperCase(Locale.ROOT);
        if (airlines.findByCodeIgnoreCase(code).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "That airline code already exists.");
        }
        Airline airline = new Airline();
        airline.setCode(code);
        airline.setName(Texts.required(request.name(), "Name"));
        airline.setCountry(countryOrTz(request.country()));
        airline.setActive(request.active() == null || request.active());
        airline.setSortOrder(request.sortOrder() == null ? 100 : request.sortOrder());
        airlines.save(airline);
        journal.audit(null, staff.account(), "OPS", "airline created " + code, null, airline.getName());
        return toView(airline);
    }

    @Transactional
    public AirlineView update(Long id, AirlineWrite request) {
        staff.require(Role.ADMIN);
        Airline airline = airlines.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Airline was not found."));
        if (request.name() != null && !request.name().isBlank()) {
            airline.setName(request.name().trim());
        }
        if (request.country() != null && !request.country().isBlank()) {
            airline.setCountry(countryOrTz(request.country()));
        }
        if (request.active() != null) {
            airline.setActive(request.active());
        }
        if (request.sortOrder() != null) {
            airline.setSortOrder(request.sortOrder());
        }
        journal.audit(null, staff.account(), "OPS", "airline updated " + airline.getCode(), null, airline.getName());
        return toView(airline);
    }

    @Transactional
    public void delete(Long id) {
        staff.require(Role.ADMIN);
        Airline airline = airlines.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Airline was not found."));
        long used = manifests.countByAirlineIgnoreCase(airline.getName())
                + manifests.countByAirlineIgnoreCase(airline.getCode());
        if (used > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Cannot delete " + airline.getName() + ": " + used + " manifest(s) still reference it. Deactivate instead."
            );
        }
        String code = airline.getCode();
        airlines.delete(airline);
        journal.audit(null, staff.account(), "OPS", "airline deleted " + code, code, null);
    }

    /** Resolve a free-text airline to the catalog name (by code or name). */
    @Transactional(readOnly = true)
    public String resolveName(String raw) {
        String value = Texts.required(raw, "Airline").trim();
        return airlines.findByCodeIgnoreCase(value)
                .filter(Airline::isActive)
                .map(Airline::getName)
                .or(() -> airlines.findByNameIgnoreCase(value).filter(Airline::isActive).map(Airline::getName))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Choose an airline from the configured Tanzania corridor list."
                ));
    }

    private String countryOrTz(String country) {
        if (country == null || country.isBlank()) {
            return "TZ";
        }
        return country.trim().toUpperCase(Locale.ROOT);
    }

    private AirlineView toView(Airline airline) {
        return new AirlineView(
                airline.getId(),
                airline.getCode(),
                airline.getName(),
                airline.getCountry(),
                airline.isActive(),
                airline.getSortOrder()
        );
    }
}
