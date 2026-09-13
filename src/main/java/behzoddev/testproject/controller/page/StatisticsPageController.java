package behzoddev.testproject.controller.page;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// "📊 Statistika" -> "👤 Foydalanuvchilar kesimida test statistikasi"
// (foydalanuvchi so'rovi, 2026-09-13).
@Controller
public class StatisticsPageController {

    @GetMapping("/statistics/user-sessions")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public String openUserSessionStatisticsPage() {
        return "userSessionStatistics"; // userSessionStatistics.html
    }
}
