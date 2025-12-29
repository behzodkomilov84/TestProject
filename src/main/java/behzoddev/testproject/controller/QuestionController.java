package behzoddev.testproject.controller;

import behzoddev.testproject.dto.QuestionDto;
import behzoddev.testproject.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class QuestionController {
    private final QuestionService questionService;

    @GetMapping("/sciences/{scienceId}/topic/{topicId}/questions")
    public ResponseEntity<List<QuestionDto>> getQuestionsByIds(@PathVariable Long scienceId, @PathVariable Long topicId) {
        List<QuestionDto> questionDto = questionService.getQuestionsByIds(scienceId, topicId);

        return ResponseEntity.ok(questionDto);
    }

    @GetMapping("/questions/{questionId}")
    public ResponseEntity<QuestionDto> getQuestionById(@PathVariable Long questionId) {
        QuestionDto questionDto = questionService.getQuestionById(questionId);

        return ResponseEntity.ok(questionDto);
    }

}
