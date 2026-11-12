package tz.co.chambaka.glyt.service;

import org.springframework.stereotype.Service;
import tz.co.chambaka.glyt.domain.AuditLog;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.CustodyEvent;
import tz.co.chambaka.glyt.domain.UserAccount;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.CustodyEventType;
import tz.co.chambaka.glyt.repo.AuditLogRepository;
import tz.co.chambaka.glyt.repo.CustodyEventRepository;

@Service
public class Journal {

    private final AuditLogRepository audits;
    private final CustodyEventRepository custody;

    public Journal(AuditLogRepository audits, CustodyEventRepository custody) {
        this.audits = audits;
        this.custody = custody;
    }

    public void audit(Bag bag, UserAccount user, String location, String action, String previous, String next) {
        AuditLog row = new AuditLog();
        row.setBag(bag);
        row.setUser(user);
        row.setLocation(location);
        row.setAction(action);
        row.setPreviousValue(previous);
        row.setNewValue(next);
        audits.save(row);
    }

    public void custody(
            Bag bag,
            UserAccount user,
            CustodyEventType type,
            String location,
            String notes,
            String sealNumber,
            BagStatus previous,
            BagStatus next
    ) {
        CustodyEvent event = new CustodyEvent();
        event.setBag(bag);
        event.setUser(user);
        event.setEventType(type);
        event.setLocation(location);
        event.setNotes(notes);
        event.setSealNumber(sealNumber);
        event.setPreviousStatus(previous);
        event.setNewStatus(next);
        custody.save(event);
    }
}
