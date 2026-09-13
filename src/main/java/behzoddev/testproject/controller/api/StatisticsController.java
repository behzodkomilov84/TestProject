package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.testsession.UserTestSessionStatsDto;
import behzoddev.testproject.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    // Faqat OWNER/ADMIN (o'qituvchi) — o'z o'quvchilarining/barcha
    // foydalanuvchilarning test natijalarini kuzatishi uchun. Qidiruv/
    // saralash frontendda (statistics.js) — shu sabab bu yerda hech
    // qanday parametr yo'q, TO'LIQ ro'yxat bir martada qaytariladi.
    @GetMapping("/user-sessions")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public List<UserTestSessionStatsDto> getUserTestSessionStats() {
        return statisticsService.getUserTestSessionStats();
    }
}
