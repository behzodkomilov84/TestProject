package behzoddev.testproject.dto.topic;

import jakarta.validation.constraints.NotBlank;

// sectionId — ixtiyoriy, mavzu qaysi Bo'limga biriktirilganini bildiradi
// (NULL — hali bo'limsiz). Admin topic-boshqaruv UI'i (topics.html)ga
// kerak.
public record TopicIdAndNameDto(
        Long id,
        @NotBlank(message = "❌Topic.name bo'sh bo'lishi mumkin emas.") String name,
        Long sectionId,
        // Ixtiyoriy — shu mavzu biror KURS bo'limiga bog'langan bo'lsa, o'sha
        // kursning nomi (topics.html'da "🔗 Kurs: ..." belgisini ko'rsatish
        // uchun). NULL — kursga bog'lanmagan.
        String linkedCourseTitle,
        // Shu mavzuda nechta savol borligi — topics.html'da har bir mavzu
        // qatorida ko'rsatish uchun (masalan "(12 ta test)").
        long questionCount) {

    // Orqaga moslik — TopicRepository'dagi JPQL constructor-expression'lar
    // (findTopicsByScienceId/findTopicByIds) linkedCourseTitle'ni bermaydi
    // (bulk join fan-out xavfi tufayli — bir mavzu nazariy jihatdan bir
    // nechta kurs bo'limiga bog'lanishi mumkin, JOIN qatorlarni ko'paytirib
    // yuborardi), lekin questionCount'ni beradi (count() — fan-out
    // xavfisiz, aniq bitta qatorli korrelyatsiyalangan subso'rov). Shu
    // maydon (linkedCourseTitle) TopicService.getTopicsByScienceId'da
    // alohida (bitta bulk) so'rov bilan to'ldiriladi.
    public TopicIdAndNameDto(Long id, String name, Long sectionId, long questionCount) {
        this(id, name, sectionId, null, questionCount);
    }

    // Yana ham eskiroq — hech qanday qo'shimcha maydonsiz.
    public TopicIdAndNameDto(Long id, String name, Long sectionId) {
        this(id, name, sectionId, null, 0);
    }
}
