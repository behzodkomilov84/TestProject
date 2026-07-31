package behzoddev.testproject.controller.api;

import behzoddev.testproject.dao.TestSessionRepository;
import behzoddev.testproject.dto.*;
import behzoddev.testproject.dto.profile.ChangeEmailDto;
import behzoddev.testproject.dto.profile.ChangePasswordDto;
import behzoddev.testproject.dto.profile.ChangeUsernameDto;
import behzoddev.testproject.dto.profile.ProfileDto;
import behzoddev.testproject.dto.profile.TestHistoryDto;
import behzoddev.testproject.dto.testsession.TestStatsDto;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.TestSession;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.ProfileService;
import behzoddev.testproject.service.TestSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final TestSessionRepository testSessionRepository;
    private final ProfileService profileService;
    private final TestSessionService testSessionService;

    // 1️⃣ Профиль
    @GetMapping
    public ProfileDto getProfile(@AuthenticationPrincipal User user) {
        return new ProfileDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRoles().stream()
                        .map(Role::getRoleName)
                        .sorted()
                        .toList()
        );
    }

    // 2️⃣ Статистика пользователя
    @GetMapping("/stats")
    public TestStatsDto getStats(@AuthenticationPrincipal User user) {

        return testSessionService.getStats(user);
    }

    // 3️⃣ История тестов
    @GetMapping("/history")
    public PageResponseDto<TestHistoryDto> getHistory(@AuthenticationPrincipal User user,
                                                      Pageable pageable) {

        return profileService.getHistory(user, pageable);

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

    // Email o'zgartirish username/parol kabi autentifikatsiya identifikatoriga
    // ta'sir qilmaydi, shuning uchun qayta login talab qilinmaydi (X-LOGOUT yo'q).
    @PatchMapping("/email")
    public ResponseEntity<Void> changeEmail(
            @RequestBody ChangeEmailDto changeEmailDto,
            @AuthenticationPrincipal User user
    ) {
        profileService.changeEmail(user, changeEmailDto);
        return ResponseEntity.ok().build();
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
