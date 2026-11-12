package tz.co.chambaka.glyt.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.BaggageException;
import tz.co.chambaka.glyt.domain.Manifest;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.repo.BagRepository;
import tz.co.chambaka.glyt.repo.BaggageExceptionRepository;
import tz.co.chambaka.glyt.repo.ManifestBagRepository;
import tz.co.chambaka.glyt.repo.ManifestRepository;
import tz.co.chambaka.glyt.repo.StorageLocationRepository;
import tz.co.chambaka.glyt.web.ApiException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class ReportService {

    private static final ZoneId DAR = ZoneId.of("Africa/Dar_es_Salaam");

    private final BagRepository bags;
    private final ManifestRepository manifests;
    private final BaggageExceptionRepository exceptions;
    private final StorageLocationRepository storage;
    private final ManifestBagRepository links;
    private final PortalDepthService portal;

    public ReportService(
            BagRepository bags,
            ManifestRepository manifests,
            BaggageExceptionRepository exceptions,
            StorageLocationRepository storage,
            ManifestBagRepository links,
            PortalDepthService portal
    ) {
        this.bags = bags;
        this.manifests = manifests;
        this.exceptions = exceptions;
        this.storage = storage;
        this.links = links;
        this.portal = portal;
    }

    @Transactional(readOnly = true)
    public String csv(String type, String from, String to, String status, String airline) {
        LocalDate start = from == null || from.isBlank() ? null : LocalDate.parse(from);
        LocalDate end = to == null || to.isBlank() ? null : LocalDate.parse(to);
        return switch (type) {
            case "movement", "movements" -> movement(start, end, status);
            case "manifests" -> manifestReport(start, end, airline);
            case "storage" -> storageReport();
            case "exceptions" -> exceptionReport();
            case "daily" -> movement(LocalDate.now(DAR), LocalDate.now(DAR), status);
            case "collections" -> collections(start, end);
            case "audits" -> portal.auditsCsv();
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Report type is not recognised.");
        };
    }

    private String movement(LocalDate start, LocalDate end, String status) {
        StringBuilder csv = new StringBuilder("bag_id,customer,status,location,weight_kg,seal,manifest,storage,updated\n");
        for (Bag bag : bags.findAll()) {
            if (!inRange(bag.getUpdatedAt().atZone(DAR).toLocalDate(), start, end)) {
                continue;
            }
            if (status != null && !status.isBlank() && !bag.getStatus().name().equalsIgnoreCase(status)) {
                continue;
            }
            csv.append(row(List.of(
                    bag.getCode(),
                    bag.getCustomer().getFullName(),
                    bag.getStatus().staffLabel(),
                    bag.getCurrentLocation(),
                    String.valueOf(bag.getWeightKg()),
                    bag.getSealNumber(),
                    bag.getManifest() == null ? "" : bag.getManifest().getNumber(),
                    bag.getStorageLocation() == null ? "" : bag.getStorageLocation().getCode(),
                    bag.getUpdatedAt().toString()
            )));
        }
        return csv.toString();
    }

    private String manifestReport(LocalDate start, LocalDate end, String airline) {
        StringBuilder csv = new StringBuilder("manifest,date,airline,flight,route,status,bags,weight_kg,prepared_by\n");
        for (Manifest manifest : manifests.findAllByOrderByCreatedAtDesc()) {
            if (!inRange(LocalDate.parse(manifest.getManifestDate()), start, end)) {
                continue;
            }
            if (airline != null && !airline.isBlank() && !manifest.getAirline().toLowerCase().contains(airline.toLowerCase())) {
                continue;
            }
            List<Bag> included = links.findByManifestId(manifest.getId()).stream().map(tz.co.chambaka.glyt.domain.ManifestBag::getBag).toList();
            csv.append(row(List.of(
                    manifest.getNumber(),
                    manifest.getManifestDate(),
                    manifest.getAirline(),
                    manifest.getFlightNumber(),
                    manifest.getRoute(),
                    manifest.getStatus().name(),
                    String.valueOf(included.size()),
                    String.valueOf(included.stream().mapToDouble(Bag::getWeightKg).sum()),
                    manifest.getPreparedBy().getFullName()
            )));
        }
        return csv.toString();
    }

    private String storageReport() {
        StringBuilder csv = new StringBuilder("position,status,bag_id,customer,expected_collection\n");
        for (var location : storage.findAllByOrderByCodeAsc()) {
            Bag bag = bags.findByStorageLocationId(location.getId()).orElse(null);
            csv.append(row(List.of(
                    location.getCode(),
                    bag == null ? "Available" : "Occupied",
                    bag == null ? "" : bag.getCode(),
                    bag == null ? "" : bag.getCustomer().getFullName(),
                    bag == null ? "" : bag.getBooking().getZanzibarTravelDate()
            )));
        }
        return csv.toString();
    }

    private String exceptionReport() {
        StringBuilder csv = new StringBuilder("id,bag_id,type,priority,status,location,raised_at,description\n");
        for (BaggageException row : exceptions.findAllByOrderByCreatedAtDesc()) {
            csv.append(row(List.of(
                    String.valueOf(row.getId()),
                    row.getBag().getCode(),
                    row.getType().label(),
                    row.getPriority().name(),
                    row.getStatus().name(),
                    row.getLocation(),
                    row.getCreatedAt().toString(),
                    row.getDescription()
            )));
        }
        return csv.toString();
    }

    private String collections(LocalDate start, LocalDate end) {
        StringBuilder csv = new StringBuilder("bag_id,customer,collected_at,storage_freed\n");
        for (Bag bag : bags.findByStatus(BagStatus.COLLECTED)) {
            if (!inRange(bag.getUpdatedAt().atZone(DAR).toLocalDate(), start, end)) {
                continue;
            }
            csv.append(row(List.of(bag.getCode(), bag.getCustomer().getFullName(), bag.getUpdatedAt().toString(), "yes")));
        }
        return csv.toString();
    }

    private boolean inRange(LocalDate value, LocalDate start, LocalDate end) {
        if (start != null && value.isBefore(start)) {
            return false;
        }
        return end == null || !value.isAfter(end);
    }

    private String row(List<String> cells) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            String value = cells.get(i) == null ? "" : cells.get(i);
            line.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        line.append('\n');
        return line.toString();
    }
}
