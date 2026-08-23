package behzoddev.testproject.dto.topic;

// sectionId/sectionName/sectionOrderIndex — ixtiyoriy (NULL agar mavzu
// hech qanday Bo'limga biriktirilmagan bo'lsa). testConfigPage.js shu
// maydonlarga qarab mavzularni Bo'lim bo'yicha guruhlab, sarlavha bilan
// ko'rsatadi; bo'limi yo'q fanlar uchun hammasi NULL bo'lib qoladi va
// ko'rinish avvalgidek tekis ro'yxat bo'lib qolaveradi.
public record TopicWithQuestionCountDto(
        Long id,
        String name,
        Long questionCount,
        Long sectionId,
        String sectionName,
        Integer sectionOrderIndex) {
}
