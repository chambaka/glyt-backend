package tz.co.chambaka.glyt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.BaggageException;
import tz.co.chambaka.glyt.domain.UserAccount;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionState;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionType;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.Priority;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.repo.BagRepository;
import tz.co.chambaka.glyt.repo.BaggageExceptionRepository;
import tz.co.chambaka.glyt.repo.UserAccountRepository;
import tz.co.chambaka.glyt.support.ServiceTestData;
import tz.co.chambaka.glyt.support.TestFixtures;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ExceptionRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ExceptionUpdateRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ExceptionView;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExceptionServiceTest {

    @Mock BaggageExceptionRepository exceptions;
    @Mock BagRepository bags;
    @Mock UserAccountRepository users;
    @Mock StaffContext staff;
    @Mock Journal journal;

    private ExceptionService service;
    private UserAccount actor;
    private Bag bag;

    @BeforeEach
    void setUp() {
        service = new ExceptionService(exceptions, bags, users, staff, journal);
        actor = TestFixtures.staff(Role.KIA_OFFICER, "KIA");
        when(staff.account()).thenReturn(actor);
        bag = ServiceTestData.bag(1L, BagStatus.RECEIVED_KIA);
        when(bags.findById(1L)).thenReturn(Optional.of(bag));
        when(exceptions.save(any(BaggageException.class))).thenAnswer(ServiceTestData.assignId(21L));
    }

    private static void api(Runnable action, HttpStatus status, String message) {
        ApiException ex = ServiceTestData.expectApi(action);
        assertThat(ex.status()).isEqualTo(status);
        assertThat(ex.getMessage()).contains(message);
    }

    private BaggageException openRow(long id) {
        BaggageException row = ServiceTestData.exception(id, bag, ExceptionType.DAMAGED, Priority.HIGH, ExceptionStatus.OPEN);
        when(exceptions.findById(id)).thenReturn(Optional.of(row));
        return row;
    }

    // ---- open ----

    @Test
    void openCreatesRowFlagsBagAndAudits() {
        BaggageException row = service.open(bag, ExceptionType.OVERWEIGHT, Priority.HIGH, "too heavy", actor);

        assertThat(row.getStatus()).isEqualTo(ExceptionStatus.OPEN);
        assertThat(row.getLocation()).isEqualTo("KIA");
        assertThat(row.getRaisedBy()).isSameAs(actor);
        assertThat(bag.getExceptionState()).isEqualTo(ExceptionState.OPEN);
        verify(exceptions).save(row);
        verify(journal).audit(bag, actor, "KIA", "exception opened: Bag overweight", null, "too heavy");
    }

    // ---- raise ----

    @Test
    void raiseCreatesExceptionWithoutAssignee() {
        ExceptionView view = service.raise(new ExceptionRequest(1L, "seal damaged", " cracked ", "critical", null));

        assertThat(view.type()).isEqualTo("SEAL_DAMAGED");
        assertThat(view.priority()).isEqualTo("CRITICAL");
        assertThat(view.description()).isEqualTo("cracked");
        assertThat(view.assignedTo()).isNull();
        assertThat(view.raisedBy()).isEqualTo("Test User");
        assertThat(view.id()).isEqualTo(21L);
        assertThat(view.dueAt()).isNotNull();
        verify(staff).requireWriter();
    }

    @Test
    void raiseAssignsKnownUser() {
        UserAccount assignee = TestFixtures.staff(Role.ZNZ_OFFICER, "ZNZ");
        assignee.setFullName("Zed Officer");
        when(users.findById(20L)).thenReturn(Optional.of(assignee));
        ExceptionView view = service.raise(new ExceptionRequest(1L, "other", "d", "normal", 20L));
        assertThat(view.assignedTo()).isEqualTo("Zed Officer");
    }

    @Test
    void raiseRejectsInvalidInput() {
        when(bags.findById(9L)).thenReturn(Optional.empty());
        api(() -> service.raise(new ExceptionRequest(9L, "other", "d", "normal", null)), HttpStatus.NOT_FOUND, "Bag was not found");
        api(() -> service.raise(new ExceptionRequest(1L, "bogus", "d", "normal", null)), HttpStatus.BAD_REQUEST, "Exception type is not recognised");
        api(() -> service.raise(new ExceptionRequest(1L, "other", "d", "urgent", null)), HttpStatus.BAD_REQUEST, "Priority is not recognised");
        api(() -> service.raise(new ExceptionRequest(1L, "other", " ", "normal", null)), HttpStatus.BAD_REQUEST, "Description is required");
        when(users.findById(99L)).thenReturn(Optional.empty());
        api(() -> service.raise(new ExceptionRequest(1L, "other", "d", "normal", 99L)), HttpStatus.BAD_REQUEST, "Assigned user was not found");
    }

    @Test
    void raiseRequiresWriter() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "read only")).when(staff).requireWriter();
        api(() -> service.raise(new ExceptionRequest(1L, "other", "d", "normal", null)), HttpStatus.FORBIDDEN, "read only");
        verify(exceptions, never()).save(any());
    }

    // ---- update ----

    @Test
    void updateChangesDescriptionPriorityAndAssignee() {
        BaggageException row = openRow(5);
        UserAccount assignee = TestFixtures.staff(Role.ZNZ_OFFICER, "ZNZ");
        when(users.findById(20L)).thenReturn(Optional.of(assignee));

        ExceptionView view = service.update(5L, new ExceptionUpdateRequest(" new text ", "critical", 20L));

        assertThat(row.getDescription()).isEqualTo("new text");
        assertThat(row.getPriority()).isEqualTo(Priority.CRITICAL);
        assertThat(row.getAssignedTo()).isSameAs(assignee);
        assertThat(view.priority()).isEqualTo("CRITICAL");
        verify(exceptions).save(row);
        verify(journal).audit(bag, actor, "KIA", "exception updated: Bag damaged", null, "new text");
    }

    @Test
    void updateLeavesFieldsAloneWhenNotProvided() {
        BaggageException row = openRow(5);
        service.update(5L, new ExceptionUpdateRequest(null, null, null));
        service.update(5L, new ExceptionUpdateRequest(null, "  ", null));
        assertThat(row.getDescription()).isEqualTo("desc 5");
        assertThat(row.getPriority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void updateClearsAssigneeWhenIdNotPositive() {
        BaggageException row = openRow(5);
        row.setAssignedTo(actor);
        service.update(5L, new ExceptionUpdateRequest(null, null, 0L));
        assertThat(row.getAssignedTo()).isNull();
    }

    @Test
    void updateRejectsInvalidInput() {
        when(exceptions.findById(404L)).thenReturn(Optional.empty());
        api(() -> service.update(404L, new ExceptionUpdateRequest(null, null, null)), HttpStatus.NOT_FOUND, "Exception was not found");

        BaggageException resolved = ServiceTestData.exception(6, bag, ExceptionType.OTHER, Priority.NORMAL, ExceptionStatus.RESOLVED);
        when(exceptions.findById(6L)).thenReturn(Optional.of(resolved));
        api(() -> service.update(6L, new ExceptionUpdateRequest("x", null, null)), HttpStatus.BAD_REQUEST, "Only open exceptions");

        openRow(5);
        api(() -> service.update(5L, new ExceptionUpdateRequest(" ", null, null)), HttpStatus.BAD_REQUEST, "Description is required");
        api(() -> service.update(5L, new ExceptionUpdateRequest(null, "urgent", null)), HttpStatus.BAD_REQUEST, "Priority is not recognised");
        when(users.findById(99L)).thenReturn(Optional.empty());
        api(() -> service.update(5L, new ExceptionUpdateRequest(null, null, 99L)), HttpStatus.BAD_REQUEST, "Assigned user was not found");
    }

    // ---- resolve ----

    @Test
    void resolveClosesExceptionAndClearsBagFlagWhenNoneRemain() {
        BaggageException row = openRow(5);
        bag.setExceptionState(ExceptionState.OPEN);
        when(exceptions.countByBagIdAndStatus(1L, ExceptionStatus.OPEN)).thenReturn(0L);

        ExceptionView view = service.resolve(5L, " fixed ");

        assertThat(row.getStatus()).isEqualTo(ExceptionStatus.RESOLVED);
        assertThat(row.getResolutionNotes()).isEqualTo("fixed");
        assertThat(row.getResolvedBy()).isSameAs(actor);
        assertThat(row.getResolvedAt()).isNotNull();
        assertThat(bag.getExceptionState()).isEqualTo(ExceptionState.NONE);
        assertThat(view.resolvedBy()).isEqualTo("Test User");
        assertThat(view.resolvedAt()).isNotNull();
        verify(exceptions).saveAndFlush(row);
        verify(bags).save(bag);
        verify(journal).audit(bag, actor, "KIA", "exception resolved: Bag damaged", "OPEN", "fixed");
    }

    @Test
    void resolveKeepsBagFlaggedWhileOtherExceptionsAreOpen() {
        openRow(5);
        when(exceptions.countByBagIdAndStatus(1L, ExceptionStatus.OPEN)).thenReturn(2L);
        service.resolve(5L, "ok");
        assertThat(bag.getExceptionState()).isEqualTo(ExceptionState.OPEN);
    }

    @Test
    void resolveRejectsMissingAlreadyResolvedAndBlankNotes() {
        when(exceptions.findById(404L)).thenReturn(Optional.empty());
        api(() -> service.resolve(404L, "x"), HttpStatus.NOT_FOUND, "Exception was not found");

        BaggageException resolved = ServiceTestData.exception(6, bag, ExceptionType.OTHER, Priority.NORMAL, ExceptionStatus.RESOLVED);
        when(exceptions.findById(6L)).thenReturn(Optional.of(resolved));
        api(() -> service.resolve(6L, "x"), HttpStatus.BAD_REQUEST, "already resolved");

        openRow(5);
        api(() -> service.resolve(5L, "  "), HttpStatus.BAD_REQUEST, "Resolution notes");
        verify(exceptions, never()).saveAndFlush(any());
    }

    // ---- list / toView ----

    @Test
    void listFiltersByStatus() {
        BaggageException row = ServiceTestData.exception(5, bag, ExceptionType.DAMAGED, Priority.HIGH, ExceptionStatus.OPEN);
        when(exceptions.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(row, row));
        when(exceptions.findByStatusOrderByCreatedAtDesc(ExceptionStatus.RESOLVED)).thenReturn(List.of(row));

        assertThat(service.list(null)).hasSize(2);
        assertThat(service.list("  ")).hasSize(2);
        assertThat(service.list("resolved")).hasSize(1);
        api(() -> service.list("bogus"), HttpStatus.BAD_REQUEST, "Status is not recognised");
    }

    @Test
    void toViewFlagsOverdueOpenExceptionsOnly() {
        BaggageException overdue = ServiceTestData.exception(1, bag, ExceptionType.DAMAGED, Priority.CRITICAL, ExceptionStatus.OPEN);
        overdue.setCreatedAt(Instant.now().minus(Duration.ofHours(5)));
        BaggageException onTime = ServiceTestData.exception(2, bag, ExceptionType.DAMAGED, Priority.NORMAL, ExceptionStatus.OPEN);
        BaggageException resolvedLate = ServiceTestData.exception(3, bag, ExceptionType.DAMAGED, Priority.CRITICAL, ExceptionStatus.RESOLVED);
        resolvedLate.setCreatedAt(Instant.now().minus(Duration.ofHours(5)));
        resolvedLate.setResolvedBy(actor);
        resolvedLate.setResolvedAt(Instant.now());

        ExceptionView overdueView = service.toView(overdue);
        assertThat(overdueView.overdue()).isTrue();
        assertThat(overdueView.ageHours()).isBetween(4L, 5L);
        assertThat(overdueView.typeLabel()).isEqualTo("Bag damaged");
        assertThat(service.toView(onTime).overdue()).isFalse();
        ExceptionView resolvedView = service.toView(resolvedLate);
        assertThat(resolvedView.overdue()).isFalse();
        assertThat(resolvedView.resolvedBy()).isEqualTo("Test User");
        assertThat(resolvedView.assignedTo()).isNull();
    }
}
