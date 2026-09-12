package behzoddev.testproject.dto.topic;

import java.time.LocalDateTime;

// "O'chirilganlar savati" (TEST BOSHQARUVI, Fan ichida) — soft-delete
// qilingan bitta mavzu (TopicService.getDeletedTopics). "sectionId"/
// "sectionName" — foydalanuvchi so'rovi, 2026-09-12: "TEST BOSHQARUVI dagi
// 'O'chirilgan darslar' panelini ham kursga bog'lanmagan darslar kabi
// qil" — o'chirilgandan keyin ham Topic'ning "section" bog'lanishi
// TEGILMAY qoladi (faqat deletedAt o'rnatiladi), shu sabab har bir dars
// ASL Bo'limi bo'yicha guruhlanadi (sectionId == null — "— Bo'limsiz —").
public record TopicTrashDto(Long id, String name, LocalDateTime deletedAt, long questionCount, Long sectionId, String sectionName) {
}
