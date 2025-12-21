package behzoddev.testproject.mapper;

import behzoddev.testproject.dto.*;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Science;
import behzoddev.testproject.entity.Topic;
import org.mapstruct.Mapper;

import java.util.Set;

@Mapper(componentModel = "spring")
public interface ScienceMapper {

    Science mapScienceDtoToScience(ScienceDto scienceDto);

    Topic mapTopicDtoToTopic(TopicDto topicDto);

    ScienceDto mapSciencetoScienceDto(Science science);

    Set<ScienceDto> toScinceDtoSet(Set<Science> sciences);

    Set<QuestionDto> toQuestionDtoSet(Set<Question> questions);

    Science mapScienceNameDtoToScience(ScienceNameDto scienceNameDto);
}
