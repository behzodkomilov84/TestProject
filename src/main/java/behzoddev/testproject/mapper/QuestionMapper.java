package behzoddev.testproject.mapper;

import behzoddev.testproject.dto.question.QuestionDto;
import behzoddev.testproject.dto.question.QuestionShortDto;
import behzoddev.testproject.dto.student.ResponseAnswerDto;
import behzoddev.testproject.dto.student.ResponseQuestionDto;
import behzoddev.testproject.dto.teacher.ResponseQuestionTextDto;
import behzoddev.testproject.entity.Question;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring",
        uses = AnswerMapper.class)
public interface QuestionMapper {

    QuestionDto mapQuestiontoQuestionDto(Question question);

    List<QuestionDto> mapQuestionListToQuestionDtoList(List<Question> questions);

    /*@Mapping(target = "id", ignore = true)
    @Mapping(target = "topic", ignore = true)
    Question mapQuestionDtoToQuestion(QuestionDto questionDto);*/

    List<QuestionShortDto> mapQuestionListToQuestionShortDtoList(List<Question> questions);

    @Mapping(target = "topic", ignore = true)
    @Mapping(target = "id", ignore = true)
    Question mapQuestionShortDtoToQuestion(QuestionShortDto newQuestion);

    ResponseQuestionTextDto mapQuestionToResponseQuestionTextDto(Question question);

    @Mapping(source = "questionText", target = "text")
    ResponseQuestionDto mapQuestionToResponseQuestionDto(Question question);

    List<ResponseQuestionDto> mapQuestionListToResponseQuestionDtoList(List<Question> questions);



}

