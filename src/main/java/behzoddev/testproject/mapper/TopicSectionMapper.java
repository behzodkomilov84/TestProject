package behzoddev.testproject.mapper;

import behzoddev.testproject.dto.section.TopicSectionNameDto;
import behzoddev.testproject.entity.TopicSection;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TopicSectionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "topics", ignore = true)
    @Mapping(target = "science", ignore = true)
    @Mapping(target = "orderIndex", ignore = true)
    TopicSection mapNameDtoToTopicSection(TopicSectionNameDto dto);
}
