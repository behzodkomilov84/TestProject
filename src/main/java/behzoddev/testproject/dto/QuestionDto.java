package behzoddev.testproject.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.util.List;

@Builder
public record QuestionDto(Long id,
                          @NotBlank(message = "QuestionText must not be blank") String questionText,
                          List<AnswerDto> answers) {


}
