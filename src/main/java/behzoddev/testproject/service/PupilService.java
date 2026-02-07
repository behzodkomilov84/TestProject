package behzoddev.testproject.service;

import behzoddev.testproject.dao.AssignmentRepository;
import behzoddev.testproject.dao.AttemptRepository;
import behzoddev.testproject.dao.GroupInviteRepository;
import behzoddev.testproject.entity.Attempt;
import behzoddev.testproject.entity.GroupInvite;
import behzoddev.testproject.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PupilService {

    private final GroupInviteRepository groupInviteRepository;
    private final AssignmentRepository assignmentRepository;
    private final AttemptRepository attemptRepository;

    @Transactional
    public void acceptInvite(Long inviteId, User pupil) {

        GroupInvite invite = groupInviteRepository.findById(inviteId).orElseThrow();

        if (!invite.getPupil().getId().equals(pupil.getId()))
            throw new AccessDeniedException("");

        invite.setAccepted(true);

        invite.getGroup().getPupils().add(pupil);
    }

    //Сохранение результата ученика
    @Transactional
    public void saveAttempt(
            User pupil,
            Long assignmentId,
            int correct,
            int total,
            int duration
    ) {

        Attempt attempt = Attempt.builder()
                .assignment(assignmentRepository.getReferenceById(assignmentId))
                .pupil(pupil)
                .correctAnswers(correct)
                .totalQuestions(total)
                .percent(correct * 100 / total)
                .durationSec(duration)
                .build();

        attemptRepository.save(attempt);
    }

}
