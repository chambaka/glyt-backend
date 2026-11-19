package tz.co.chambaka.glyt.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.chambaka.glyt.domain.NotificationOutbox;
import tz.co.chambaka.glyt.repo.NotificationOutboxRepository;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationOutboxRepository outbox;
    @InjectMocks NotificationService service;

    @Test
    void enqueueIgnoresBlankRecipients() {
        service.enqueue("EMAIL", null, "T", "{}");
        service.enqueue("EMAIL", "   ", "T", "{}");
        verify(outbox, never()).save(any());
    }

    @Test
    void enqueueStoresTrimmedRecipientAndUppercaseChannel() {
        service.enqueue(" sms ", " a@b.com ", "TPL", "{\"a\":1}");

        ArgumentCaptor<NotificationOutbox> saved = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(outbox).save(saved.capture());
        assertThat(saved.getValue().getChannel()).isEqualTo("SMS");
        assertThat(saved.getValue().getRecipient()).isEqualTo("a@b.com");
        assertThat(saved.getValue().getTemplateCode()).isEqualTo("TPL");
        assertThat(saved.getValue().getPayloadJson()).isEqualTo("{\"a\":1}");
        assertThat(saved.getValue().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void enqueueDefaultsChannelToEmail() {
        service.enqueue(null, "a@b.com", "TPL", null);
        service.enqueue("  ", "a@b.com", "TPL", null);
        ArgumentCaptor<NotificationOutbox> saved = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(outbox, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allMatch(row -> "EMAIL".equals(row.getChannel()));
    }

    private static NotificationOutbox pending(String channel) {
        NotificationOutbox row = new NotificationOutbox();
        row.setChannel(channel);
        row.setRecipient("a@b.com");
        row.setTemplateCode("TPL");
        row.setPayloadJson("{}");
        row.setStatus("PENDING");
        return row;
    }

    @Test
    void processPendingMarksRowsSentUpToTheLimit() {
        List<NotificationOutbox> rows = new ArrayList<>(List.of(pending("EMAIL"), pending("SMS"), pending("EMAIL")));
        when(outbox.findTop50ByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(rows);

        int processed = service.processPending(2);

        assertThat(processed).isEqualTo(2);
        assertThat(rows.get(0).getStatus()).isEqualTo("SENT");
        assertThat(rows.get(0).getSentAt()).isNotNull();
        assertThat(rows.get(0).getAttempts()).isEqualTo(1);
        assertThat(rows.get(0).getLastError()).isNull();
        assertThat(rows.get(1).getStatus()).isEqualTo("SENT");
        assertThat(rows.get(2).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void processPendingWithNothingPending() {
        when(outbox.findTop50ByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of());
        assertThat(service.processPending(10)).isZero();
    }

    @Test
    void processPendingWithZeroLimitProcessesNothing() {
        NotificationOutbox row = pending("EMAIL");
        when(outbox.findTop50ByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of(row));
        assertThat(service.processPending(0)).isZero();
        assertThat(row.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void deliveryFailureKeepsRowPendingUntilFiveAttempts() {
        NotificationOutbox retry = spy(pending("EMAIL"));
        doThrow(new IllegalStateException("smtp down")).when(retry).setStatus("SENT");
        NotificationOutbox exhausted = spy(pending("EMAIL"));
        exhausted.setAttempts(4);
        doThrow(new IllegalStateException("smtp down")).when(exhausted).setStatus("SENT");
        when(outbox.findTop50ByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of(retry, exhausted));

        assertThat(service.processPending(10)).isEqualTo(2);

        assertThat(retry.getStatus()).isEqualTo("PENDING");
        assertThat(retry.getAttempts()).isEqualTo(1);
        assertThat(retry.getLastError()).isEqualTo("smtp down");
        assertThat(exhausted.getStatus()).isEqualTo("FAILED");
        assertThat(exhausted.getAttempts()).isEqualTo(5);
    }
}
