package behzoddev.testproject.dto.section;

// Bitta Bo'lim (TopicSection)da nechta FAOL (o'chirilmagan) mavzu
// (Topic) borligi — topic-sections.html'da BULK (bitta so'rov, N+1 emas)
// ko'rsatish uchun (TopicSectionService.getSectionsByScienceId/
// deleteEmptySections, TopicRepository.countBySectionIdsGrouped).
// dto.question.TopicQuestionCountDto bilan bir xil g'oya, faqat
// Bo'lim->Mavzu darajasida.
public record SectionTopicCountDto(Long sectionId, long count) {
}
