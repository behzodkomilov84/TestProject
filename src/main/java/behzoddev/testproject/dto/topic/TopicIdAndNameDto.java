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
        String linkedCourseTitle) {

    // Orqaga moslik — TopicRepository'dagi JPQL constructor-expression'lar
    // (findTopicsByScienceId/findTopicByIds) linkedCourseTitle'ni bermaydi
    // (bulk join fan-out xavfi tufayli — bir mavzu nazariy jihatdan bir
    // nechta kurs bo'limiga bog'lanishi mumkin, JOIN qatorlarni ko'paytirib
    // yuborardi). Shu maydon TopicService.getTopicsByScienceId'da alohida
    // (bitta bulk) so'rov bilan to'ldiriladi.
    public TopicIdAndNameDto(Long id, String name, Long sectionId) {
        this(id, name, sectionId, null);
    }
}
