package behzoddev.testproject.mapper;

import behzoddev.testproject.dto.QuestionDto;
import behzoddev.testproject.dto.ScienceDto;
import behzoddev.testproject.dto.ScienceNameDto;
import behzoddev.testproject.dto.TopicDto;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Science;
import behzoddev.testproject.entity.Topic;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Set;

@Mapper(componentModel = "spring",
        uses = {TopicMapper.class, QuestionMapper.class, AnswerMapper.class})
public interface ScienceMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "topics", ignore = true)
    Science mapScienceDtoToScience(ScienceDto scienceDto);

    @Mapping(target = "science", ignore = true)
    @Mapping(target = "id", ignore = true)
    Topic mapTopicDtoToTopic(TopicDto topicDto);

    ScienceDto mapSciencetoScienceDto(Science science);

    Set<ScienceDto> toScinceDtoSet(Set<Science> sciences);

    Set<QuestionDto> toQuestionDtoSet(Set<Question> questions);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "topics", ignore = true)
    Science mapScienceNameDtoToScience(ScienceNameDto scienceNameDto);


}
