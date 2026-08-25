package behzoddev.testproject.dto.course;

import java.time.LocalDateTime;

// "O'chirilganlar savati" (kurs ichida) — soft-delete qilingan bitta kurs
// mavzusi/darsi (CourseService.getDeletedSections).
public record CourseSectionTrashDto(Long id, String title, LocalDateTime deletedAt) {
}
