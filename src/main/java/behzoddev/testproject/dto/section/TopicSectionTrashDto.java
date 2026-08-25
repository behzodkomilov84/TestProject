package behzoddev.testproject.dto.section;

import java.time.LocalDateTime;

// "O'chirilganlar savati" (Fan ichida) — soft-delete qilingan bitta
// Bo'lim (TopicSectionService.getDeletedSections).
public record TopicSectionTrashDto(Long id, String name, LocalDateTime deletedAt) {
}
