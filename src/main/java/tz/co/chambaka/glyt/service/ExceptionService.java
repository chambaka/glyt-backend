package tz.co.chambaka.glyt.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.BaggageException;
import tz.co.chambaka.glyt.domain.UserAccount;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionState;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionType;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.Priority;
import tz.co.chambaka.glyt.repo.BagRepository;
import tz.co.chambaka.glyt.repo.BaggageExceptionRepository;
import tz.co.chambaka.glyt.repo.UserAccountRepository;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ExceptionRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ExceptionUpdateRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ExceptionView;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class ExceptionService {

    private final BaggageExceptionRepository exceptions;
    private final BagRepository bags;
    private final UserAccountRepository users;
    private final StaffContext staff;
    private final Journal journal;

    public ExceptionService(
            BaggageExceptionRepository exceptions,
            BagRepository bags,
            UserAccountRepository users,
            StaffContext staff,
            Journal journal
    ) {
        this.exceptions = exceptions;
        this.bags = bags;
        this.users = users;
        this.staff = staff;
        this.journal = journal;
    }

    @Transactional
    public BaggageException open(Bag bag, ExceptionType type, Priority priority, String description, UserAccount actor) {
        BaggageException row = new BaggageException();
        row.setBag(bag);
        row.setType(type);
        row.setPriority(priority);
        row.setDescription(description);
        row.setLocation(bag.getCurrentLocation());
        row.setRaisedBy(actor);
        row.setStatus(ExceptionStatus.OPEN);
        exceptions.save(row);
        bag.setExceptionState(ExceptionState.OPEN);
        journal.audit(bag, actor, bag.getCurrentLocation(), "exception opened: " + type.label(), null, description);
        return row;
    }

    @Transactional
    public ExceptionView raise(ExceptionRequest request) {
        staff.requireWriter();
        UserAccount actor = staff.account();
        Bag bag = bags.findById(request.bagId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bag was not found."));
        ExceptionType type = Texts.parse(ExceptionType.class, request.type(), "Exception type");
        Priority priority = Texts.parse(Priority.class, request.priority(), "Priority");
        String description = Texts.required(request.description(), "Description");
        BaggageException row = open(bag, type, priority, description, actor);
        if (request.assignedToId() != null) {
            UserAccount assignee = users.findById(request.assignedToId())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Assigned user was not found."));
            row.setAssignedTo(assignee);
        }
        return toView(row);
    }

    @Transactional
    public ExceptionView update(Long id, ExceptionUpdateRequest request) {
        staff.requireWriter();
        BaggageException row = exceptions.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Exception was not found."));
        if (row.getStatus() != ExceptionStatus.OPEN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only open exceptions can be updated.");
        }
        if (request.description() != null) {
            row.setDescription(Texts.required(request.description(), "Description"));
        }
        if (request.priority() != null && !request.priority().isBlank()) {
            row.setPriority(Texts.parse(Priority.class, request.priority(), "Priority"));
        }
        if (request.assignedToId() != null) {
            if (request.assignedToId() <= 0) {
                row.setAssignedTo(null);
            } else {
                UserAccount assignee = users.findById(request.assignedToId())
                        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Assigned user was not found."));
                row.setAssignedTo(assignee);
            }
        }
        exceptions.save(row);
        journal.audit(
                row.getBag(),
                staff.account(),
                row.getBag().getCurrentLocation(),
                "exception updated: " + row.getType().label(),
                null,
                row.getDescription()
        );
        return toView(row);
    }

    @Transactional
    public ExceptionView resolve(Long id, String notes) {
        staff.requireWriter();
        UserAccount actor = staff.account();
        BaggageException row = exceptions.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Exception was not found."));
        if (row.getStatus() == ExceptionStatus.RESOLVED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This exception is already resolved.");
        }
        String resolution = Texts.required(notes, "Resolution notes");
        row.setStatus(ExceptionStatus.RESOLVED);
        row.setResolutionNotes(resolution);
        row.setResolvedBy(actor);
        row.setResolvedAt(Instant.now());
        exceptions.saveAndFlush(row);
        Bag bag = row.getBag();
        long stillOpen = exceptions.countByBagIdAndStatus(bag.getId(), ExceptionStatus.OPEN);
        // Clear the bag alert state when no open exceptions remain.
        bag.setExceptionState(stillOpen == 0 ? ExceptionState.NONE : ExceptionState.OPEN);
        bags.save(bag);
        journal.audit(bag, actor, bag.getCurrentLocation(), "exception resolved: " + row.getType().label(), "OPEN", resolution);
        return toView(row);
    }

    @Transactional(readOnly = true)
    public List<ExceptionView> list(String status) {
        List<BaggageException> rows = status == null || status.isBlank()
                ? exceptions.findAllByOrderByCreatedAtDesc()
                : exceptions.findByStatusOrderByCreatedAtDesc(Texts.parse(ExceptionStatus.class, status, "Status"));
        return rows.stream().map(this::toView).toList();
    }

    public ExceptionView toView(BaggageException row) {
        Instant due = PortalDepthService.dueAt(row);
        long ageHours = Duration.between(row.getCreatedAt(), Instant.now()).toHours();
        boolean overdue = row.getStatus() == ExceptionStatus.OPEN && Instant.now().isAfter(due);
        return new ExceptionView(
                row.getId(),
                row.getBag().getId(),
                row.getBag().getCode(),
                row.getType().name(),
                row.getType().label(),
                row.getDescription(),
                row.getPriority().name(),
                row.getStatus().name(),
                row.getLocation(),
                row.getRaisedBy().getFullName(),
                row.getAssignedTo() == null ? null : row.getAssignedTo().getFullName(),
                row.getResolutionNotes(),
                row.getResolvedBy() == null ? null : row.getResolvedBy().getFullName(),
                row.getResolvedAt() == null ? null : row.getResolvedAt().toString(),
                row.getCreatedAt().toString(),
                due.toString(),
                overdue,
                ageHours
        );
    }
}
