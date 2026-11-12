package tz.co.chambaka.glyt.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.chambaka.glyt.domain.NotificationOutbox;
import tz.co.chambaka.glyt.repo.NotificationOutboxRepository;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationOutboxRepository outbox;

    public NotificationService(NotificationOutboxRepository outbox) {
        this.outbox = outbox;
    }

    @Transactional
    public void enqueue(String channel, String recipient, String templateCode, String payloadJson) {
        if (recipient == null || recipient.isBlank()) {
            return;
        }
        NotificationOutbox row = new NotificationOutbox();
        row.setChannel(channel == null || channel.isBlank() ? "EMAIL" : channel.trim().toUpperCase());
        row.setRecipient(recipient.trim());
        row.setTemplateCode(templateCode);
        row.setPayloadJson(payloadJson);
        row.setStatus("PENDING");
        outbox.save(row);
        log.info("Queued {} notification {} -> {}", row.getChannel(), row.getTemplateCode(), row.getRecipient());
    }

    @Transactional
    public int processPending(int limit) {
        List<NotificationOutbox> pending = outbox.findTop50ByStatusOrderByCreatedAtAsc("PENDING");
        int processed = 0;
        for (NotificationOutbox row : pending) {
            if (processed >= limit) {
                break;
            }
            try {
                // Mail autoconfig is excluded in the pilot; mark as logged delivery for now.
                log.info("Deliver {} [{}] to {} payload={}", row.getChannel(), row.getTemplateCode(), row.getRecipient(), row.getPayloadJson());
                row.setStatus("SENT");
                row.setSentAt(Instant.now());
                row.setAttempts(row.getAttempts() + 1);
                row.setLastError(null);
            } catch (Exception ex) {
                row.setAttempts(row.getAttempts() + 1);
                row.setLastError(ex.getMessage());
                row.setStatus(row.getAttempts() >= 5 ? "FAILED" : "PENDING");
            }
            processed++;
        }
        return processed;
    }
}
