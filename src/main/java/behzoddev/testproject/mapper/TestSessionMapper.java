package behzoddev.testproject.mapper;

import behzoddev.testproject.dto.TestHistoryDto;
import behzoddev.testproject.dto.TestSessionHistoryDto;
import behzoddev.testproject.entity.TestSession;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TestSessionMapper {

    default TestSessionHistoryDto mapTestSessiontoTestSessionHistoryDto(TestSession testSession) {
        return TestSessionHistoryDto.builder()
                .testSessionId(testSession.getId())
                .scienceName(testSession.getQuestions().get(0).getQuestion().getTopic().getScience().getName())
                .finishedAt(testSession.getFinishedAt())
                .totalQuestions(testSession.getTotalQuestions())
                .correctAnswers(testSession.getCorrectAnswers())
                .percent(testSession.getPercent())
                .durationSec(testSession.getDurationSec())
                        .build();
    }

    @Mapping(source = "id", target = "testSessionId")
    TestHistoryDto mapTestSessiontoTestHistoryDto(TestSession testSession);
}
