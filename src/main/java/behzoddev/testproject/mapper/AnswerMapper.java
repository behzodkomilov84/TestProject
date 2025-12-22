package behzoddev.testproject.mapper;

import behzoddev.testproject.dto.AnswerDto;
import behzoddev.testproject.entity.Answer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AnswerMapper {

    @Mapping(target = "question", ignore = true)
    Answer mapAnswerDtoToAnswer(AnswerDto dto);

    AnswerDto mapAnswertoAnswerDto(Answer answer);

    List<AnswerDto> mapAnswerListtoAnswerDtoList(List<Answer> answers);

}

