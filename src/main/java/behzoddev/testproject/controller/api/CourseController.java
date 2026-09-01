package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.course.CourseChapterDto;
import behzoddev.testproject.dto.course.CourseDetailDto;
import behzoddev.testproject.dto.course.CourseDto;
import behzoddev.testproject.dto.course.CourseSaveDto;
import behzoddev.testproject.dto.course.TopicLinkAuditDto;
import behzoddev.testproject.dto.export.ExportedFileDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.CourseService;
import behzoddev.testproject.service.CourseWordExportService;
import behzoddev.testproject.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
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
    private final CourseWordExportService courseWordExportService;

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

    // Endi haqiqiy "o'chirish" emas — "O'chirilganlar savati"ga o'tkazish
    // (soft-delete). Bo'limlar/mavzular/obunalar TEGILMAY qoladi, keyinroq
    // qaytadan tiklash mumkin (restore).
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public void delete(@PathVariable Long id, @AuthenticationPrincipal User user) {
        courseService.deleteCourse(id, user);
    }

    // "O'chirilganlar savati" — soft-delete qilingan kurslar ro'yxati
    // ("/{id}" bilan chalkashmasligi uchun aniq literal yo'l "/deleted").
    @GetMapping("/deleted")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public List<CourseDto> deleted(@AuthenticationPrincipal User user) {
        return courseService.getDeletedCourses(user);
    }

    // "♻️ Tiklash" — kursni "O'chirilganlar savati"dan qaytaradi.
    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public void restore(@PathVariable Long id, @AuthenticationPrincipal User user) {
        courseService.restoreCourse(id, user);
    }

    // "🗑️ Butunlay o'chirish" — QAYTARIB BO'LMAYDI, faqat allaqachon
    // savatdagi kursga nisbatan (CourseService.permanentlyDeleteCourse).
    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public void permanentDelete(@PathVariable Long id, @AuthenticationPrincipal User user) {
        courseService.permanentlyDeleteCourse(id, user);
    }

    // Kurs muqova rasmini yuklash — qaytgan URL CourseSaveDto.coverImageUrl'ga qo'yiladi.
    @PostMapping("/upload-cover")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public Map<String, String> uploadCover(@RequestParam("image") MultipartFile image) {
        return Map.of("url", fileStorageService.storeCourseCoverImage(image));
    }

    // "📝 Kursni Word'ga eksport qilish" (courseDetail.js) — butun kursni
    // BITTA .docx faylga: kurs mavzulari (matn/video), ularga bog'langan
    // testlar va (eng oxirida, alohida bo'lim) javoblar — har biri
    // mustaqil checkbox orqali yoqilib/o'chirilib tanlanadi (default —
    // barchasi yoqilgan).
    @GetMapping("/{courseId}/export/word")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public ResponseEntity<byte[]> exportCourseToWord(
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "true") boolean includeContent,
            @RequestParam(defaultValue = "true") boolean includeTests,
            @RequestParam(defaultValue = "true") boolean includeAnswers,
            @AuthenticationPrincipal User user
    ) {
        ExportedFileDto file = courseWordExportService.exportCourse(courseId, includeContent, includeTests, includeAnswers, user);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.filenameBase() + ".docx", StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(file.data());
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

    // "⬆⬇" — Bo'lim "box"larini kurs sahifasida yuqoriga/pastga surish
    // (courseDetail.js). "reorder" literal segment — Spring bunday holatda
    // {chapterId} path-variable'dan ustun qo'yadi ("sync-topics"/"empty"
    // bilan bir xil andoza).
    @PutMapping("/{courseId}/chapters/reorder")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public void reorderChapters(@PathVariable Long courseId, @RequestBody List<Long> chapterIds,
                                 @AuthenticationPrincipal User user) {
        courseService.reorderChapters(courseId, chapterIds, user);
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

    // "🗑️ Bo'lim + mavzularni birga o'chirish" — deleteChapter'dan farqli,
    // BO'SH bo'lishi shart emas: Bo'limdagi barcha kurs mavzularini
    // (va bog'langan bo'lsa, TEST BOSHQARUVIdagi mos mavzu+savollarni ham)
    // birga o'chiradi. "/with-topics" literal segment — Spring bunday
    // holatda {chapterId} path-variable'dan ustun qo'yadi, chalkashmaydi.
    @DeleteMapping("/{courseId}/chapters/{chapterId}/with-topics")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public void deleteChapterWithLinkedTopics(@PathVariable Long courseId, @PathVariable Long chapterId,
                                               @AuthenticationPrincipal User user) {
        courseService.deleteChapterWithLinkedTopics(courseId, chapterId, user);
    }

    // "🔗 Havolalarni tekshirish" — shu kursga bog'langan har bir mavzuning
    // savollari to'g'ri javob izohida O'ZINING mavzusiga havola bor-yo'qligini,
    // bor bo'lsa TO'G'RI ekanini tekshiradi (courseDetail.js).
    @GetMapping("/{courseId}/topic-links/audit")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public List<TopicLinkAuditDto> auditTopicLinks(@PathVariable Long courseId) {
        return courseService.auditTopicLinks(courseId);
    }

    // "➕ Havola qo'shish" — shu mavzudagi izohida HECH QANDAY mavzu
    // havolasi yo'q savollarga to'g'ri havolani bittada qo'shadi.
    @PostMapping("/{courseId}/topic-links/add-missing")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public Map<String, Integer> addMissingTopicLinks(@PathVariable Long courseId, @RequestParam Long topicId) {
        return Map.of("added", courseService.addMissingTopicLinks(courseId, topicId));
    }

    // "✅ To'g'irlash" — bitta savolning izohidagi noto'g'ri mavzu havolasini
    // o'zining mavzusiga to'g'ri havola bilan almashtiradi.
    @PostMapping("/{courseId}/topic-links/fix-wrong")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public void fixWrongTopicLink(@PathVariable Long courseId, @RequestParam Long questionId) {
        courseService.fixWrongTopicLink(courseId, questionId);
    }

    // "✅ Barchasini to'g'irlash" — topicId berilsa FAQAT shu mavzudagi,
    // berilmasa BUTUN kursdagi barcha xato havolali savollarni bittada
    // to'g'irlaydi.
    @PostMapping("/{courseId}/topic-links/fix-all-wrong")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public Map<String, Integer> fixAllWrongTopicLinks(@PathVariable Long courseId,
                                                        @RequestParam(required = false) Long topicId) {
        int fixed = topicId != null
                ? courseService.fixAllWrongTopicLinks(courseId, topicId)
                : courseService.fixAllWrongTopicLinksInCourse(courseId);
        return Map.of("fixed", fixed);
    }

    // "🧹 Takroriy havolalarni tozalash" — bir savolda bir nechta mavzu
    // havolasi belgisi qolib ketgan bo'lsa (masalan eski to'liq-URL
    // formatidagi bug tufayli), hammasini bittaga tushiradi.
    @PostMapping("/{courseId}/topic-links/dedupe")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public Map<String, Integer> dedupeTopicLinks(@PathVariable Long courseId) {
        return Map.of("deduped", courseService.dedupeTopicLinksInCourse(courseId));
    }
}
