package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.*;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Science;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.exception.ErrorResponse;
import behzoddev.testproject.service.QuestionService;
import behzoddev.testproject.service.ScienceService;
import behzoddev.testproject.service.TopicService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.*;

@Controller
@RequiredArgsConstructor
public class ScienceController {

    private final ScienceService scienceService;
    private final TopicService topicService;
    private final QuestionService questionService;

    @GetMapping("/api/science")
    @ResponseBody
    public ResponseEntity<Set<ScienceIdAndNameDto>> getSciences() {
        Set<ScienceIdAndNameDto> scienceIdsAndNames = scienceService.getAllScienceIdAndNameDto();

        return ResponseEntity.ok(scienceIdsAndNames);
    }

    @PostMapping("/api/science/save")
    @ResponseBody
    public ResponseEntity<?> saveScience(@RequestBody Map<String, Object> payload) {

        var newSubjects = (List<String>) payload.get("new");
        var needToUpdateSubjects = (List<Map<String, Object>>) payload.get("updated");

        List<Long> deletedScienceIds = new ArrayList<>();
        for (Object obj : (List<Object>) payload.get("deletedIds")) {
            deletedScienceIds.add(((Number) obj).longValue());
        }

        // Добавляем новые
        for (String name : newSubjects) {
            scienceService.saveScience(new ScienceNameDto(name));
        }

        // Обновляем существующие
        for (Map<String, Object> item : needToUpdateSubjects) {
            Long id = ((Number) item.get("id")).longValue();
            String name = (String) item.get("name");
            scienceService.updateScienceName(id, name);
        }

        // Удаление
        for (Long id : deletedScienceIds) {
            scienceService.removeScience(id);
        }

        return ResponseEntity.ok(Map.of("message", "✅ Ma'lumotlar bazaga saqlandi!"));
    }

    @GetMapping("/science/full")
    public ResponseEntity<Set<ScienceDto>> getSciencesFull() {
        Set<ScienceDto> sciences = scienceService.getAllSciencesDto();

        return ResponseEntity.ok(sciences);
    }

    @GetMapping("/science/{scienceId}")
    public ResponseEntity<ScienceIdAndNameDto> getScienceNameById(@PathVariable Long scienceId) {
        ScienceIdAndNameDto scienceNameDto = scienceService.getScienceNameById(scienceId).orElseThrow();
        return ResponseEntity.ok(scienceNameDto);
    }

    @GetMapping("/science/{scienceId}/full")
    public ResponseEntity<ScienceDto> getScience(@PathVariable Long scienceId) {
        ScienceDto scienceDto = scienceService.getScienceById(scienceId).orElseThrow();
        return ResponseEntity.ok(scienceDto);
    }

    @PostMapping("/science")
    public ResponseEntity<?> createScience(@Valid @RequestBody ScienceNameDto scienceNameDto) {

        Optional<Science> existing = scienceService.getByName(scienceNameDto.name());
        if (existing.isPresent()) {
            ErrorResponse error = new ErrorResponse(
                    "Science with name '" + scienceNameDto.name() + "' already exists",
                    HttpStatus.CONFLICT.value()
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }

        Science science = scienceService.saveScience(scienceNameDto);
        return ResponseEntity
                .created(URI.create("sciences/" + science.getId())
                ).body("Science with name '" + scienceNameDto.name() + "' was created");
    }

    @PostMapping("/science/{scienceId}/topic")
    public ResponseEntity<?> createTopic(
            @PathVariable Long scienceId,
            @Valid @RequestBody TopicNameDto topicNameDto
    ) {
        Set<TopicIdAndNameDto> existingTopics =
                topicService.getTopicsByScienceId(scienceId);

        boolean exists = existingTopics.stream()
                .anyMatch(t -> t.name().equalsIgnoreCase(topicNameDto.name()));

        if (exists) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "Topic with name '" + topicNameDto.name() + "' already exists",
                            HttpStatus.CONFLICT.value()
                    ));
        }

        Topic topic = topicService.saveTopic(scienceId, topicNameDto);

        return ResponseEntity.created(
                URI.create("science/" + scienceId + "/topic/" + topic.getId())
        ).build();
    }

    @PostMapping("topic/{topicId}")
    public ResponseEntity<?> createQuestion(@PathVariable Long topicId,
                                            @Valid @RequestBody QuestionShortDto newQuestion) {

        List<QuestionShortDto> existingQuestions =
                questionService.getQuestionsByTopicId(topicId);

        if (questionService.isQuestionWithAnswersExists(existingQuestions, newQuestion)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "A question with such answers already exists.",
                            HttpStatus.CONFLICT.value()
                    ));
        }

        Question question = questionService.saveQuestion(topicId, newQuestion);

        return ResponseEntity.created(
                URI.create("science/" + scienceService.getScienceIdByTopicId(topicId)
                        + "/topic/" + topicId + "/question/" + question.getId())
        ).build();
    }

    @PutMapping("/science")
    public ResponseEntity<?> updateScience(@Valid @RequestBody Science science) {

        boolean scienceNameExist = scienceService.isScienceNameExist(science.getName());
        boolean scienceIdExist = scienceService.isScienceIdExist(science.getId());

        if (scienceIdExist && !scienceNameExist) {
            scienceService.saveScience(science);
            return ResponseEntity.noContent().build();
        }

        if (!scienceIdExist) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "Science with id '" + science.getId() + "' is not exists",
                            HttpStatus.CONFLICT.value()
                    ));
        }

        if (scienceNameExist) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "Science with name '" + science.getName() + "' is already exists",
                            HttpStatus.CONFLICT.value()
                    ));
        }
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/science")
    public ResponseEntity<Void> updateScienceName(@RequestParam Long id, @Valid @RequestParam String name) {
        scienceService.updateScienceName(id, name);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/science/{scienceId}")
    public ResponseEntity<Void> deleteScience(@PathVariable Long scienceId) {
        scienceService.removeScience(scienceId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/topic/{topicId}")
    public ResponseEntity<Void> deleteTopic(@PathVariable Long topicId) {
        topicService.removeTopic(topicId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/question/{questionId}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long questionId) {
        questionService.deleteQuestion(questionId);
        return ResponseEntity.noContent().build();
    }
}
