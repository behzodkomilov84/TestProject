package behzoddev.testproject.mapper;

import behzoddev.testproject.dto.student.ResponseAnswerDto;
import behzoddev.testproject.dto.student.ResponseQuestionDto;
import behzoddev.testproject.dto.student.ResponseQuestionSetDto;
import behzoddev.testproject.entity.QuestionSet;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface QuestionSetMapper {

    default ResponseQuestionSetDto mapQuestionSetToResponseQuestionSetDto(QuestionSet set) {
        List<ResponseQuestionDto> questions = set.getQuestions()
                .stream()
                .map(q -> new ResponseQuestionDto(
                        q.getId(),
                        q.getQuestionText(),
                        q.getAnswers()
                                .stream()
                                .map(a -> new ResponseAnswerDto(
                                        a.getId(),
                                        a.getAnswerText()
                                ))
                                .toList()
                ))
                .toList();

        return new ResponseQuestionSetDto(
                set.getId(),
                set.getName(),
                questions
        );
    }
}
