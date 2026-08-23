package behzoddev.testproject.dto.topic;

import jakarta.validation.constraints.NotBlank;

// sectionId — ixtiyoriy, mavzu qaysi Bo'limga biriktirilganini bildiradi
// (NULL — hali bo'limsiz). Admin topic-boshqaruv UI'i (topics.html)ga
// kerak.
public record TopicIdAndNameDto(
        Long id,
        @NotBlank(message = "❌Topic.name bo'sh bo'lishi mumkin emas.") String name,
        Long sectionId) {
}
