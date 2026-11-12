package tz.co.chambaka.glyt.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.repo.BagRepository;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PickupView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PublicTrack;
import tz.co.chambaka.glyt.web.dto.GlytDtos.TimelineStep;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PublicTrackService {

    private static final ZoneId DAR = ZoneId.of("Africa/Dar_es_Salaam");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(DAR);

    private final BagRepository bags;
    private final AppProperties properties;

    public PublicTrackService(BagRepository bags, AppProperties properties) {
        this.bags = bags;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public PublicTrack track(String code, String token) {
        Bag bag = bags.findByCodeIgnoreCase(code)
                .filter(found -> token != null && token.equals(found.trackingValue()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This tracking link is not valid."));
        int rank = bag.getStatus().rank();
        List<TimelineStep> timeline = List.of(
                step("Received at Kilimanjaro", rank >= BagStatus.RECEIVED_KIA.rank()),
                step("Security sealed", rank >= BagStatus.SEALED.rank()),
                step("Handed to airline", rank >= BagStatus.HANDED_AIRLINE.rank()),
                step("Received in Zanzibar", rank >= BagStatus.RECEIVED_ZNZ.rank()),
                step("Secured in Zanzibar", rank >= BagStatus.STORED.rank()),
                step("Ready for collection", rank >= BagStatus.READY.rank()),
                step("Collected", rank >= BagStatus.COLLECTED.rank())
        );
        var ops = properties.ops();
        return new PublicTrack(
                bag.getCode(),
                bag.getStatus().customerLabel(),
                FORMAT.format(bag.getUpdatedAt()),
                timeline,
                new PickupView(ops.pickupName(), ops.pickupPlace(), ops.pickupHours(), ops.pickupPhone(), ops.pickupWhatsapp())
        );
    }

    private TimelineStep step(String label, boolean done) {
        return new TimelineStep(label, done);
    }
}
