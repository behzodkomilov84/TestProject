package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseRepository;
import behzoddev.testproject.dao.UserActivityTotalRepository;
import behzoddev.testproject.dao.UserCourseActivityTotalRepository;
import behzoddev.testproject.dto.user.CourseActivityDto;
import behzoddev.testproject.dto.user.UserActivitySummaryDto;
import behzoddev.testproject.entity.Course;
import behzoddev.testproject.entity.UserActivityTotal;
import behzoddev.testproject.entity.UserCourseActivityTotal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

// "/users" sahifasidagi "Oxirgi tashrif vaqti" katakchasi bosilganda —
// "Saytda jami necha soat" + kurslar kesimidagi taqsimot (foydalanuvchi
// so'rovi, 2026-09-10). Haqiqiy to'plash UserActivityTracker'da — bu
// servis faqat O'QISH (DB'dan olib, kurs nomlari bilan birlashtirish)
// uchun.
@Service
@RequiredArgsConstructor
public class UserActivityService {

    private final UserActivityTotalRepository userActivityTotalRepository;
    private final UserCourseActivityTotalRepository userCourseActivityTotalRepository;
    private final CourseRepository courseRepository;

    @Transactional(readOnly = true)
    public UserActivitySummaryDto getSummary(Long userId) {
        long totalSeconds = userActivityTotalRepository.findById(userId)
                .map(UserActivityTotal::getTotalSeconds)
                .orElse(0L);

        List<UserCourseActivityTotal> rows = userCourseActivityTotalRepository
                .findById_UserIdOrderByTotalSecondsDesc(userId);

        // Kurs nomlarini BIR SO'ROV bilan (N+1 emas) olamiz — o'chirilgan
        // kurslar ham nom sifatida ko'rinishi kerak (findAllById soft-delete
        // filtrini qo'llamaydi, shuning uchun tarix yo'qolib qolmaydi).
        List<Long> courseIds = rows.stream().map(r -> r.getId().getCourseId()).toList();
        Map<Long, String> titleByCourseId = courseIds.isEmpty()
                ? Map.of()
                : courseRepository.findAllById(courseIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Course::getId, Course::getTitle));

        List<CourseActivityDto> breakdown = rows.stream()
                .map(r -> CourseActivityDto.builder()
                        .courseId(r.getId().getCourseId())
                        .courseTitle(titleByCourseId.getOrDefault(r.getId().getCourseId(), "(o'chirilgan kurs)"))
                        .totalSeconds(r.getTotalSeconds())
                        .build())
                .toList();

        return UserActivitySummaryDto.builder()
                .totalSeconds(totalSeconds)
                .courseBreakdown(breakdown)
                .build();
    }
}
