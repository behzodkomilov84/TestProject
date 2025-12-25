package behzoddev.testproject.controller;

import behzoddev.testproject.dto.*;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Science;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.exception.ErrorResponse;
import behzoddev.testproject.service.ScienceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RestController
@RequiredArgsConstructor
//@RequestMapping("/sciences")
public class ScienceController {

    private final ScienceService scienceService;

    @GetMapping("/sciences")
    public ResponseEntity<Set<ScienceIdAndNameDto>> getSciences() {
        Set<ScienceIdAndNameDto> scienceNameDtos = scienceService.getAllScienceIdAndNameDto();

        return ResponseEntity.ok(scienceNameDtos);
    }

    @GetMapping("/sciences/full")
    public ResponseEntity<Set<ScienceDto>> getSciencesFull() {
        Set<ScienceDto> sciences = scienceService.getAllSciencesDto();

        return ResponseEntity.ok(sciences);
    }

    @GetMapping("/sciences/{scienceId}")
    public ResponseEntity<ScienceIdAndNameDto> getScienceNameById(@PathVariable Long scienceId) {
        ScienceIdAndNameDto scienceNameDto = scienceService.getScienceNameById(scienceId).orElseThrow();
        return ResponseEntity.ok(scienceNameDto);
    }

    @GetMapping("/sciences/{scienceId}/full")
    public ResponseEntity<ScienceDto> getScience(@PathVariable Long scienceId) {
        ScienceDto scienceDto = scienceService.getScienceById(scienceId).orElseThrow();
        return ResponseEntity.ok(scienceDto);
    }

    @GetMapping("/sciences/{scienceId}/topic")
    public ResponseEntity<Set<TopicIdAndNameDto>> getTopicsOfScience(@PathVariable Long scienceId) {
        Set<TopicIdAndNameDto> topicIdAndNameDtos = scienceService.getTopicsByScienceId(scienceId);

        return ResponseEntity.ok(topicIdAndNameDtos);
    }

    @GetMapping("/sciences/{scienceId}/topic/{topicId}")
    public ResponseEntity<TopicIdAndNameDto> getTopicByIds(@PathVariable Long scienceId, @PathVariable Long topicId) {
        TopicIdAndNameDto topicIdAndNameDto = scienceService.getTopicByIds(scienceId, topicId);

        return ResponseEntity.ok(topicIdAndNameDto);
    }

    @GetMapping("/sciences/{scienceId}/topic/{topicId}/questions")
    public ResponseEntity<List<QuestionDto>> getQuestionsByIds(@PathVariable Long scienceId, @PathVariable Long topicId) {
        List<QuestionDto> questionDto = scienceService.getQuestionsByIds(scienceId, topicId);

        return ResponseEntity.ok(questionDto);
    }

    @GetMapping("/questions/{questionId}")
    public ResponseEntity<QuestionDto> getQuestionById(@PathVariable Long questionId) {
        QuestionDto questionDto = scienceService.getQuestionById(questionId);

        return ResponseEntity.ok(questionDto);
    }

    @PostMapping("/sciences")
    public ResponseEntity<?> createScience(@RequestBody ScienceNameDto scienceNameDto) {

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

    @PostMapping("/sciences/{scienceId}/topic")
    public ResponseEntity<?> createTopic(
            @PathVariable Long scienceId,
            @RequestBody TopicNameDto topicNameDto
    ) {
        Set<TopicIdAndNameDto> existingTopics =
                scienceService.getTopicsByScienceId(scienceId);

        boolean exists = existingTopics.stream()
                .anyMatch(t -> t.name().equalsIgnoreCase(topicNameDto.name()));

        if (exists) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "Topic with name '" + topicNameDto.name() + "' already exists",
                            HttpStatus.CONFLICT.value()
                    ));
        }

        Topic topic = scienceService.saveTopic(scienceId, topicNameDto);

        return ResponseEntity.created(
                URI.create("sciences/" + scienceId + "/topic/" + topic.getId())
        ).build();
    }

    @PostMapping("topic/{topicId}")
    public ResponseEntity<?> createQuestion(@PathVariable Long topicId,
                                            @RequestBody QuestionShortDto newQuestion) {

        List<QuestionShortDto> existingQuestions =
                scienceService.getQuestionsByTopicId(topicId);

        if (scienceService.isQuestionWithAnswersExists(existingQuestions, newQuestion)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "A question with such answers already exists.",
                            HttpStatus.CONFLICT.value()
                    ));
        }

        Question question = scienceService.saveQuestion(topicId, newQuestion);

        return ResponseEntity.created(
                URI.create("sciences/" + scienceService.getScienceIdByTopicId(topicId)
                        + "/topic/" + topicId + "/questions/" + question.getId())
        ).build();
    }

    @PutMapping("/sciences")
    public ResponseEntity<?> updateScience(@RequestBody Science science) {

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

    @PatchMapping("/sciences")
    public ResponseEntity<Void> updateScienceName(@RequestParam Long id, @RequestParam String name) {
        scienceService.updateScienceName(id, name);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/sciences/{scienceId}")
    public ResponseEntity<Void> deleteScience(@PathVariable Long scienceId) {
        scienceService.removeScience(scienceId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/topic/{topicId}")
    public ResponseEntity<Void> deleteTopic(@PathVariable Long topicId) {
        scienceService.removeTopic(topicId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long questionId) {
        scienceService.removeQuestion(questionId);
        return ResponseEntity.noContent().build();
    }
}
