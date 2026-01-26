package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.AnswerShortDto;
import behzoddev.testproject.dto.ModalCommentSaveDto;
import behzoddev.testproject.dto.QuestionDto;
import behzoddev.testproject.dto.QuestionSaveDto;
import behzoddev.testproject.service.AnswerService;
import behzoddev.testproject.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
    private final AnswerService answerService;

    @GetMapping("/api/question")
    public ResponseEntity<Page<QuestionDto>> getPage(
            @RequestParam Long topicId,
            @PageableDefault(size = 10, page = 0) Pageable pageable,
            @RequestParam(required = false) String searchQuestionText
    ) {
        Page<QuestionDto> questionDtoPageByTopicId = questionService.getQuestionDtoPageByTopicId(
                topicId,
                searchQuestionText,
                pageable
        );

            return ResponseEntity.ok(questionDtoPageByTopicId);
    }

    @GetMapping("/api/question/all")
    public ResponseEntity<List<QuestionDto>> getAll(
            @RequestParam Long topicId,
            @RequestParam(required = false) String searchQuestionText
    ) {
        return ResponseEntity.ok(
                questionService.findAll(topicId, searchQuestionText)
        );
    }

    @PostMapping("/api/question/save")
    @ResponseBody
    public ResponseEntity<?> saveQuestion(@RequestBody Map<Object, Object> payload) {

        long topicId = Long.parseLong(payload.get("topicId").toString());

        String questionText = payload.get("questionText").toString();

        var answers = (List<Map<Object, Object>>) payload.get("answers");
        List<AnswerShortDto> answerShortDto = new ArrayList<>();
        List<String> answerTextList = new ArrayList<>();

        for (Map<Object, Object> answer : answers) {

            String answerText = answer.get("answerText").toString();
            answerTextList.add(answerText);

            boolean isTrue = Boolean.parseBoolean(answer.get("isTrue").toString());

            String commentary = "Noto'g'ri javob";
            if (isTrue) {
                commentary = "To'g'ri javob";
            }

            if (answer.get("commentary") != null) {
                commentary = answer.get("commentary").toString();
            }

            answerShortDto.add(new AnswerShortDto(answerText, isTrue, commentary));
        }

        boolean isUnique = answerService.isUnique(answerTextList); //Javoblarni bir xil masligini tekshiradi.

        if (!isUnique) {
            throw new IllegalArgumentException("❌Javoblar bir xil bo'lishi mumkin emas.");
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
    public ResponseEntity<?> updateQuestion(@RequestBody QuestionDto payload) {

        try {
            questionService.updateQuestion(payload);

            return ResponseEntity.ok(
                    Map.of("message", "Updated")
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", e.getMessage()));
        }

    }

    @PatchMapping("/api/question/updateComment")
    public ResponseEntity<?> updateComment(@RequestBody ModalCommentSaveDto payload) {

        try {
            answerService.updateCommentOfTrueAnswer(payload);

            return ResponseEntity.ok(
                    Map.of("message", "Muvaffaqiyatli o'zgartirildi.")
            );
        } catch (Exception e) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", e.getMessage()));
        }

    }


    @DeleteMapping("/api/question/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            questionService.deleteQuestion(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}