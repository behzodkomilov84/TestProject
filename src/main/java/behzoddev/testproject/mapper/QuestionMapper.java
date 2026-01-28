package behzoddev.testproject.mapper;

import behzoddev.testproject.dto.QuestionDto;
import behzoddev.testproject.dto.QuestionSaveDto;
import behzoddev.testproject.dto.QuestionShortDto;
import behzoddev.testproject.entity.Answer;
import behzoddev.testproject.entity.Question;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring",
        uses = AnswerMapper.class)
public interface QuestionMapper {

    QuestionDto mapQuestiontoQuestionDto(Question question);

    List<QuestionDto> mapQuestionListToQuestionDtoList(List<Question> questions);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "topic", ignore = true)
    Question mapQuestionDtoToQuestion(QuestionDto questionDto);

    List<QuestionShortDto> mapQuestionListToQuestionShortDtoList(List<Question> questions);

    @Mapping(target = "topic", ignore = true)
    @Mapping(target = "id", ignore = true)
    Question mapQuestionShortDtoToQuestion(QuestionShortDto newQuestion);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "topic", ignore = true)
    @Mapping(target = "questionText", ignore = true)
    Question mapQuestionSaveDtoToQuestion(QuestionSaveDto dto);

}

