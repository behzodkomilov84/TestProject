package behzoddev.testproject.controller.api;

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
// ruxsat berilgan) — CRUD amallari faqat OWNER uchun @PreAuthorize orqali.
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
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public CourseDto create(@RequestBody CourseSaveDto dto, @AuthenticationPrincipal User owner) {
        return courseService.createCourse(dto, owner);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public CourseDto update(@PathVariable Long id, @RequestBody CourseSaveDto dto) {
        return courseService.updateCourse(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public void delete(@PathVariable Long id) {
        courseService.deleteCourse(id);
    }

    // Kurs muqova rasmini yuklash — qaytgan URL CourseSaveDto.coverImageUrl'ga qo'yiladi.
    @PostMapping("/upload-cover")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public Map<String, String> uploadCover(@RequestParam("image") MultipartFile image) {
        return Map.of("url", fileStorageService.storeCourseCoverImage(image));
    }
}
