package behzoddev.testproject.mapper;

import behzoddev.testproject.dto.AnswerDto;
import behzoddev.testproject.dto.QuestionDto;
import behzoddev.testproject.entity.Answer;
import behzoddev.testproject.entity.Question;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface QuestionMapper {

    AnswerDto mapAnswertoAnswerDto(Answer answer);

    List<AnswerDto> mapAnswerListtoAnswerDtoList(List<Answer> answers);

    @Mapping(target = "answers", source = "answers")
    QuestionDto mapQuestiontoQuestionDto(Question question);

    List<QuestionDto> mapQuestionListToQuestionDtoList(List<Question> questions);

}
