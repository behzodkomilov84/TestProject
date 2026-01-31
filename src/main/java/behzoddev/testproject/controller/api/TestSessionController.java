package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.*;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.TestSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/test-session")
@RequiredArgsConstructor
public class TestSessionController {

    private final TestSessionService testSessionService;

    @PostMapping("/start")
    public StartTestResponseDto start(@RequestBody StartTestDto request,
                                      @AuthenticationPrincipal User user) {
        return testSessionService.startTest(user, request.topicIds(), request.limit());
    }

    // завершение теста
    @PostMapping("/finish")
    public ResponseEntity<Void> finish(
            @RequestBody FinishTestRequestDto request,
            @AuthenticationPrincipal User user
    ) {
        testSessionService.finishTest(request, user);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/history")
    public Page<TestSessionHistoryDto> history(
            @AuthenticationPrincipal User user,
            Pageable pageable
    ) {
        return testSessionService.getHistory(user, pageable);
    }

    // детали
    @GetMapping("/{testSessionId}")
    public List<TestSessionDetailDto> details(
            @PathVariable Long testSessionId,
            @AuthenticationPrincipal User user
    ) {
        return testSessionService.getDetails(testSessionId, user);
    }
}