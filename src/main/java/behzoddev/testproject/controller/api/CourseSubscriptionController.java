package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.course.CourseSubscriptionDto;
import behzoddev.testproject.dto.course.CreateCourseSubscriptionDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.CourseSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Kurs obunalari — faqat OWNER (qo'lda tasdiqlaydi, Telegram oqimi yo'q).
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_OWNER')")
public class CourseSubscriptionController {

    private final CourseSubscriptionService courseSubscriptionService;

    // Istalgan login qilgan foydalanuvchi (rolidan qat'i nazar) kursga
    // obuna bo'lishni so'rashi mumkin — method-level @PreAuthorize
    // class-level "faqat OWNER" cheklovini shu endpoint uchun bekor qiladi.
    @PostMapping("/api/courses/{courseId}/subscriptions/request")
    @PreAuthorize("isAuthenticated()")
    public void request(@PathVariable Long courseId, @AuthenticationPrincipal User user) {
        courseSubscriptionService.requestSubscription(courseId, user);
    }

    @PostMapping("/api/courses/{courseId}/subscriptions")
    public CourseSubscriptionDto subscribe(
            @PathVariable Long courseId,
            @RequestBody CreateCourseSubscriptionDto dto,
            @AuthenticationPrincipal User owner
    ) {
        return courseSubscriptionService.subscribe(courseId, dto, owner);
    }

    @GetMapping("/api/courses/{courseId}/subscriptions")
    public List<CourseSubscriptionDto> listForCourse(@PathVariable Long courseId) {
        return courseSubscriptionService.listForCourse(courseId);
    }

    @GetMapping("/api/course-subscriptions")
    public List<CourseSubscriptionDto> listAll() {
        return courseSubscriptionService.listAll();
    }

    @PostMapping("/api/course-subscriptions/{id}/cancel")
    public void cancel(@PathVariable Long id) {
        courseSubscriptionService.cancel(id);
    }
}
