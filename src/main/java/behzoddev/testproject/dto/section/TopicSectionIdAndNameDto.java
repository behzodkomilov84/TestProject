package behzoddev.testproject.dto.section;

public record TopicSectionIdAndNameDto(
        Long id,
        String name,
        int orderIndex,
        // Ixtiyoriy — shu Bo'lim biror KURSga bog'langan bo'lsa, o'sha
        // kursning nomi (topic-sections sahifasida "🔗 Kurs: ..." belgisini
        // ko'rsatish VA bo'limni shu yerdan tahrirlashni bloklash uchun —
        // TopicSectionService.getSectionsByScienceId/updateSectionName).
        // NULL — hech qanday kursga bog'lanmagan (erkin tahrirlanadi).
        String linkedCourseTitle) {

    // Orqaga moslik — TopicSectionRepository'dagi JPQL constructor-
    // expression (findByScienceIdOrderByOrderIndex) linkedCourseTitle'ni
    // bermaydi (bulk join fan-out xavfi tufayli) — bu maydon
    // TopicSectionService'da alohida (bitta bulk) so'rov bilan to'ldiriladi.
    public TopicSectionIdAndNameDto(Long id, String name, int orderIndex) {
        this(id, name, orderIndex, null);
    }
}
