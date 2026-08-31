package behzoddev.testproject.dto.course;

import java.util.List;

// Bitta mavzuning barcha savollari bo'yicha "izohdagi mavzu havolasi"
// tekshiruvi natijasi — CourseService.auditTopicLinks ("🔗 Havolalarni
// tekshirish"). missingCount>0 bo'lsa — courseDetail.js "➕ Havola
// qo'shish" tugmasini ko'rsatadi; wrongItems bo'sh bo'lmasa — har biriga
// "✅ To'g'irlash" tugmasi bilan.
public record TopicLinkAuditDto(
        Long topicId,
        String topicName,
        String expectedHref,
        int okCount,
        int missingCount,
        List<TopicLinkAuditItemDto> wrongItems
) {
}
