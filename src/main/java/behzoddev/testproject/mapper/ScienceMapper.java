package behzoddev.testproject.mapper;

import behzoddev.testproject.dto.*;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Science;
import behzoddev.testproject.entity.Topic;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Set;

@Mapper(componentModel = "spring")
public interface ScienceMapper {

//    @Mapping(target = "topics", ignore = true)
//    default Science map(ScienceDto scienceDto) {
//        Science science = mapScienceDtoToScience(scienceDto);
//
//        for (TopicDto topicDto : scienceDto.topics()) {
//            Topic topic = mapTopicDtoToTopic(topicDto);
//            topic.setScience(science);
//        }
//        return science;
//    }

    Science mapScienceDtoToScience(ScienceDto scienceDto);

    Topic mapTopicDtoToTopic(TopicDto topicDto);

    ScienceDto mapSciencetoScienceDto(Science science);

    Set<ScienceDto> toScinceDtoSet(Set<Science> sciences);

    Set<QuestionDto> toQuestionDtoSet(Set<Question> questions);

}
