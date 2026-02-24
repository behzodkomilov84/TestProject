package behzoddev.testproject.mapper;

import behzoddev.testproject.dto.student.ResponseQuestionDto;
import behzoddev.testproject.dto.student.ResponseQuestionSetDto;
import behzoddev.testproject.entity.QuestionSet;
import behzoddev.testproject.service.AssignmentAttemptService;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface QuestionSetMapper {

    default ResponseQuestionSetDto mapQuestionSetToResponseQuestionSetDto(QuestionSet set) {

        List<ResponseQuestionDto> questions =
                AssignmentAttemptService.getResponseQuestionDtos(set);

        return new ResponseQuestionSetDto(
                set.getId(),
                set.getName(),
                questions
        );
    }

}
