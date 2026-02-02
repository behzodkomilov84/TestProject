package behzoddev.testproject.controller.api;

import behzoddev.testproject.dao.TestSessionRepository;
import behzoddev.testproject.dto.*;
import behzoddev.testproject.entity.TestSession;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final TestSessionRepository testSessionRepository;
    private final ProfileService profileService;

    // 1️⃣ Профиль
    @GetMapping
    public ProfileDto getProfile(@AuthenticationPrincipal User user) {
        return new ProfileDto(
                user.getId(),
                user.getUsername(),
                user.getRole().getRoleName()
        );
    }

    // 2️⃣ Статистика пользователя
    @GetMapping("/stats")
    public TestStatsDto getStats(@AuthenticationPrincipal User user) {

        List<TestSession> sessions = testSessionRepository.findByUserId(user.getId());

        int totalTests = sessions.size();
        int avgPercent = sessions.stream().mapToInt(TestSession::getPercent).sum();
        avgPercent = totalTests > 0 ? avgPercent / totalTests : 0;

        int best = sessions.stream().mapToInt(TestSession::getPercent).max().orElse(0);
        int worst = sessions.stream().mapToInt(TestSession::getPercent).min().orElse(0);
        long totalDuration = sessions.stream().mapToLong(TestSession::getDurationSec).sum();

        return new TestStatsDto(totalTests, avgPercent, best, worst, totalDuration);
    }

    // 3️⃣ История тестов
    @GetMapping("/history")
    public List<TestHistoryDto> getHistory(@AuthenticationPrincipal User user) {
        List<TestSession> sessions = testSessionRepository.findByUserId(user.getId());

        return sessions.stream().map(s -> new TestHistoryDto(
                s.getId(),
                s.getStartedAt(),
                s.getFinishedAt(),
                s.getTotalQuestions(),
                s.getCorrectAnswers(),
                s.getWrongAnswers(),
                s.getPercent(),
                s.getDurationSec()
        )).toList();
    }

    // 4️⃣ Детальный просмотр теста (DTO!)
    @GetMapping("/history/{testSessionId}")
    public TestHistoryDto getOneTest(
            @AuthenticationPrincipal User user,
            @PathVariable Long testSessionId) {

        TestSession s = testSessionRepository.findById(testSessionId)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!s.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        return new TestHistoryDto(
                s.getId(),
                s.getStartedAt(),
                s.getFinishedAt(),
                s.getTotalQuestions(),
                s.getCorrectAnswers(),
                s.getWrongAnswers(),
                s.getPercent(),
                s.getDurationSec()
        );
    }

    @PatchMapping("/username")
    public ResponseEntity<Void> changeUsername(
            @RequestBody ChangeUsernameDto changeUsernameDto,
            @AuthenticationPrincipal User user
    ) {
        profileService.changeUsername(user, changeUsernameDto);
        return ResponseEntity.ok().header("X-LOGOUT", "true").build();
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(
            @RequestBody ChangePasswordDto changePasswordDto,
            @AuthenticationPrincipal User user
    ) {
        profileService.changePassword(user, changePasswordDto);
        return ResponseEntity.ok().header("X-LOGOUT", "true").build();
    }
}
