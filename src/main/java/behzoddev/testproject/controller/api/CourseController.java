package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.course.CourseChapterDto;
import behzoddev.testproject.dto.course.CourseDetailDto;
import behzoddev.testproject.dto.course.CourseDto;
import behzoddev.testproject.dto.course.CourseSaveDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.CourseService;
import behzoddev.testproject.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

// Kurs katalogi barcha login qilgan foydalanuvchilarga ochiq
// (SecurityConfig'da "/api/courses" GET so'rovlari authenticated() sifatida
// ruxsat berilgan) — CRUD amallari OWNER va ADMIN uchun ochiq
// (@PreAuthorize), lekin tahrirlash/o'chirishda CourseService.checkCanManage
// qo'shimcha tekshiradi: ADMIN faqat O'ZI yaratgan kursni, OWNER esa
// barcha kurslarni boshqara oladi.
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final FileStorageService fileStorageService;

    @GetMapping
    public List<CourseDto> list(@AuthenticationPrincipal User user) {
        return courseService.listCatalog(user);
    }

    @GetMapping("/{id}")
    public CourseDetailDto detail(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return courseService.getDetail(id, user);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public CourseDto create(@RequestBody CourseSaveDto dto, @AuthenticationPrincipal User owner) {
        return courseService.createCourse(dto, owner);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public CourseDto update(@PathVariable Long id, @RequestBody CourseSaveDto dto,
                             @AuthenticationPrincipal User user) {
        return courseService.updateCourse(id, dto, user);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public void delete(@PathVariable Long id, @AuthenticationPrincipal User user) {
        courseService.deleteCourse(id, user);
    }

    // Kurs muqova rasmini yuklash — qaytgan URL CourseSaveDto.coverImageUrl'ga qo'yiladi.
    @PostMapping("/upload-cover")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public Map<String, String> uploadCover(@RequestParam("image") MultipartFile image) {
        return Map.of("url", fileStorageService.storeCourseCoverImage(image));
    }

    // Bo'lim (CourseChapter) nomini o'zgartirish — CourseChapter BITTA
    // umumiy yozuv bo'lgani uchun, shu bir chaqiruv bilan unga biriktirilgan
    // BARCHA mavzularda nom avtomatik yangilanadi (mavzularni birma-bir
    // tahrirlab chiqish shart emas).
    @PutMapping("/{courseId}/chapters/{chapterId}")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public void renameChapter(@PathVariable Long courseId, @PathVariable Long chapterId,
                               @RequestBody Map<String, String> body,
                               @AuthenticationPrincipal User user) {
        courseService.renameChapter(courseId, chapterId, body.get("name"), user);
    }

    // Kurs Bo'limlari bilan TEST BOSHQARUVIdagi Bo'lim (TopicSection)
    // orasidagi bog'lanishni qo'lda majburiy sinxronlashtirish — "🔄
    // Bo'lim-Mavzu bog'lanishini sinxronlash" tugmasi (courseDetail.js).
    @PostMapping("/{courseId}/chapters/sync-topics")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public Map<String, Integer> syncChapterTopics(@PathVariable Long courseId, @AuthenticationPrincipal User user) {
        return Map.of("updated", courseService.syncChapterTopicSections(courseId, user));
    }

    // Kursning BARCHA Bo'limlari (hozircha bo'sh bo'lganlari ham) — Bo'lim
    // tanlash select'ini to'liq to'ldirish uchun (courseDetail.js).
    @GetMapping("/{courseId}/chapters")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public List<CourseChapterDto> getChapters(@PathVariable Long courseId, @AuthenticationPrincipal User user) {
        return courseService.getChapters(courseId, user);
    }

    // Shu kursda hech qanday mavzuga biriktirilmagan BARCHA Bo'limlarni
    // bir yo'la o'chirish — "/{chapterId}" bilan chalkashmasligi uchun
    // aniq literal yo'l ("empty"), Spring bunday holatlarda literal
    // segmentni {chapterId} path-variable'dan avtomatik ustun qo'yadi.
    @DeleteMapping("/{courseId}/chapters/empty")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public Map<String, Integer> deleteEmptyChapters(@PathVariable Long courseId, @AuthenticationPrincipal User user) {
        return Map.of("deleted", courseService.deleteEmptyChapters(courseId, user));
    }

    // Faqat BO'SH (hech qanday mavzuga biriktirilmagan) Bo'limni o'chirish.
    @DeleteMapping("/{courseId}/chapters/{chapterId}")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public void deleteChapter(@PathVariable Long courseId, @PathVariable Long chapterId,
                               @AuthenticationPrincipal User user) {
        courseService.deleteChapter(courseId, chapterId, user);
    }
}
