package com.nabeelsayyed.benefitsportal.service;

import com.nabeelsayyed.benefitsportal.model.ClaimEvent;
import com.nabeelsayyed.benefitsportal.model.ClaimEventType;
import com.nabeelsayyed.benefitsportal.model.ClaimStatus;
import com.nabeelsayyed.benefitsportal.repository.ClaimEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Writes and reads the audit history. Every change to a claim goes through here, inside the
 * same database transaction as the change itself: if saving the change fails, the history
 * line is rolled back too, and the other way round. The two can never disagree.
 */
@Service
public class AuditService {

    private final ClaimEventRepository eventRepository;
    private final CurrentActor currentActor;
    private final Clock clock;

    public AuditService(ClaimEventRepository eventRepository, CurrentActor currentActor, Clock clock) {
        this.eventRepository = eventRepository;
        this.currentActor = currentActor;
        this.clock = clock;
    }

    @Transactional
    public ClaimEvent record(Long claimId, ClaimEventType type, ClaimStatus from, ClaimStatus to, String details) {
        return recordAs(currentActor.name(), claimId, type, from, to, details);
    }

    /** For actions the app takes on its own (for example, automatic checks), not a person. */
    @Transactional
    public ClaimEvent recordAs(String actor, Long claimId, ClaimEventType type,
                               ClaimStatus from, ClaimStatus to, String details) {
        String trimmed = details == null || details.isBlank() ? null : details.trim();
        return eventRepository.save(new ClaimEvent(claimId, type, from, to, actor, trimmed, LocalDateTime.now(clock)));
    }

    @Transactional(readOnly = true)
    public List<ClaimEvent> historyFor(Long claimId) {
        return eventRepository.findAllByClaimIdOrderByOccurredAtAscIdAsc(claimId);
    }
}
