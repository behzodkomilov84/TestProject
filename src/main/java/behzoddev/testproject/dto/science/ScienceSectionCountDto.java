package behzoddev.testproject.dto.science;

// Bitta Fan (Science)da nechta FAOL (o'chirilmagan) Bo'lim (TopicSection)
// borligi — /science/fields sahifasida BULK (bitta so'rov, N+1 emas)
// ko'rsatish uchun (ScienceService.getAllScienceIdAndNameDto,
// TopicSectionRepository.countByScienceIdsGrouped). dto.section.
// SectionTopicCountDto bilan bir xil g'oya, bir daraja yuqorida.
public record ScienceSectionCountDto(Long scienceId, long count) {
}
