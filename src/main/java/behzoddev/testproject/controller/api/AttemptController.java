package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.student.AttemptStartResponseDto;
import behzoddev.testproject.dto.student.SyncAttemptRequestDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.AssignmentAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student/attempt")
@RequiredArgsConstructor
public class AttemptController {

    private final AssignmentAttemptService assignmentAttemptService;

    @PostMapping("/start/{assignmentId}")
    public ResponseEntity<AttemptStartResponseDto> startAttempt(

            @PathVariable Long assignmentId,
            @AuthenticationPrincipal User pupil
    ) {

        return ResponseEntity.ok(
                assignmentAttemptService.startAttempt(assignmentId, pupil));
    }

    @PostMapping("/sync")
    public ResponseEntity<Void> syncAttempt(
            @AuthenticationPrincipal User pupil,
            @RequestBody SyncAttemptRequestDto request
    ) {
        assignmentAttemptService.syncAttempt(pupil, request);
        return ResponseEntity.ok().build();
    }
}
