package behzoddev.testproject.mapper;

import behzoddev.testproject.dto.AnswerDto;
import behzoddev.testproject.dto.AnswerShortDto;
import behzoddev.testproject.entity.Answer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AnswerMapper {

    @Mapping(target = "question", ignore = true)
    @Mapping(target = "id", ignore = true)
    Answer mapAnswerDtoToAnswer(AnswerDto dto);

    @Mapping(target = "question", ignore = true)
    @Mapping(target = "id", ignore = true)
    Answer mapAnswerShortDtoToAnswer(AnswerShortDto dto);

    AnswerDto mapAnswertoAnswerDto(Answer answer);

    @Mapping(target = "question", ignore = true)
    List<AnswerDto> mapAnswerListToAnswerDtoList(List<Answer> answers);

    List<Answer> mapAnswerShortDtoListToAnswerList(List<AnswerShortDto> dto);


}

