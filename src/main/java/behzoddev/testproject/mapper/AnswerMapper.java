package behzoddev.testproject.mapper;

import behzoddev.testproject.dto.answer.AnswerDto;
import behzoddev.testproject.dto.answer.AnswerShortDto;
import behzoddev.testproject.dto.student.ResponseAnswerDto;
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

    AnswerDto mapAnswerToAnswerDto(Answer answer);

    @Mapping(target = "question", ignore = true)
    List<AnswerDto> mapAnswerListToAnswerDtoList(List<Answer> answers);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "question", ignore = true)
    List<Answer> mapAnswerShortDtoListToAnswerList(List<AnswerShortDto> dto);

    @Mapping(target = "question", ignore = true)
    List<Answer> mapAnswerDtoListToAnswerList(List<AnswerDto> answerListDto);

    List<AnswerShortDto> mapAnswerListToAnswerShorDtoList(List<Answer> answers);

    @Mapping(target = "id", ignore = true)
    List<AnswerShortDto> mapAnswerDtoListToAnswerShorDtoList(List<AnswerDto> answers);

    @Mapping(source = "answerText", target = "text")
    ResponseAnswerDto mapAnswertoResponseAnswerDto(Answer answer);

    List<ResponseAnswerDto> mapAnswerListToResponseAnswerDtoList(List<Answer> answers);

}

