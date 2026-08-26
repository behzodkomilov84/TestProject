package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.course.TopicExplanationSearchResultDto;
import behzoddev.testproject.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// "Kurs ichidan mavzu yoritmasi bo'yicha qidiruv" — topics.html
// (TEST BOSHQARUVI) va courseDetail.html (kurs sahifasi) uchun UMUMIY
// endpoint (CourseService.searchTopicExplanations). Alohida controller
// sifatida chiqarilgan — CourseSectionController'ning bazaviy yo'li
// "/api/courses/{courseId}/sections" bitta aniq kursga bog'liq, bu
// endpoint esa BIR NECHTA (frontend hali bilmaydigan) kursni qamrab olishi
// mumkin.
@RestController
@RequestMapping("/api/course-sections")
@RequiredArgsConstructor
public class TopicExplanationSearchController {

    private final CourseService courseService;

    @GetMapping("/search-explanations")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public List<TopicExplanationSearchResultDto> search(
            @RequestParam List<Long> topicIds,
            @RequestParam String q
    ) {
        return courseService.searchTopicExplanations(topicIds, q);
    }
}
