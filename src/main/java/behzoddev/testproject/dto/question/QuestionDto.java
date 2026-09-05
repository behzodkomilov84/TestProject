package behzoddev.testproject.dto.question;

import behzoddev.testproject.dto.answer.AnswerDto;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.util.List;

@Builder
public record QuestionDto(Long id,
                          @NotBlank(message = "❌questionText bo'sh bo'lishi mumkin emas.") String questionText,
                          String imageUrl,
                          Integer imageWidth,
                          Integer imageHeight,
                          List<AnswerDto> answers) {


}
