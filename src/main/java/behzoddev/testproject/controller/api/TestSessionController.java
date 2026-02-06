package behzoddev.testproject.controller.api;

import behzoddev.testproject.dao.TestSessionRepository;
import behzoddev.testproject.dto.*;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.mapper.TestSessionMapper;
import behzoddev.testproject.service.TestSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test-session")
@RequiredArgsConstructor
public class TestSessionController {

    private final TestSessionService testSessionService;
    private final TestSessionRepository testSessionRepository;
    private final TestSessionMapper testSessionMapper;

    @PostMapping("/start")
    public StartTestResponseDto start(@RequestBody StartTestDto request,
                                      @AuthenticationPrincipal User user) {
        return testSessionService.startTest(user, request.topicIds(), request.limit(), request.mode());
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
    public PageResponseDto<TestSessionHistoryDto> getHistory(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "7") int size
    ) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("finishedAt").descending());

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

    @PostMapping("/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> cancelTest(@RequestBody Map<String, Long> payload,
                                           @AuthenticationPrincipal User user) {

        testSessionService.cancelTest(payload, user);
        return ResponseEntity.ok().build();

    }

}