package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.student.AttemptDto;
import behzoddev.testproject.dto.student.AttemptFullDto;
import behzoddev.testproject.dto.student.SyncAttemptRequestDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.AssignmentAttemptService;
import behzoddev.testproject.service.AttemptHeartbeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student/attempt")
@RequiredArgsConstructor
public class AssignmentAttemptController {

    private final AssignmentAttemptService assignmentAttemptService;
    private final AttemptHeartbeatService attemptHeartbeatService;

    @GetMapping("/getattempt/{taskId}")
    public ResponseEntity<AttemptDto> getAttemptByTaskId(
            @PathVariable Long taskId,
            @AuthenticationPrincipal User pupil) {
        return ResponseEntity.ok(assignmentAttemptService.getFullAttemptByTaskId(taskId, pupil));
    }

    @PostMapping("/start/{assignmentId}")
    public ResponseEntity<AttemptDto> startAttempt(

            @PathVariable Long assignmentId,
            @AuthenticationPrincipal User pupil
    ) {

        return ResponseEntity.ok(
                assignmentAttemptService.startAttempt(assignmentId, pupil));
    }

    @PostMapping("/heartbeat/{id}")
    public ResponseEntity<Void> heartbeat(
            @AuthenticationPrincipal User pupil,
            @PathVariable Long id
    ) {
        attemptHeartbeatService.heartbeat(pupil, id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/sync")
    public ResponseEntity<Void> syncAttempt(
            @AuthenticationPrincipal User pupil,
            @RequestBody SyncAttemptRequestDto request
    ) {
        assignmentAttemptService.syncAttempt(pupil, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/finish")
    public ResponseEntity<Void> finishAttempt(
            @AuthenticationPrincipal User pupil,
            @PathVariable Long id
    ) {

        assignmentAttemptService.finishTaskSession(pupil, id);

        return ResponseEntity.ok().build();
    }

   @GetMapping("/get-full-attempt/{taskId}")
    public ResponseEntity<AttemptFullDto> getAttempt(
            @PathVariable Long taskId,
            @AuthenticationPrincipal User pupil
    ) {
        return ResponseEntity.ok(
                assignmentAttemptService.getFullAttemptForResult(taskId, pupil)
        );
    }
}
