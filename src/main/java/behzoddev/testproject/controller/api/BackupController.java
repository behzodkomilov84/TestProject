package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.backup.BackupCourseCandidateDto;
import behzoddev.testproject.dto.backup.BackupFileDto;
import behzoddev.testproject.dto.backup.BackupRestoreRequestDto;
import behzoddev.testproject.dto.backup.BackupRestoreResultDto;
import behzoddev.testproject.service.BackupRestoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

// "📦 Backup orqali tiklash" — "O'chirilganlar savati" (CourseController's
// /deleted, /restore, /permanent) YETARLI bo'lmagan holatlar uchun: kurs
// BUTUNLAY o'chirilgan yoki bu funksiyalar yaratilishidan OLDIN yo'qolgan
// bo'lsa. FAQAT ROLE_OWNER — jonli bazaga to'g'ridan-to'g'ri yozadigan,
// nihoyatda sezgir amal (ADMIN'ga ham ruxsat berilmagan, trash bin'dan farqli).
@RestController
@RequestMapping("/api/backups")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_OWNER')")
public class BackupController {

    private final BackupRestoreService backupRestoreService;

    @GetMapping
    public List<BackupFileDto> list() {
        return backupRestoreService.listBackups();
    }

    @GetMapping("/{fileName}/preview")
    public List<BackupCourseCandidateDto> preview(
            @PathVariable String fileName,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return backupRestoreService.previewCourses(fileName, from, to);
    }

    @PostMapping("/{fileName}/restore")
    public BackupRestoreResultDto restore(@PathVariable String fileName, @RequestBody BackupRestoreRequestDto body) {
        return backupRestoreService.applyRestore(fileName, body.from(), body.to(), body.courseIds());
    }
}
