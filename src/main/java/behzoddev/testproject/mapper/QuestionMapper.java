package behzoddev.testproject.mapper;

import behzoddev.testproject.dto.AnswerDto;
import behzoddev.testproject.dto.QuestionDto;
import behzoddev.testproject.dto.QuestionShortDto;
import behzoddev.testproject.entity.Answer;
import behzoddev.testproject.entity.Question;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface QuestionMapper {


    @Mapping(target = "answers", source = "answers")
    QuestionDto mapQuestiontoQuestionDto(Question question);

    List<QuestionDto> mapQuestionListToQuestionDtoList(List<Question> questions);

    @Mapping(target = "topic", ignore = true)
    Question mapQuestionDtoToQuestion(QuestionDto questionDto);

    List<QuestionShortDto> mapQuestionListToQuestionShortDtoList(List<Question> questions);

    @Mapping(target = "topic", ignore = true)
    Question mapQuestionShortDtoToQuestion(QuestionShortDto newQuestion);
}
