package behzoddev.testproject.mapper;

import behzoddev.testproject.dto.question.QuestionDto;
import behzoddev.testproject.dto.science.ScienceDto;
import behzoddev.testproject.dto.science.ScienceNameDto;
import behzoddev.testproject.dto.batch.ScienceCreateDto;
import behzoddev.testproject.dto.batch.ScienceUpdateDto;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Science;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Set;

@Mapper(componentModel = "spring",
        uses = {TopicMapper.class, QuestionMapper.class, AnswerMapper.class})
public interface ScienceMapper {

    ScienceDto mapSciencetoScienceDto(Science science);

    Set<ScienceDto> toScinceDtoSet(Set<Science> sciences);

    Set<QuestionDto> toQuestionDtoSet(Set<Question> questions);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "topics", ignore = true)
    Science mapScienceNameDtoToScience(ScienceNameDto scienceNameDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "topics", ignore = true)
    Science mapScienceCreateDtoToScience(ScienceCreateDto scienceCreateDto);

    @Mapping(target = "topics", ignore = true)
    Science mapScienceUpdateDtoToScience(ScienceUpdateDto scienceUpdateDto);

}
