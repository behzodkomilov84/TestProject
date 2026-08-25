package behzoddev.testproject.dto.topic;

import java.time.LocalDateTime;

// "O'chirilganlar savati" (TEST BOSHQARUVI, Fan ichida) — soft-delete
// qilingan bitta mavzu (TopicService.getDeletedTopics).
public record TopicTrashDto(Long id, String name, LocalDateTime deletedAt, long questionCount) {
}
