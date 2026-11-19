package tz.co.chambaka.glyt.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.chambaka.glyt.domain.AuditLog;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.CustodyEvent;
import tz.co.chambaka.glyt.domain.UserAccount;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.CustodyEventType;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.repo.AuditLogRepository;
import tz.co.chambaka.glyt.repo.CustodyEventRepository;
import tz.co.chambaka.glyt.support.TestFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class JournalTest {

    @Mock
    AuditLogRepository audits;

    @Mock
    CustodyEventRepository custody;

    @Test
    void auditPersistsRowWithAllFields() {
        Journal journal = new Journal(audits, custody);
        Bag bag = new Bag();
        bag.setCode("BAG-1");
        UserAccount user = TestFixtures.staff(Role.KIA_OFFICER, "KIA");

        journal.audit(bag, user, "KIA", "STATUS_CHANGE", "BOOKED", "RECEIVED_KIA");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(audits).save(captor.capture());
        AuditLog row = captor.getValue();
        assertThat(row.getBag()).isSameAs(bag);
        assertThat(row.getUser()).isSameAs(user);
        assertThat(row.getLocation()).isEqualTo("KIA");
        assertThat(row.getAction()).isEqualTo("STATUS_CHANGE");
        assertThat(row.getPreviousValue()).isEqualTo("BOOKED");
        assertThat(row.getNewValue()).isEqualTo("RECEIVED_KIA");
        assertThat(row.getCreatedAt()).isNotNull();
        verifyNoInteractions(custody);
    }

    @Test
    void auditAllowsNullBagAndUser() {
        Journal journal = new Journal(audits, custody);

        journal.audit(null, null, "ALL", "LOGIN", null, null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(audits).save(captor.capture());
        assertThat(captor.getValue().getBag()).isNull();
        assertThat(captor.getValue().getUser()).isNull();
        assertThat(captor.getValue().getPreviousValue()).isNull();
    }

    @Test
    void custodyPersistsEventWithAllFields() {
        Journal journal = new Journal(audits, custody);
        Bag bag = new Bag();
        UserAccount user = TestFixtures.staff(Role.ZNZ_OFFICER, "ZNZ");

        journal.custody(
                bag, user, CustodyEventType.ZNZ_RECEIPT, "ZNZ", "arrived ok", "SEAL-9",
                BagStatus.HANDED_AIRLINE, BagStatus.RECEIVED_ZNZ
        );

        ArgumentCaptor<CustodyEvent> captor = ArgumentCaptor.forClass(CustodyEvent.class);
        verify(custody).save(captor.capture());
        CustodyEvent event = captor.getValue();
        assertThat(event.getBag()).isSameAs(bag);
        assertThat(event.getUser()).isSameAs(user);
        assertThat(event.getEventType()).isEqualTo(CustodyEventType.ZNZ_RECEIPT);
        assertThat(event.getLocation()).isEqualTo("ZNZ");
        assertThat(event.getNotes()).isEqualTo("arrived ok");
        assertThat(event.getSealNumber()).isEqualTo("SEAL-9");
        assertThat(event.getPreviousStatus()).isEqualTo(BagStatus.HANDED_AIRLINE);
        assertThat(event.getNewStatus()).isEqualTo(BagStatus.RECEIVED_ZNZ);
        verifyNoInteractions(audits);
    }

    @Test
    void custodyAllowsNullOptionalFields() {
        Journal journal = new Journal(audits, custody);

        journal.custody(new Bag(), null, CustodyEventType.CANCELLED, "KIA", null, null, null, BagStatus.CANCELLED);

        ArgumentCaptor<CustodyEvent> captor = ArgumentCaptor.forClass(CustodyEvent.class);
        verify(custody).save(captor.capture());
        assertThat(captor.getValue().getNotes()).isNull();
        assertThat(captor.getValue().getSealNumber()).isNull();
        assertThat(captor.getValue().getPreviousStatus()).isNull();
        assertThat(captor.getValue().getNewStatus()).isEqualTo(BagStatus.CANCELLED);
    }
}
