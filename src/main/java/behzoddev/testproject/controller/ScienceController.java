package behzoddev.testproject.controller;

import behzoddev.testproject.dto.*;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Science;
import behzoddev.testproject.entity.Topic;
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
@RequestMapping("/sciences")
public class ScienceController {

    private final ScienceService scienceService;

    @GetMapping
    public ResponseEntity<Set<ScienceIdAndNameDto>> getSciences() {
        Set<ScienceIdAndNameDto> scienceNameDtos = scienceService.getAllScienceNameDto();

        return ResponseEntity.ok(scienceNameDtos);
    }

    @GetMapping("/full")
    public ResponseEntity<Set<ScienceDto>> getSciencesFull() {
        Set<ScienceDto> sciences = scienceService.getAllSciencesDto();

        return ResponseEntity.ok(sciences);
    }

    @GetMapping("/{scienceId}")
    public ResponseEntity<ScienceIdAndNameDto> getScienceNameById(@PathVariable Long scienceId) {
        ScienceIdAndNameDto scienceNameDto = scienceService.getScienceNameById(scienceId).orElseThrow();
        return ResponseEntity.ok(scienceNameDto);
    }

    @GetMapping("/{scienceId}/full")
    public ResponseEntity<ScienceDto> getScience(@PathVariable Long scienceId) {
        ScienceDto scienceDto = scienceService.getScienceById(scienceId).orElseThrow();
        return ResponseEntity.ok(scienceDto);
    }

    @GetMapping("/{scienceId}/topic")
    public ResponseEntity<Set<TopicIdAndNameDto>> getTopicsOfScience(@PathVariable Long scienceId) {
        Set<TopicIdAndNameDto> topicIdAndNameDtos = scienceService.getTopicsByScienceId(scienceId);

        return ResponseEntity.ok(topicIdAndNameDtos);
    }

    @GetMapping("/{scienceId}/topic/{topicId}")
    public ResponseEntity<TopicIdAndNameDto> getTopicByIds(@PathVariable Long scienceId, @PathVariable Long topicId) {
        TopicIdAndNameDto topicIdAndNameDto = scienceService.getTopicByIds(scienceId, topicId);

        return ResponseEntity.ok(topicIdAndNameDto);
    }

    @GetMapping("/{scienceId}/topic/{topicId}/questions")
    public ResponseEntity<List<QuestionDto>> getQuestionsByIds(@PathVariable Long scienceId, @PathVariable Long topicId) {
        List<QuestionDto> questionDto = scienceService.getQuestionsByIds(scienceId, topicId);

        return ResponseEntity.ok(questionDto);
    }

    @PostMapping
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

    @PostMapping("/{scienceId}/topic")
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

    @PostMapping("/{scienceId}/topic/{topicId}")
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


}
