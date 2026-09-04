package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.course.CourseFieldDto;
import behzoddev.testproject.dto.course.CourseFieldSaveDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.CourseFieldService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// "Yo'nalish" (soha) — CourseController bilan bir xil ruxsat qoidasi:
// GET (ro'yxat) barcha login qilgan foydalanuvchiga (kurslar katalogi
// hammaga ochiq), CRUD esa OWNER va ADMIN uchun (foydalanuvchi so'rovi
// bo'yicha, 2026-09-04 — "kurs yaratish huquqi bilan bir xil").
@RestController
@RequestMapping("/api/course-fields")
@RequiredArgsConstructor
public class CourseFieldController {

    private final CourseFieldService courseFieldService;

    @GetMapping
    public List<CourseFieldDto> list() {
        return courseFieldService.listFields();
    }

    @GetMapping("/deleted")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public List<CourseFieldDto> deleted() {
        return courseFieldService.listDeletedFields();
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public CourseFieldDto create(@RequestBody CourseFieldSaveDto dto, @AuthenticationPrincipal User user) {
        return courseFieldService.createField(dto, user);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public CourseFieldDto rename(@PathVariable Long id, @RequestBody CourseFieldSaveDto dto) {
        return courseFieldService.renameField(id, dto.name());
    }

    // "⬆⬇" — literal "/reorder" segmenti, Spring bunday holatda
    // "{id}" path-variable'dan ustun qo'yadi (loyihadagi bir xil andoza).
    @PutMapping("/reorder")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public void reorder(@RequestBody List<Long> fieldIds) {
        courseFieldService.reorderFields(fieldIds);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public void delete(@PathVariable Long id) {
        courseFieldService.deleteField(id);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public void restore(@PathVariable Long id) {
        courseFieldService.restoreField(id);
    }
}
