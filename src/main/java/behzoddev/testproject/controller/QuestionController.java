package behzoddev.testproject.controller;

import behzoddev.testproject.dto.AnswerDto;
import behzoddev.testproject.dto.AnswerShortDto;
import behzoddev.testproject.dto.QuestionDto;
import behzoddev.testproject.dto.QuestionSaveDto;
import behzoddev.testproject.service.ExcelService;
import behzoddev.testproject.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class QuestionController {
    private final QuestionService questionService;
    private final ExcelService excelService;

    @GetMapping("/api/question")
    public ResponseEntity<List<QuestionDto>> getQuestionsByTopic(@RequestParam Long topicId) {
        List<QuestionDto> questionDtos = questionService.getQuestionDtoListByTopicId(topicId);

        return ResponseEntity.ok(questionDtos);
    }

    @PostMapping("/api/question/save")
    @ResponseBody
    public ResponseEntity<?> saveQuestion(@RequestBody Map<Object, Object> payload) {

        long topicId = Long.parseLong(payload.get("topicId").toString());

        String questionText = payload.get("questionText").toString();

        var answers = (List<Map<Object, Object>>) payload.get("answers");
        List<AnswerShortDto> answerShortDto = new ArrayList<>();

        for (Map<Object, Object> answer : answers) {
            String answerText = answer.get("answerText").toString();
            boolean isTrue = Boolean.parseBoolean(answer.get("isTrue").toString());

            String commentary = "Noto'g'ri javob";
            if (isTrue){
                commentary = "To'g'ri javob";
            }

            if (answer.get("commentary") != null) {
                commentary = answer.get("commentary").toString();
            }

            answerShortDto.add(new AnswerShortDto(answerText, isTrue, commentary));
        }

        boolean isUnique = questionService.isUnique(answerShortDto); //Javoblarni bir xil masligini tekshiradi.

        if (!isUnique) {
            throw new IllegalArgumentException("Answers must be unique");
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

    @PutMapping("/api/question/update")
    public ResponseEntity<Object> updateQuestion(@RequestBody Map<Object, Object> payload) {

        var questionId = Long.parseLong(payload.get("id").toString());

        var newQuestionText = payload.get("questionText").toString();

        List<Map<Object, Object>> answersInPayload = (List<Map<Object, Object>>) payload.get("answers");

        ArrayList<AnswerDto> newAnswers = new ArrayList<>();

        for (Map<Object, Object> answer : answersInPayload) {
            Long id = Long.parseLong(answer.get("id").toString());
            String answerText = answer.get("answerText").toString();
            boolean isTrue = Boolean.parseBoolean(answer.get("isTrue").toString());

            String commentary = "Noto'g'ri javob";
            if (isTrue){
                commentary = "To'g'ri javob";
            }

            if (answer.get("commentary") != null) {
                commentary = answer.get("commentary").toString();
            }
            newAnswers.add(new AnswerDto(id, answerText, isTrue, commentary));
        }

        QuestionDto questionDto = QuestionDto.builder()
                .id(questionId)
                .questionText(newQuestionText)
                .answers(newAnswers)
                .build();

        questionService.updateQuestion(questionDto);


        return ResponseEntity.ok(Map.of("message", "✅ Ma'lumotlar bazada o'zgartirildi!"));
    }

    @DeleteMapping("/api/question/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        questionService.deleteQuestion(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/import/excel")
    public ResponseEntity<?> importExcel(
            @RequestParam MultipartFile file,
            @RequestParam Long topicId
    ) {
        return ResponseEntity.ok(excelService.importQuestions(file, topicId));
    }

}

