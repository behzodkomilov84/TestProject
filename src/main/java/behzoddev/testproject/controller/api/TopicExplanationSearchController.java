package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.course.TopicExplanationSearchRequestDto;
import behzoddev.testproject.dto.course.TopicExplanationSearchResultDto;
import behzoddev.testproject.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    // Tahrirlash emas, oddiy o'qish/qidiruv — shuning uchun @PreAuthorize
    // YO'Q (SecurityConfig'da "/api/course-sections/search-explanations"
    // istalgan login qilgan foydalanuvchiga — OWNER/ADMIN/USER — ochiq
    // qilib qo'yilgan).
    //
    // GET + "topicIds" query-parametrlar ro'yxati o'rniga POST + JSON body
    // (foydalanuvchi xabari, 2026-09-20: katta kurslarda — masalan 897 ta
    // bog'langan mavzu — GET URL 12000+ belgigacha o'sib, server/brauzer URL
    // uzunligi chegarasidan oshib "Failed to fetch" bilan yiqilardi).
    @PostMapping("/search-explanations")
    public List<TopicExplanationSearchResultDto> search(@RequestBody TopicExplanationSearchRequestDto request) {
        return courseService.searchTopicExplanations(request.topicIds(), request.q());
    }
}
