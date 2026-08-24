package behzoddev.testproject.dto.section;

// Bitta fan (Science) ichida — qaysi Bo'limlar (TopicSection) biror KURSga
// bog'langanini bulk (N+1 emas, bitta so'rov bilan) bilish uchun
// (TopicSectionService.getSectionsByScienceId — topic-sections sahifasida
// "🔗 Kurs: ..." belgisini ko'rsatish VA shu bo'limni tahrirlashni
// bloklash: TopicSectionService.updateSectionName).
public record TopicSectionCourseTitleDto(Long sectionId, String courseTitle) {
}
