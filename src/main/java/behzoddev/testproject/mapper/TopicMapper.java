package behzoddev.testproject.mapper;

import behzoddev.testproject.dto.TopicNameDto;
import behzoddev.testproject.entity.Topic;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TopicMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "questions", ignore = true)
    @Mapping(target = "science", ignore = true)
    Topic mapTopicNameDtoToTopic(TopicNameDto topicNameDto);


}
