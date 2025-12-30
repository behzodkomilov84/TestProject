package behzoddev.testproject.controller;

import behzoddev.testproject.dto.AnswerShortDto;
import behzoddev.testproject.dto.QuestionDto;
import behzoddev.testproject.dto.QuestionSaveDto;
import behzoddev.testproject.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class QuestionController {
    private final QuestionService questionService;

    @GetMapping("/api/question")
    public ResponseEntity<List<QuestionDto>> getQuestionsByTopic(@RequestParam Long topicId) {
        List<QuestionDto> questionDtos = questionService.getQuestionDtoListByTopicId(topicId);

        return ResponseEntity.ok(questionDtos);
    }

    @PostMapping("/api/question/save")
    @ResponseBody
    public ResponseEntity<?> saveQuestion(@RequestBody Map<Object, Object> payload) {

        long topicId = Long.parseLong(payload.get("topicId").toString());

        String questionText = payload.get("question").toString();

        var answers = (List<Map<Object, Object>>) payload.get("answers");
        List<AnswerShortDto> answerShortDto = new ArrayList<>();

        for (Map<Object, Object> answer : answers) {
            String answerText = answer.get("answerText").toString();
            boolean isTrue = Boolean.parseBoolean(answer.get("isTrue").toString());

            answerShortDto.add(new AnswerShortDto(answerText, isTrue));
        }

        questionService.save(QuestionSaveDto.builder()
                .topicId(topicId)
                .questionText(questionText)
                .answers(answerShortDto)
                .build());

        return ResponseEntity.ok(Map.of("message", "saved"));
    }

    @GetMapping("/science/{scienceId}/topic/{topicId}/question")
    public ResponseEntity<List<QuestionDto>> getQuestionsByIds(@PathVariable Long scienceId, @PathVariable Long topicId) {
        List<QuestionDto> questionDto = questionService.getQuestionsByIds(scienceId, topicId);

        return ResponseEntity.ok(questionDto);
    }

    @GetMapping("/question/{questionId}")
    public ResponseEntity<QuestionDto> getQuestionById(@PathVariable Long questionId) {
        QuestionDto questionDto = questionService.getQuestionById(questionId);

        return ResponseEntity.ok(questionDto);
    }

}
