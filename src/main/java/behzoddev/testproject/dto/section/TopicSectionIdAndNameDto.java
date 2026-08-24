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
        String linkedCourseTitle,
        // Shu Bo'limda nechta mavzu borligi — topic-sections.html'da har
        // bir bo'lim qatorida ko'rsatish uchun (masalan "(5 ta mavzu)").
        long topicCount) {

    // Orqaga moslik — JPQL constructor-expression (TopicSectionRepository.
    // findByScienceIdOrderByOrderIndex) linkedCourseTitle'ni bermaydi
    // (bulk join fan-out xavfi tufayli — TopicSectionService'da alohida
    // so'rov bilan to'ldiriladi), lekin topicCount'ni beradi (count() —
    // fan-out xavfisiz, aniq bitta qatorli korrelyatsiyalangan subso'rov).
    public TopicSectionIdAndNameDto(Long id, String name, int orderIndex, long topicCount) {
        this(id, name, orderIndex, null, topicCount);
    }

    // Yana ham eskiroq — hech qanday qo'shimcha maydonsiz (agar kimdir
    // shunday chaqirsa).
    public TopicSectionIdAndNameDto(Long id, String name, int orderIndex) {
        this(id, name, orderIndex, null, 0);
    }
}
