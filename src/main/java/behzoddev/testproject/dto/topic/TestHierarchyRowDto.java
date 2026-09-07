package behzoddev.testproject.dto.topic;

// testConfigPage.html uchun to'liq TO'RT DARAJALI ierarxiya — Yo'nalish
// (CourseField) -> Bo'lim (Science) -> Mavzu (TopicSection) -> Dars
// (Topic) — bitta so'rovda TEKIS (flat) qatorlar sifatida qaytadi,
// frontend (testConfigPage.js) shu qatorlardan daraxt qurib chiqadi
// (foydalanuvchi so'rovi, 2026-09-07: "танловлар йўналиш, бўлим, мавзу,
// дарс иерарҳиясида бўлсин ... барчасида танлов checkbox ли бўлсин").
// fieldId/sectionId NULL bo'lishi mumkin (Yo'nalishga yoki Mavzuga
// biriktirilmagan bo'lsa) — frontend bunday qatorlarni "Yo'nalishsiz"/
// "Mavzusiz" alohida guruhga joylaydi (topics.html'dagi kabi).
public record TestHierarchyRowDto(
        Long fieldId,
        String fieldName,
        Long scienceId,
        String scienceName,
        Long sectionId,
        String sectionName,
        Long topicId,
        String topicName,
        long questionCount
) {
}
